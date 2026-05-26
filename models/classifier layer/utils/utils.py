"""
utils/utils.py
==============
Shared utilities: reproducibility, logging, data helpers.
"""

import os
import random
import logging
import numpy as np
import pandas as pd
import torch


def set_seed(seed: int = 42) -> None:
    """Fix all random seeds for full reproducibility."""
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
    # Make cuDNN deterministic (slight speed cost)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark     = False


def get_logger(name: str, level: int = logging.INFO) -> logging.Logger:
    """Return a named logger with a clean console handler."""
    logger = logging.getLogger(name)
    if not logger.handlers:
        handler = logging.StreamHandler()
        fmt = logging.Formatter(
            "[%(asctime)s] %(levelname)-8s %(name)s — %(message)s",
            datefmt="%H:%M:%S",
        )
        handler.setFormatter(fmt)
        logger.addHandler(handler)
    logger.setLevel(level)
    return logger


def save_csv(df: pd.DataFrame, path: str) -> None:
    """Save a DataFrame, creating parent dirs as needed."""
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    df.to_csv(path, index=False)
    logging.getLogger("utils").info(f"Saved CSV → {path}")


def acc_magnitude(windows: np.ndarray) -> np.ndarray:
    """
    Compute per-timestep acceleration magnitude from window array.

    Args:
        windows: shape [N, T, F] where F[0:3] = acc_x, acc_y, acc_z

    Returns:
        mag: shape [N, T]
    """
    return np.sqrt(np.sum(windows[:, :, :3] ** 2, axis=-1))


def gyro_magnitude(windows: np.ndarray) -> np.ndarray:
    """
    Compute per-timestep gyroscope magnitude.

    Args:
        windows: shape [N, T, F] where F[3:6] = gyro_x, gyro_y, gyro_z

    Returns:
        mag: shape [N, T]
    """
    return np.sqrt(np.sum(windows[:, :, 3:6] ** 2, axis=-1))


def compute_jerk(windows: np.ndarray, dt: float = 0.01) -> np.ndarray:
    """
    Jerk = rate of change of acceleration (m/s³ proxy).
    Useful crash feature: impacts produce extreme jerk spikes.

    Args:
        windows: shape [N, T, F]
        dt     : sampling interval in seconds

    Returns:
        jerk_mag: shape [N, T]  (first sample = 0)
    """
    acc = windows[:, :, :3]                      # [N, T, 3]
    d_acc = np.diff(acc, axis=1, prepend=acc[:, :1, :]) / dt  # [N, T, 3]
    return np.sqrt(np.sum(d_acc ** 2, axis=-1))  # [N, T]


def windows_to_tensor(windows: np.ndarray, device: torch.device) -> torch.Tensor:
    """Convert numpy [N, T, F] windows to float32 torch tensor on device."""
    return torch.tensor(windows, dtype=torch.float32).to(device)
