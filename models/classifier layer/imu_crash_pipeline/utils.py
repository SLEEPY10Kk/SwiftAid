from __future__ import annotations

import logging
from pathlib import Path
import random

import numpy as np
import torch


def setup_logging(level: int = logging.INFO) -> None:
    logging.basicConfig(
        level=level,
        format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
    )


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
    torch.backends.cudnn.benchmark = False
    torch.backends.cudnn.deterministic = True


def get_device() -> torch.device:
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")


def ensure_dir(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path


def validate_windows(windows: np.ndarray, expected_features: int = 6) -> np.ndarray:
    if not isinstance(windows, np.ndarray):
        raise TypeError("windows must be a NumPy array.")
    if windows.ndim != 3:
        raise ValueError("Expected windows with shape [num_windows, timesteps, features].")
    if windows.shape[-1] != expected_features:
        raise ValueError(f"Expected {expected_features} features, got {windows.shape[-1]}.")
    if not np.isfinite(windows).all():
        raise ValueError("windows contains NaN or infinite values.")
    return windows.astype(np.float32, copy=False)


def load_windows(path: Path | None, expected_features: int = 6) -> np.ndarray | None:
    if path is None:
        return None
    if not path.exists():
        raise FileNotFoundError(f"Window file not found: {path}")
    return validate_windows(np.load(path), expected_features=expected_features)
