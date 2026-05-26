from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np


FEATURE_COLUMNS = ("ax", "ay", "az", "gx", "gy", "gz")
WINDOWS_PATH = Path(__file__).resolve().parent / "filtered_imu_windows.npy"
OUTPUT_DIR = Path(__file__).resolve().parent / "imu_anomaly_baseline"
SCORES_CSV = OUTPUT_DIR / "window_anomaly_scores.csv"
PLOT_PATH = OUTPUT_DIR / "anomaly_scores.png"
THRESHOLDS_PATH = OUTPUT_DIR / "anomaly_thresholds.npz"


@dataclass(frozen=True)
class BaselineConfig:
    sample_rate_hz: float = 50.0
    threshold_percentile: float = 99.0
    robust_z_threshold: float = 6.0
    feature_columns: tuple[str, ...] = FEATURE_COLUMNS


def validate_windows(windows: np.ndarray, feature_columns: tuple[str, ...] = FEATURE_COLUMNS) -> None:
    if not isinstance(windows, np.ndarray):
        raise TypeError("windows must be a NumPy array.")
    if windows.ndim != 3:
        raise ValueError("windows must have shape [num_windows, timesteps, features].")
    if windows.shape[-1] != len(feature_columns):
        raise ValueError(f"Expected {len(feature_columns)} features: {feature_columns}.")
    if not np.isfinite(windows).all():
        raise ValueError("windows contains NaN or infinite values.")


def compute_signal_magnitudes(
    windows: np.ndarray,
    feature_columns: tuple[str, ...] = FEATURE_COLUMNS,
) -> tuple[np.ndarray, np.ndarray]:
    """Return acceleration and gyroscope magnitudes per timestep."""
    validate_windows(windows, feature_columns)
    col_to_idx = {name: idx for idx, name in enumerate(feature_columns)}

    accel = windows[:, :, [col_to_idx["ax"], col_to_idx["ay"], col_to_idx["az"]]]
    gyro = windows[:, :, [col_to_idx["gx"], col_to_idx["gy"], col_to_idx["gz"]]]

    accel_mag = np.linalg.norm(accel, axis=2)
    gyro_mag = np.linalg.norm(gyro, axis=2)
    return accel_mag, gyro_mag


def compute_jerk(accel_mag: np.ndarray, sample_rate_hz: float) -> np.ndarray:
    """
    Compute jerk as the time derivative of acceleration magnitude.

    Shape is [num_windows, timesteps - 1].
    """
    if sample_rate_hz <= 0:
        raise ValueError("sample_rate_hz must be positive.")
    return np.diff(accel_mag, axis=1) * sample_rate_hz


def extract_window_features(windows: np.ndarray, config: BaselineConfig) -> dict[str, np.ndarray]:
    """Create interpretable statistical crash/anomaly features per window."""
    accel_mag, gyro_mag = compute_signal_magnitudes(windows, config.feature_columns)
    jerk = compute_jerk(accel_mag, config.sample_rate_hz)
    abs_jerk = np.abs(jerk)

    return {
        "accel_mag_max": accel_mag.max(axis=1),
        "accel_mag_p95": np.percentile(accel_mag, 95, axis=1),
        "gyro_mag_max": gyro_mag.max(axis=1),
        "gyro_mag_p95": np.percentile(gyro_mag, 95, axis=1),
        "jerk_abs_max": abs_jerk.max(axis=1),
        "jerk_abs_p95": np.percentile(abs_jerk, 95, axis=1),
    }


def fit_statistical_thresholds(
    reference_features: dict[str, np.ndarray],
    config: BaselineConfig,
) -> dict[str, dict[str, float]]:
    """
    Fit thresholds from normal/reference windows.

    Threshold policy:
    - Percentile threshold catches rare high-energy events in each feature.
    - Robust z threshold uses median + k * MAD-scaled sigma for outlier resistance.
    - Final feature threshold is the larger of the two, which reduces false positives
      when the reference distribution has occasional benign spikes.
    """
    thresholds: dict[str, dict[str, float]] = {}
    for name, values in reference_features.items():
        values = np.asarray(values, dtype=np.float64)
        percentile_threshold = float(np.percentile(values, config.threshold_percentile))
        median = float(np.median(values))
        mad = float(np.median(np.abs(values - median)))
        robust_sigma = 1.4826 * mad
        robust_threshold = median + config.robust_z_threshold * robust_sigma
        final_threshold = max(percentile_threshold, robust_threshold)

        thresholds[name] = {
            "percentile_threshold": percentile_threshold,
            "median": median,
            "mad": mad,
            "robust_sigma": robust_sigma,
            "robust_threshold": float(robust_threshold),
            "final_threshold": float(final_threshold),
        }
    return thresholds


