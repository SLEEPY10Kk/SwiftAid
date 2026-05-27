from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader, TensorDataset

from .config import Settings
from .model import LSTMAutoencoder
from .registry import ModelRegistry
from .storage import SensorWindowStore


@dataclass(frozen=True)
class RetrainResult:
    status: str
    message: str
    metadata: dict | None = None


def chronological_split(windows: np.ndarray, train_fraction: float = 0.8) -> tuple[np.ndarray, np.ndarray]:
    split = max(1, int(len(windows) * train_fraction))
    split = min(split, len(windows) - 1)
    return windows[:split], windows[split:]


def make_loader(windows: np.ndarray, batch_size: int, shuffle: bool) -> DataLoader:
    tensor = torch.tensor(windows, dtype=torch.float32)
    return DataLoader(TensorDataset(tensor), batch_size=batch_size, shuffle=shuffle)


def train_one_epoch(
    model: nn.Module,
    loader: DataLoader,
    optimizer: torch.optim.Optimizer,
    loss_fn: nn.Module,
    device: torch.device,
) -> float:
    model.train()
    total_loss = 0.0
    total_count = 0

    for (batch,) in loader:
        batch = batch.to(device)
        optimizer.zero_grad(set_to_none=True)
        reconstruction, _ = model(batch)
        loss = loss_fn(reconstruction, batch)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        optimizer.step()

        total_loss += loss.item() * len(batch)
        total_count += len(batch)

    return total_loss / max(total_count, 1)


@torch.no_grad()
def reconstruction_errors(
    model: nn.Module,
    windows: np.ndarray,
    batch_size: int,
    device: torch.device,
) -> np.ndarray:
    model.eval()
    errors = []
    for (batch,) in make_loader(windows, batch_size=batch_size, shuffle=False):
        batch = batch.to(device)
        reconstruction, _ = model(batch)
        mse = torch.mean((reconstruction - batch) ** 2, dim=(1, 2))
        errors.append(mse.cpu().numpy())
    return np.concatenate(errors)


@torch.no_grad()
def evaluate(
    model: nn.Module,
    windows: np.ndarray,
    batch_size: int,
    device: torch.device,
) -> float:
    return float(reconstruction_errors(model, windows, batch_size, device).mean())


def compute_adaptive_threshold(errors: np.ndarray, percentile: float) -> tuple[float, str]:
    mean = float(errors.mean())
    std = float(errors.std(ddof=0))
    mean_std = mean + 3.0 * std
    pct = float(np.percentile(errors, percentile))
    threshold = max(mean_std, pct)
    method = f"max(mean+3std={mean_std:.8f}, p{percentile:g}={pct:.8f})"
    return threshold, method


def validate_candidate_model(
    threshold: float,
    validation_loss: float,
    active_metadata: dict | None,
    settings: Settings,
) -> tuple[bool, list[str]]:
    """
    Decide whether a retrained model is safe to activate and push.

    The first model version can be promoted if it is finite and inside absolute
    bounds. Later versions must also stay close to the current active threshold
    and avoid a large validation-loss regression.
    """
    reasons = []

    if not np.isfinite(threshold):
        reasons.append("threshold is not finite")
    if not np.isfinite(validation_loss):
        reasons.append("validation loss is not finite")
    if threshold < settings.min_threshold:
        reasons.append(f"threshold {threshold:.8f} is below minimum {settings.min_threshold:.8f}")
    if threshold > settings.max_threshold:
        reasons.append(f"threshold {threshold:.8f} is above maximum {settings.max_threshold:.8f}")

    if active_metadata:
        active_threshold = float(active_metadata["threshold"])
        allowed_delta = abs(active_threshold) * settings.max_threshold_change_ratio
        threshold_delta = abs(threshold - active_threshold)
        if threshold_delta > allowed_delta:
            reasons.append(
                "threshold changed too much "
                f"({threshold_delta:.8f} > allowed {allowed_delta:.8f})"
            )

        active_val_loss = active_metadata.get("validation_loss")
        if active_val_loss is not None:
            max_allowed_loss = float(active_val_loss) * (1.0 + settings.max_validation_loss_increase_ratio)
            if validation_loss > max_allowed_loss:
                reasons.append(
                    "validation loss regressed too much "
                    f"({validation_loss:.8f} > allowed {max_allowed_loss:.8f})"
                )

    return len(reasons) == 0, reasons


def retrain_autoencoder(
    store: SensorWindowStore,
    registry: ModelRegistry,
    settings: Settings,
    activate: bool = True,
) -> RetrainResult:
    windows = store.load_training_windows(min_timesteps=settings.timesteps)
    if len(windows) < settings.min_retrain_windows:
        return RetrainResult(
            status="skipped",
            message=(
                f"Need at least {settings.min_retrain_windows} trusted normal windows; "
                f"found {len(windows)}."
            ),
        )

    train_windows, val_windows = chronological_split(windows)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = LSTMAutoencoder(
        input_size=len(settings.features),
        hidden_size=settings.hidden_size,
        latent_size=settings.latent_size,
        num_layers=settings.num_layers,
        dropout=settings.dropout,
    ).to(device)

    optimizer = torch.optim.AdamW(model.parameters(), lr=settings.learning_rate, weight_decay=1e-5)
    loss_fn = nn.MSELoss()
    train_loader = make_loader(train_windows, settings.batch_size, shuffle=True)

    best_val_loss = float("inf")
    best_state = None
    bad_epochs = 0
    for _epoch in range(1, settings.max_epochs + 1):
        train_one_epoch(model, train_loader, optimizer, loss_fn, device)
        val_loss = evaluate(model, val_windows, settings.batch_size, device)
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_state = {key: value.detach().cpu().clone() for key, value in model.state_dict().items()}
            bad_epochs = 0
        else:
            bad_epochs += 1
            if bad_epochs >= settings.patience:
                break

    if best_state is not None:
        model.load_state_dict(best_state)
        model.to(device)

    val_errors = reconstruction_errors(model, val_windows, settings.batch_size, device)
    threshold, method = compute_adaptive_threshold(val_errors, settings.threshold_percentile)
    active_metadata = registry.get_active()
    should_activate, rejection_reasons = validate_candidate_model(
        threshold=threshold,
        validation_loss=best_val_loss,
        active_metadata=active_metadata,
        settings=settings,
    )

    metadata = registry.save_version(
        model=model.cpu(),
        threshold=threshold,
        threshold_method=method,
        validation_loss=best_val_loss,
        train_window_count=len(train_windows),
        config={
            "input_size": len(settings.features),
            "hidden_size": settings.hidden_size,
            "latent_size": settings.latent_size,
            "num_layers": settings.num_layers,
            "dropout": settings.dropout,
            "timesteps": settings.timesteps,
            "features": list(settings.features),
            "safety_checks": {
                "min_threshold": settings.min_threshold,
                "max_threshold": settings.max_threshold,
                "max_threshold_change_ratio": settings.max_threshold_change_ratio,
                "max_validation_loss_increase_ratio": settings.max_validation_loss_increase_ratio,
            },
        },
        activate=activate and should_activate,
    )
    if not should_activate:
        return RetrainResult(
            status="rejected",
            message=(
                f"Saved candidate model {metadata['version']} but did not activate it: "
                + "; ".join(rejection_reasons)
            ),
            metadata=metadata,
        )

    return RetrainResult(
        status="completed",
        message=f"Created model version {metadata['version']} with threshold {threshold:.8f}.",
        metadata=metadata,
    )
