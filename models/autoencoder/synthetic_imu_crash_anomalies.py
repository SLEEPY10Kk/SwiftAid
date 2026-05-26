from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np


FEATURE_COLUMNS = ("ax", "ay", "az", "gx", "gy", "gz")
WINDOWS_PATH = Path(__file__).resolve().parent / "filtered_imu_windows.npy"
OUTPUT_DIR = Path(__file__).resolve().parent / "synthetic_crash_anomalies"
AUGMENTED_WINDOWS_PATH = OUTPUT_DIR / "imu_windows_with_synthetic_crashes.npy"
SYNTHETIC_LABELS_PATH = OUTPUT_DIR / "synthetic_crash_labels.npy"
EXAMPLE_PLOT_PATH = OUTPUT_DIR / "original_vs_synthetic_crash.png"


@dataclass(frozen=True)
class SyntheticCrashConfig:
    sample_rate_hz: float = 50.0
    anomaly_fraction: float = 0.15
    min_event_duration_s: float = 0.25
    max_event_duration_s: float = 0.90
    acceleration_spike_g_range: tuple[float, float] = (2.5, 7.0)
    rotational_spike_range: tuple[float, float] = (2.0, 8.0)
    abrupt_stop_strength_range: tuple[float, float] = (0.35, 0.85)
    noise_scale: float = 0.03
    seed: int = 42


def validate_windows(windows: np.ndarray) -> None:
    if not isinstance(windows, np.ndarray):
        raise TypeError("windows must be a NumPy array.")
    if windows.ndim != 3:
        raise ValueError("windows must have shape [num_windows, timesteps, features].")
    if windows.shape[-1] != len(FEATURE_COLUMNS):
        raise ValueError(f"Expected features {FEATURE_COLUMNS}.")
    if not np.isfinite(windows).all():
        raise ValueError("windows contains NaN or infinite values.")


def smooth_event_envelope(length: int) -> np.ndarray:
    """
    Create a bell-shaped envelope so injected crashes are abrupt but not single-sample artifacts.

    A Hann envelope preserves temporal continuity around the injected event.
    """
    if length < 3:
        return np.ones(length, dtype=np.float32)
    return np.hanning(length).astype(np.float32)


def random_unit_vector(rng: np.random.Generator, size: int = 3) -> np.ndarray:
    vector = rng.normal(size=size)
    norm = np.linalg.norm(vector)
    if norm == 0:
        vector[0] = 1.0
        norm = 1.0
    return vector / norm


def choose_event_slice(
    timesteps: int,
    sample_rate_hz: float,
    config: SyntheticCrashConfig,
    rng: np.random.Generator,
) -> slice:
    min_len = max(3, int(round(config.min_event_duration_s * sample_rate_hz)))
    max_len = max(min_len, int(round(config.max_event_duration_s * sample_rate_hz)))
    event_len = int(rng.integers(min_len, min(max_len, timesteps) + 1))
    start = int(rng.integers(0, timesteps - event_len + 1))
    return slice(start, start + event_len)


def inject_acceleration_spike(
    window: np.ndarray,
    event_slice: slice,
    config: SyntheticCrashConfig,
    rng: np.random.Generator,
) -> None:
    """Inject a high-magnitude acceleration pulse across ax/ay/az."""
    length = event_slice.stop - event_slice.start
    envelope = smooth_event_envelope(length)[:, None]
    direction = random_unit_vector(rng)
    spike_magnitude = rng.uniform(*config.acceleration_spike_g_range) * 9.81
    spike = envelope * direction[None, :] * spike_magnitude
    window[event_slice, 0:3] += spike


def inject_rotational_change(
    window: np.ndarray,
    event_slice: slice,
    config: SyntheticCrashConfig,
    rng: np.random.Generator,
) -> None:
    """Inject sudden gyroscope changes across gx/gy/gz."""
    length = event_slice.stop - event_slice.start
    envelope = smooth_event_envelope(length)[:, None]
    direction = random_unit_vector(rng)
    rotational_magnitude = rng.uniform(*config.rotational_spike_range)
    rotational_spike = envelope * direction[None, :] * rotational_magnitude
    window[event_slice, 3:6] += rotational_spike


def simulate_abrupt_stop(
    window: np.ndarray,
    event_slice: slice,
    config: SyntheticCrashConfig,
    rng: np.random.Generator,
) -> None:
    """
    Simulate a crash stop by damping motion after impact.

    This creates a high-energy event followed by reduced acceleration/rotation variation,
    which is closer to crash dynamics than isolated spikes.
    """
    stop_start = event_slice.stop
    if stop_start >= len(window):
        return

    stop_strength = rng.uniform(*config.abrupt_stop_strength_range)
    tail_len = len(window) - stop_start
    damping = np.linspace(1.0 - stop_strength, 1.0 - 0.5 * stop_strength, tail_len)[:, None]

    accel_baseline = np.median(window[:, 0:3], axis=0, keepdims=True)
    gyro_baseline = np.median(window[:, 3:6], axis=0, keepdims=True)
    window[stop_start:, 0:3] = accel_baseline + (window[stop_start:, 0:3] - accel_baseline) * damping
    window[stop_start:, 3:6] = gyro_baseline + (window[stop_start:, 3:6] - gyro_baseline) * damping


