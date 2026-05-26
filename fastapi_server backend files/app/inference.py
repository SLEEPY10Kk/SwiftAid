from __future__ import annotations

from pathlib import Path

import numpy as np
import torch

from .config import Settings
from .model import LSTMAutoencoder
from .schemas import IMUSample


class ModelNotLoadedError(RuntimeError):
    """Raised when inference is requested before the model is available."""


class CrashDetectionService:
    """Owns model loading, preprocessing, and reconstruction-error inference."""

    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model: LSTMAutoencoder | None = None
        self.model_version: str | None = None
        self.threshold = settings.threshold

    def load_model(
        self,
        model_path: Path | None = None,
        threshold: float | None = None,
        model_version: str | None = None,
    ) -> None:
        checkpoint_path = model_path or self.settings.model_path
        if not checkpoint_path.exists():
            raise FileNotFoundError(f"Model checkpoint not found: {checkpoint_path}")

        model = LSTMAutoencoder(
            input_size=len(self.settings.features),
            hidden_size=self.settings.hidden_size,
            latent_size=self.settings.latent_size,
            num_layers=self.settings.num_layers,
            dropout=self.settings.dropout,
        )
        checkpoint = torch.load(checkpoint_path, map_location=self.device)
        model.load_state_dict(checkpoint["model_state_dict"])
        model.to(self.device)
        model.eval()
        self.model = model
        self.model_version = model_version
        if threshold is not None:
            self.threshold = threshold

    def predict(self, samples: list[IMUSample]) -> dict[str, float | bool | int | str]:
        if self.model is None:
            raise ModelNotLoadedError("LSTM autoencoder is not loaded.")

        window = self._samples_to_window(samples)
        score = self._reconstruction_mse(window)
        return {
            "anomaly_score": score,
            "is_anomaly": score > self.threshold,
            "threshold": self.threshold,
            "num_samples": len(samples),
            "device": str(self.device),
            "model_version": self.model_version,
        }

    def _samples_to_window(self, samples: list[IMUSample]) -> torch.Tensor:
        array = np.asarray(
            [[getattr(sample, feature) for feature in self.settings.features] for sample in samples],
            dtype=np.float32,
        )
        array = fit_to_timesteps(array, timesteps=self.settings.timesteps)
        tensor = torch.from_numpy(array).unsqueeze(0)
        return tensor.to(self.device)

    @torch.no_grad()
    def _reconstruction_mse(self, window: torch.Tensor) -> float:
        assert self.model is not None
        reconstruction, _ = self.model(window)
        mse = torch.mean((reconstruction - window) ** 2)
        return float(mse.detach().cpu().item())


def fit_to_timesteps(array: np.ndarray, timesteps: int) -> np.ndarray:
    """
    Trim or pad a time-series to the model input length.

    If fewer than timesteps are provided, edge padding repeats the final sample.
    That avoids injecting zeros that may look like artificial motion.
    """
    if array.ndim != 2:
        raise ValueError("array must have shape [timesteps, features].")
    if len(array) == timesteps:
        return array
    if len(array) > timesteps:
        return array[-timesteps:]

    pad_count = timesteps - len(array)
    padding = np.repeat(array[-1:, :], repeats=pad_count, axis=0)
    return np.concatenate([array, padding], axis=0)


def load_threshold_from_npz(path: Path, key: str = "percentile") -> float | None:
    """Optional helper for deployments that save thresholds in `.npz` files."""
    if not path.exists():
        return None
    values = np.load(path)
    if key not in values:
        return None
    return float(values[key])
