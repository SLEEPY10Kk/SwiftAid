from __future__ import annotations

import numpy as np
import pandas as pd

from .config import FEATURE_COLUMNS
from .utils import validate_windows


def _impact_duration(signal: np.ndarray, sample_rate_hz: float) -> float:
    threshold = np.percentile(signal, 95)
    return float(np.sum(signal >= threshold) / sample_rate_hz)


def _stats(prefix: str, values: np.ndarray) -> dict[str, float]:
    return {
        f"{prefix}_mean": float(np.mean(values)),
        f"{prefix}_std": float(np.std(values)),
        f"{prefix}_min": float(np.min(values)),
        f"{prefix}_max": float(np.max(values)),
        f"{prefix}_p95": float(np.percentile(values, 95)),
        f"{prefix}_energy": float(np.mean(values**2)),
    }


def extract_window_features(
    windows: np.ndarray,
    sample_rate_hz: float,
    reconstruction_errors: np.ndarray | None = None,
    per_feature_errors: np.ndarray | None = None,
) -> pd.DataFrame:
    windows = validate_windows(windows)
    rows: list[dict[str, float]] = []
    dt = 1.0 / sample_rate_hz
    for idx, window in enumerate(windows):
        accel = window[:, :3]
        gyro = window[:, 3:]
        accel_mag = np.linalg.norm(accel, axis=1)
        gyro_mag = np.linalg.norm(gyro, axis=1)
        jerk = np.linalg.norm(np.diff(accel, axis=0, prepend=accel[[0]]) / dt, axis=1)
        row = {
            "window_index": float(idx),
            "accel_mag_mean": float(accel_mag.mean()),
            "accel_mag_std": float(accel_mag.std()),
            "accel_mag_peak": float(accel_mag.max()),
            "gyro_mag_mean": float(gyro_mag.mean()),
            "gyro_mag_std": float(gyro_mag.std()),
            "gyro_mag_peak": float(gyro_mag.max()),
            "jerk_mean": float(jerk.mean()),
            "jerk_std": float(jerk.std()),
            "jerk_peak": float(jerk.max()),
            "impact_duration_s": _impact_duration(accel_mag, sample_rate_hz),
            "peak_acceleration": float(accel_mag.max()),
            "rotational_energy": float(np.mean(gyro_mag**2)),
        }
        if reconstruction_errors is not None:
            row["reconstruction_error"] = float(reconstruction_errors[idx])
        if per_feature_errors is not None:
            for feature_idx, feature in enumerate(FEATURE_COLUMNS):
                row[f"{feature}_reconstruction_error"] = float(per_feature_errors[idx, feature_idx])
        for feature_idx, feature in enumerate(FEATURE_COLUMNS):
            row.update(_stats(feature, window[:, feature_idx]))
        rows.append(row)
    return pd.DataFrame(rows)