def add_sensor_noise(
    window: np.ndarray,
    config: SyntheticCrashConfig,
    rng: np.random.Generator,
) -> None:
    """Add small feature-scaled noise so synthetic events do not look perfectly smooth."""
    feature_std = np.std(window, axis=0, keepdims=True)
    noise = rng.normal(0.0, config.noise_scale, size=window.shape) * np.maximum(feature_std, 1e-6)
    window += noise.astype(window.dtype)


def inject_synthetic_crash(
    window: np.ndarray,
    config: SyntheticCrashConfig,
    rng: np.random.Generator,
) -> np.ndarray:
    """
    Inject a crash-like event while preserving surrounding temporal structure.

    The original signal remains intact outside the event and damped post-impact region.
    """
    modified = window.copy()
    event_slice = choose_event_slice(
        timesteps=modified.shape[0],
        sample_rate_hz=config.sample_rate_hz,
        config=config,
        rng=rng,
    )

    inject_acceleration_spike(modified, event_slice, config, rng)
    inject_rotational_change(modified, event_slice, config, rng)
    simulate_abrupt_stop(modified, event_slice, config, rng)
    add_sensor_noise(modified, config, rng)
    return modified


def generate_synthetic_crash_dataset(
    windows: np.ndarray,
    config: SyntheticCrashConfig = SyntheticCrashConfig(),
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    Create a copy of the dataset with synthetic crash anomalies injected.

    Returns:
        augmented_windows: same shape as windows
        labels: 0 for original/normal, 1 for synthetic crash
        anomaly_indices: window indices that were modified
    """
    validate_windows(windows)
    rng = np.random.default_rng(config.seed)
    augmented = windows.astype(np.float32, copy=True)
    labels = np.zeros(len(windows), dtype=np.int64)

    num_anomalies = max(1, int(round(len(windows) * config.anomaly_fraction)))
    anomaly_indices = np.sort(rng.choice(len(windows), size=num_anomalies, replace=False))

    for idx in anomaly_indices:
        augmented[idx] = inject_synthetic_crash(augmented[idx], config, rng)
        labels[idx] = 1

    return augmented, labels, anomaly_indices


def plot_original_vs_modified(
    original_window: np.ndarray,
    modified_window: np.ndarray,
    output_path: Path = EXAMPLE_PLOT_PATH,
) -> None:
    """Visualize original and synthetic crash IMU signals for one sample window."""
    import matplotlib.pyplot as plt

    output_path.parent.mkdir(parents=True, exist_ok=True)
    timesteps = np.arange(original_window.shape[0])
    original_accel_mag = np.linalg.norm(original_window[:, 0:3], axis=1)
    modified_accel_mag = np.linalg.norm(modified_window[:, 0:3], axis=1)
    original_gyro_mag = np.linalg.norm(original_window[:, 3:6], axis=1)
    modified_gyro_mag = np.linalg.norm(modified_window[:, 3:6], axis=1)

    fig, axes = plt.subplots(2, 1, figsize=(13, 7), sharex=True)
    axes[0].plot(timesteps, original_accel_mag, label="Original accel magnitude", linewidth=1.6)
    axes[0].plot(timesteps, modified_accel_mag, label="Synthetic crash accel magnitude", linewidth=1.6)
    axes[0].set_ylabel("Acceleration magnitude")
    axes[0].grid(True, alpha=0.25)
    axes[0].legend(loc="upper right")

    axes[1].plot(timesteps, original_gyro_mag, label="Original gyro magnitude", linewidth=1.6)
    axes[1].plot(timesteps, modified_gyro_mag, label="Synthetic crash gyro magnitude", linewidth=1.6)
    axes[1].set_xlabel("Timestep")
    axes[1].set_ylabel("Gyroscope magnitude")
    axes[1].grid(True, alpha=0.25)
    axes[1].legend(loc="upper right")

    fig.suptitle("Original vs Synthetic Crash-Like IMU Window")
    fig.tight_layout()
    fig.savefig(output_path, dpi=160)
    plt.close(fig)


def main() -> None:
    config = SyntheticCrashConfig()
    windows = np.load(WINDOWS_PATH)
    augmented, labels, anomaly_indices = generate_synthetic_crash_dataset(windows, config)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    np.save(AUGMENTED_WINDOWS_PATH, augmented)
    np.save(SYNTHETIC_LABELS_PATH, labels)

    try:
        example_idx = int(anomaly_indices[0])
        plot_original_vs_modified(windows[example_idx], augmented[example_idx])
        print(f"Saved comparison plot: {EXAMPLE_PLOT_PATH}")
    except ModuleNotFoundError as exc:
        if exc.name != "matplotlib":
            raise
        print("Skipped comparison plot because matplotlib is not installed.")

    print(f"Saved augmented windows: {AUGMENTED_WINDOWS_PATH}")
    print(f"Saved synthetic labels: {SYNTHETIC_LABELS_PATH}")
    print(f"Injected synthetic crashes: {int(labels.sum())} / {len(labels)}")
    print(f"First modified window index: {int(anomaly_indices[0])}")


if __name__ == "__main__":
    main()