def calculate_anomaly_scores(
    features: dict[str, np.ndarray],
    thresholds: dict[str, dict[str, float]],
) -> tuple[np.ndarray, np.ndarray]:
    """
    Score each window by its strongest normalized threshold exceedance.

    A score above 1.0 means at least one feature crossed its fitted threshold.
    """
    per_feature_ratios = []
    for name, values in features.items():
        threshold = thresholds[name]["final_threshold"]
        if threshold <= 0:
            ratio = np.zeros_like(values, dtype=np.float64)
        else:
            ratio = np.asarray(values, dtype=np.float64) / threshold
        per_feature_ratios.append(ratio)

    ratio_matrix = np.vstack(per_feature_ratios).T
    anomaly_scores = ratio_matrix.max(axis=1)
    abnormal_mask = anomaly_scores > 1.0
    return anomaly_scores, abnormal_mask


def detect_abnormal_windows(
    windows: np.ndarray,
    reference_windows: np.ndarray | None = None,
    config: BaselineConfig = BaselineConfig(),
) -> tuple[dict[str, np.ndarray], dict[str, dict[str, float]], np.ndarray, np.ndarray]:
    """
    Fit thresholds on reference windows, then detect abnormal windows.

    Use known non-crash training data as reference_windows when available.
    If omitted, thresholds are fit on the full input as an unsupervised baseline.
    """
    reference = windows if reference_windows is None else reference_windows
    reference_features = extract_window_features(reference, config)
    thresholds = fit_statistical_thresholds(reference_features, config)

    features = extract_window_features(windows, config)
    anomaly_scores, abnormal_mask = calculate_anomaly_scores(features, thresholds)
    return features, thresholds, anomaly_scores, abnormal_mask


def save_thresholds(thresholds: dict[str, dict[str, float]], output_path: Path = THRESHOLDS_PATH) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    flattened = {
        f"{feature}_{stat_name}": stat_value
        for feature, stats in thresholds.items()
        for stat_name, stat_value in stats.items()
    }
    np.savez(output_path, **flattened)


def save_scores_csv(
    features: dict[str, np.ndarray],
    anomaly_scores: np.ndarray,
    abnormal_mask: np.ndarray,
    output_path: Path = SCORES_CSV,
) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    header = ["window_index", *features.keys(), "anomaly_score", "is_abnormal"]
    rows = []
    for idx in range(len(anomaly_scores)):
        rows.append(
            [
                idx,
                *[features[name][idx] for name in features],
                anomaly_scores[idx],
                int(abnormal_mask[idx]),
            ]
        )

    np.savetxt(
        output_path,
        np.asarray(rows, dtype=np.float64),
        delimiter=",",
        header=",".join(header),
        comments="",
    )


def plot_anomaly_scores(
    anomaly_scores: np.ndarray,
    abnormal_mask: np.ndarray,
    output_path: Path = PLOT_PATH,
) -> None:
    import matplotlib.pyplot as plt

    output_path.parent.mkdir(parents=True, exist_ok=True)
    window_index = np.arange(len(anomaly_scores))

    fig, ax = plt.subplots(figsize=(13, 5))
    ax.plot(window_index, anomaly_scores, label="Anomaly score", linewidth=1.5)
    ax.axhline(1.0, color="black", linestyle="--", linewidth=1, label="Abnormal threshold")
    ax.scatter(
        window_index[abnormal_mask],
        anomaly_scores[abnormal_mask],
        color="red",
        s=28,
        label="Abnormal windows",
        zorder=3,
    )
    ax.set_title("Statistical IMU Crash Anomaly Scores")
    ax.set_xlabel("Window index")
    ax.set_ylabel("Max feature-to-threshold ratio")
    ax.grid(True, alpha=0.25)
    ax.legend(loc="upper right")
    fig.tight_layout()
    fig.savefig(output_path, dpi=160)
    plt.close(fig)


def main() -> None:
    config = BaselineConfig(sample_rate_hz=50.0, threshold_percentile=99.0, robust_z_threshold=6.0)
    windows = np.load(WINDOWS_PATH)

    features, thresholds, anomaly_scores, abnormal_mask = detect_abnormal_windows(
        windows,
        reference_windows=None,
        config=config,
    )

    save_thresholds(thresholds)
    save_scores_csv(features, anomaly_scores, abnormal_mask)

    try:
        plot_anomaly_scores(anomaly_scores, abnormal_mask)
        print(f"Saved anomaly-score plot: {PLOT_PATH}")
    except ModuleNotFoundError as exc:
        if exc.name != "matplotlib":
            raise
        print("Skipped anomaly-score plot because matplotlib is not installed.")

    print(f"Saved anomaly scores: {SCORES_CSV}")
    print(f"Saved thresholds: {THRESHOLDS_PATH}")
    print(f"Detected abnormal windows: {int(abnormal_mask.sum())} / {len(abnormal_mask)}")
    print("Threshold selection:")
    print(
        "For each feature, the final threshold is max("
        f"{config.threshold_percentile}th percentile, "
        f"median + {config.robust_z_threshold} * 1.4826 * MAD)."
    )
    print("A window is abnormal when its max feature-to-threshold ratio exceeds 1.0.")


if __name__ == "__main__":
    main()
