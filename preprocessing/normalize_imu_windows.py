from __future__ import annotations

import pickle
from pathlib import Path

import numpy as np

try:
    from sklearn.preprocessing import StandardScaler
except ModuleNotFoundError as exc:
    if exc.name != "sklearn":
        raise
    raise ModuleNotFoundError(
        "scikit-learn is required for z-score normalization. "
        "Install it with: pip install scikit-learn"
    ) from exc


WINDOWS_PATH = Path(__file__).resolve().parent / "filtered_imu_windows.npy"
OUTPUT_DIR = Path(__file__).resolve().parent / "normalized_imu"
SCALER_PATH = OUTPUT_DIR / "imu_zscore_scaler.pkl"
STATS_PATH = OUTPUT_DIR / "imu_zscore_stats.npz"


def fit_imu_scaler(train_windows: np.ndarray) -> StandardScaler:
    """
    Fit z-score normalization on training data only.

    Args:
        train_windows: Array shaped [num_windows, timesteps, features].

    Returns:
        Fitted sklearn StandardScaler with one mean/std per feature.
    """
    validate_window_array(train_windows, name="train_windows")
    _, _, num_features = train_windows.shape
    flattened = train_windows.reshape(-1, num_features)

    scaler = StandardScaler()
    scaler.fit(flattened)
    return scaler


def normalize_imu_windows(windows: np.ndarray, scaler: StandardScaler) -> np.ndarray:
    """
    Apply an already-fitted scaler to IMU windows.

    Args:
        windows: Array shaped [num_windows, timesteps, features].
        scaler: Fitted StandardScaler from training data.

    Returns:
        Normalized array with the same shape as input.
    """
    validate_window_array(windows, name="windows")
    original_shape = windows.shape
    flattened = windows.reshape(-1, original_shape[-1])
    normalized = scaler.transform(flattened)
    return normalized.reshape(original_shape).astype(np.float32)


def save_scaler_and_stats(
    scaler: StandardScaler,
    scaler_path: Path = SCALER_PATH,
    stats_path: Path = STATS_PATH,
) -> None:
    """Save the fitted scaler plus explicit mean/std arrays for reproducibility."""
    scaler_path.parent.mkdir(parents=True, exist_ok=True)
    with scaler_path.open("wb") as file:
        pickle.dump(scaler, file)
    np.savez(
        stats_path,
        mean=scaler.mean_.astype(np.float32),
        std=scaler.scale_.astype(np.float32),
        var=scaler.var_.astype(np.float32),
    )


def load_scaler(scaler_path: Path = SCALER_PATH) -> StandardScaler:
    """Load a previously fitted StandardScaler."""
    with scaler_path.open("rb") as file:
        return pickle.load(file)


def train_val_test_split(
    windows: np.ndarray,
    train_fraction: float = 0.70,
    val_fraction: float = 0.15,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    Chronological split for continuous time-series windows.

    This avoids shuffling overlapping windows across splits.
    """
    validate_window_array(windows, name="windows")
    if not 0 < train_fraction < 1:
        raise ValueError("train_fraction must be between 0 and 1.")
    if not 0 <= val_fraction < 1:
        raise ValueError("val_fraction must be in the range [0, 1).")
    if train_fraction + val_fraction >= 1:
        raise ValueError("train_fraction + val_fraction must be below 1.")

    num_windows = len(windows)
    train_end = int(num_windows * train_fraction)
    val_end = train_end + int(num_windows * val_fraction)
    return windows[:train_end], windows[train_end:val_end], windows[val_end:]


def normalize_train_val_test(
    train_windows: np.ndarray,
    val_windows: np.ndarray,
    test_windows: np.ndarray,
    scaler_path: Path = SCALER_PATH,
    stats_path: Path = STATS_PATH,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, StandardScaler]:
    """
    Fit on train only, save statistics, then transform train/validation/test.
    """
    scaler = fit_imu_scaler(train_windows)
    save_scaler_and_stats(scaler, scaler_path=scaler_path, stats_path=stats_path)

    train_norm = normalize_imu_windows(train_windows, scaler)
    val_norm = normalize_imu_windows(val_windows, scaler)
    test_norm = normalize_imu_windows(test_windows, scaler)
    return train_norm, val_norm, test_norm, scaler


def validate_window_array(windows: np.ndarray, name: str) -> None:
    """Validate expected [num_windows, timesteps, features] IMU shape."""
    if not isinstance(windows, np.ndarray):
        raise TypeError(f"{name} must be a NumPy array.")
    if windows.ndim != 3:
        raise ValueError(f"{name} must have shape [num_windows, timesteps, features].")
    if windows.shape[-1] < 1:
        raise ValueError(f"{name} must contain at least one feature.")
    if not np.isfinite(windows).all():
        raise ValueError(f"{name} contains NaN or infinite values.")


def main() -> None:
    windows = np.load(WINDOWS_PATH)
    train_windows, val_windows, test_windows = train_val_test_split(windows)

    train_norm, val_norm, test_norm, scaler = normalize_train_val_test(
        train_windows,
        val_windows,
        test_windows,
    )

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    np.save(OUTPUT_DIR / "train_windows_zscore.npy", train_norm)
    np.save(OUTPUT_DIR / "val_windows_zscore.npy", val_norm)
    np.save(OUTPUT_DIR / "test_windows_zscore.npy", test_norm)

    print(f"Saved scaler: {SCALER_PATH}")
    print(f"Saved mean/std stats: {STATS_PATH}")
    print(f"Train normalized shape: {train_norm.shape}")
    print(f"Validation normalized shape: {val_norm.shape}")
    print(f"Test normalized shape: {test_norm.shape}")
    print(f"Feature means from training data: {scaler.mean_}")
    print(f"Feature stds from training data: {scaler.scale_}")


if __name__ == "__main__":
    main()
