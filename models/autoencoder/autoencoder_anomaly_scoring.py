from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader, TensorDataset

from lstm_imu_autoencoder import LSTMAutoencoder


FEATURE_COLUMNS = ("ax", "ay", "az", "gx", "gy", "gz")

WINDOWS_PATH = Path(__file__).resolve().parent / "filtered_imu_windows.npy"
CHECKPOINT_PATH = Path(__file__).resolve().parent / "lstm_autoencoder_outputs" / "best_lstm_autoencoder.pt"

OUTPUT_DIR = Path(__file__).resolve().parent / "autoencoder_anomaly_scores"
ERRORS_CSV = OUTPUT_DIR / "reconstruction_error_labels.csv"
ERROR_DISTRIBUTION_PLOT = OUTPUT_DIR / "reconstruction_error_distribution.png"
ANOMALY_TIMESERIES_PLOT = OUTPUT_DIR / "anomaly_regions_timeseries.png"


# ---------------- CONFIG ----------------

@dataclass(frozen=True)
class ScoringConfig:
    input_size: int = 6
    hidden_size: int = 64
    latent_size: int = 16
    num_layers: int = 2
    dropout: float = 0.2

    batch_size: int = 64
    sample_rate_hz: float = 50.0

    window_size: int = 200
    overlap: float = 0.50

    percentile: float = 99.0

    smoothing_window: int = 3  # NEW: temporal stability

    @property
    def step_size(self) -> int:
        return int(round(self.window_size * (1.0 - self.overlap)))


# ---------------- DATA LOADING ----------------

def make_loader(windows: np.ndarray, batch_size: int) -> DataLoader:
    tensor = torch.tensor(windows, dtype=torch.float32)
    return DataLoader(TensorDataset(tensor), batch_size=batch_size, shuffle=False)


# ---------------- MODEL LOADING ----------------

def load_model(checkpoint_path: Path, config: ScoringConfig, device: torch.device):
    model = LSTMAutoencoder(
        input_size=config.input_size,
        hidden_size=config.hidden_size,
        latent_size=config.latent_size,
        num_layers=config.num_layers,
        dropout=config.dropout,
    ).to(device)

    ckpt = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(ckpt["model"])
    model.eval()
    return model


# ---------------- SCORING ----------------

@torch.no_grad()
def compute_reconstruction_errors(
    model: nn.Module,
    windows: np.ndarray,
    batch_size: int,
    device: torch.device,
) -> np.ndarray:
    model.eval()
    model.to(device)

    errors = []
    for (batch,) in make_loader(windows, batch_size):
        batch = batch.to(device)
        recon, _ = model(batch)
        mse = torch.mean((recon - batch) ** 2, dim=(1, 2))
        errors.append(mse.cpu().numpy())

    return np.concatenate(errors)


# ---------------- THRESHOLDS (FIXED: no leakage expected usage) ----------------

def compute_thresholds(errors: np.ndarray, percentile: float) -> dict[str, float]:
    errors = np.asarray(errors, dtype=np.float64)

    mean = float(errors.mean())
    std = float(errors.std())

    return {
        "mean": mean,
        "std": std,
        "mean_plus_3std": mean + 3 * std,
        "percentile": float(np.percentile(errors, percentile)),
        "percentile_value": percentile,
    }


# ---------------- LABELING ----------------

def label_windows(errors: np.ndarray, thresholds: dict[str, float], mode: str) -> np.ndarray:
    if mode == "mean_std":
        return (errors > thresholds["mean_plus_3std"]).astype(np.int32)

    if mode == "percentile":
        return (errors > thresholds["percentile"]).astype(np.int32)

    if mode == "combined":   # FIXED (explicit OR logic)
        return (
            (errors > thresholds["mean_plus_3std"]) |
            (errors > thresholds["percentile"])
        ).astype(np.int32)

    raise ValueError("mode must be: mean_std | percentile | combined")


# ---------------- FIX 3: TEMPORAL SMOOTHING ----------------

def smooth_labels(labels: np.ndarray, window: int) -> np.ndarray:
    if window <= 1:
        return labels

    kernel = np.ones(window)
    smoothed = np.convolve(labels.astype(float), kernel, mode="same")
    return (smoothed >= 1).astype(np.int32)


# ---------------- PIPELINE ----------------

def score_pipeline(
    model,
    windows: np.ndarray,
    val_windows: np.ndarray,
    config: ScoringConfig,
    device: torch.device,
):
    # IMPORTANT FIX: thresholds computed ONLY on validation
    val_errors = compute_reconstruction_errors(model, val_windows, config.batch_size, device)

    thresholds = compute_thresholds(val_errors, config.percentile)

    test_errors = compute_reconstruction_errors(model, windows, config.batch_size, device)

    raw_labels = label_windows(test_errors, thresholds, "combined")
    smooth_labels_out = smooth_labels(raw_labels, config.smoothing_window)

    return test_errors, thresholds, smooth_labels_out


# ---------------- MAIN ----------------

def main():
    config = ScoringConfig()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    data = np.load(WINDOWS_PATH).astype(np.float32)

    # simple split (same structure you already use)
    n = len(data)
    train_end = int(n * 0.7)
    val_end = int(n * 0.85)

    train, val, test = data[:train_end], data[train_end:val_end], data[val_end:]

    model = load_model(CHECKPOINT_PATH, config, device)

    errors, thresholds, labels = score_pipeline(
        model,
        test,
        val,
        config,
        device,
    )

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    print("\n--- THRESHOLDS ---")
    print(thresholds)

    print("\n--- ANOMALIES ---")
    print("raw:", labels.sum())

    np.save(OUTPUT_DIR / "errors.npy", errors)
    np.save(OUTPUT_DIR / "labels.npy", labels)


if __name__ == "__main__":
    main()