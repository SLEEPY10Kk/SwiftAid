from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import numpy as np
import pandas as pd


FEATURE_COLUMNS = ("ax", "ay", "az", "gx", "gy", "gz")
DEFAULT_INPUT_CSV = Path(__file__).resolve().parent / "merged_synchronized_50hz.csv"
DEFAULT_OUTPUT_NPY = Path(__file__).resolve().parent / "imu_windows_200_50overlap.npy"
DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parent / "motion_filtered_imu_windows"
DEFAULT_ORIGINAL_OUTPUT_NPY = DEFAULT_OUTPUT_DIR / "original_windows.npy"
DEFAULT_FILTERED_OUTPUT_NPY = DEFAULT_OUTPUT_DIR / "filtered_windows.npy"
DEFAULT_MOTION_SCORES_NPY = DEFAULT_OUTPUT_DIR / "motion_scores.npy"
DEFAULT_RETAINED_INDICES_NPY = DEFAULT_OUTPUT_DIR / "retained_indices.npy"
DEFAULT_PLOT = Path(__file__).resolve().parent / "sample_imu_window.png"
DEFAULT_MOTION_PLOT = DEFAULT_OUTPUT_DIR / "motion_score_histogram_before.png"
DEFAULT_MOTION_BOXPLOT = DEFAULT_OUTPUT_DIR / "motion_score_boxplot.png"
DEFAULT_FILTERED_MOTION_PLOT = DEFAULT_OUTPUT_DIR / "motion_score_histogram_after.png"
DEFAULT_STATIONARY_SAMPLE_PLOT = DEFAULT_OUTPUT_DIR / "sample_stationary_window.png"
DEFAULT_MEDIUM_SAMPLE_PLOT = DEFAULT_OUTPUT_DIR / "sample_medium_motion_window.png"
DEFAULT_HIGH_SAMPLE_PLOT = DEFAULT_OUTPUT_DIR / "sample_high_motion_window.png"


@dataclass(frozen=True)
class WindowConfig:
    window_size: int = 200
    overlap: float = 0.50
    feature_columns: tuple[str, ...] = FEATURE_COLUMNS
    motion_metric: str = "mean_abs_diff"
    motion_threshold_strategy: str = "percentile"
    motion_percentile_threshold: float = 15.0
    low_motion_retain_ratio: float = 0.2
    remove_duplicates: bool = True
    duplicate_similarity_threshold: float = 0.995
    enable_motion_balancing: bool = True

    @property
    def step_size(self) -> int:
        if not 0 <= self.overlap < 1:
            raise ValueError("overlap must be in the range [0, 1).")
        step = int(round(self.window_size * (1.0 - self.overlap)))
        if step < 1:
            raise ValueError("overlap is too large; computed step size is below 1.")
        return step


def load_imu_data(csv_path: Path, feature_columns: Iterable[str] = FEATURE_COLUMNS) -> pd.DataFrame:
    """Load IMU data and keep only the required accelerometer/gyroscope features."""
    df = pd.read_csv(csv_path)
    feature_columns = tuple(feature_columns)
    missing = sorted(set(feature_columns) - set(df.columns))

    if missing:
        raise ValueError(f"Missing required IMU columns: {missing}")

    features = df.loc[:, feature_columns].apply(pd.to_numeric, errors="coerce")
    if features.isna().any().any():
        bad_rows = int(features.isna().any(axis=1).sum())
        raise ValueError(f"Found non-numeric or missing IMU values in {bad_rows} rows.")

    return features


def create_sliding_windows(data: pd.DataFrame | np.ndarray, config: WindowConfig) -> np.ndarray:
    """
    Convert continuous IMU samples into overlapping fixed-size windows.

    Returns:
        NumPy array shaped as [num_windows, timesteps, features].
    """
    values = data.to_numpy(dtype=np.float32) if isinstance(data, pd.DataFrame) else np.asarray(data, dtype=np.float32)

    if values.ndim != 2:
        raise ValueError("data must be 2D with shape [timesteps, features].")
    if len(values) < config.window_size:
        raise ValueError(
            f"Need at least {config.window_size} timesteps, received {len(values)}."
        )

    starts = np.arange(0, len(values) - config.window_size + 1, config.step_size)
    windows = np.stack([values[start : start + config.window_size] for start in starts])
    return windows


def validate_window_array(windows: np.ndarray, name: str = "windows") -> None:
    """Validate expected [num_windows, timesteps, features] IMU shape."""
    if not isinstance(windows, np.ndarray):
        raise TypeError(f"{name} must be a NumPy array.")
    if windows.ndim != 3:
        raise ValueError(f"{name} must have shape [num_windows, timesteps, features].")
    if windows.shape[0] < 1:
        raise ValueError(f"{name} must contain at least one window.")
    if windows.shape[-1] < 1:
        raise ValueError(f"{name} must contain at least one feature.")
    if not np.isfinite(windows).all():
        raise ValueError(f"{name} contains NaN or infinite values.")


def compute_window_motion_scores(
    windows: np.ndarray,
    method: str = "mean_abs_diff",
) -> np.ndarray:
    """
    Compute one motion-intensity score per IMU window.

    Low-motion filtering matters because overlapping windows can produce many
    near-duplicate idle segments. Reducing that redundancy helps representation
    learning focus on meaningful driving dynamics instead of flat signals.
    """
    validate_window_array(windows)
    method = method.lower()

    if method == "mean_abs_diff":
        if windows.shape[1] < 2:
            raise ValueError("mean_abs_diff requires windows with at least two timesteps.")

        # For each window, measure average frame-to-frame change across time and
        # all IMU channels. In the batched array, axis=1 is the timestep axis.
        return np.mean(np.abs(np.diff(windows, axis=1)), axis=(1, 2)).astype(np.float64)

    if method == "std":
        # Overall variability is a simpler motion proxy that treats each window
        # as one compact block of accelerometer and gyroscope activity.
        return np.std(windows, axis=(1, 2)).astype(np.float64)

    if method == "rms_energy":
        # RMS energy captures the absolute signal magnitude in each window. It
        # complements derivative-based motion when the phone is tilted or under
        # sustained acceleration.
        return np.sqrt(np.mean(np.square(windows, dtype=np.float64), axis=(1, 2)))

    raise ValueError("method must be one of: 'mean_abs_diff', 'rms_energy', 'std'.")


def summarize_motion_scores(motion_scores: np.ndarray) -> dict[str, float]:
    """Return descriptive statistics used to inspect motion imbalance."""
    scores = np.asarray(motion_scores, dtype=np.float64)
    if scores.ndim != 1 or len(scores) < 1:
        raise ValueError("motion_scores must be a non-empty 1D NumPy array.")
    if not np.isfinite(scores).all():
        raise ValueError("motion_scores contains NaN or infinite values.")

    summary = {
        "total": float(len(scores)),
        "min": float(scores.min()),
        "max": float(scores.max()),
        "mean": float(scores.mean()),
        "median": float(np.median(scores)),
        "std": float(scores.std()),
    }
    for percentile in (5, 10, 15, 25, 50, 75, 90, 95):
        summary[f"p{percentile}"] = float(np.percentile(scores, percentile))
    return summary


def print_motion_score_statistics(motion_scores: np.ndarray, title: str = "Motion score statistics") -> None:
    """Print detailed score statistics for threshold tuning and diagnostics."""
    summary = summarize_motion_scores(motion_scores)
    print(f"{title}:")
    print(f"  Total windows: {int(summary['total'])}")
    print(f"  Min motion score: {summary['min']:.6g}")
    print(f"  Max motion score: {summary['max']:.6g}")
    print(f"  Mean motion score: {summary['mean']:.6g}")
    print(f"  Median motion score: {summary['median']:.6g}")
    print(f"  Standard deviation: {summary['std']:.6g}")
    print("  Percentiles:")
    for percentile in (5, 10, 15, 25, 50, 75, 90, 95):
        print(f"    p{percentile}: {summary[f'p{percentile}']:.6g}")


def estimate_motion_threshold(
    motion_scores: np.ndarray,
    strategy: str = "percentile",
    percentile: float = 15.0,
) -> float:
    """Estimate a low-motion cutoff with an adaptive statistical strategy."""
    scores = np.asarray(motion_scores)
    if scores.ndim != 1 or len(scores) < 1:
        raise ValueError("motion_scores must be a non-empty 1D NumPy array.")
    if not np.isfinite(scores).all():
        raise ValueError("motion_scores contains NaN or infinite values.")
    if not 0 <= percentile <= 100:
        raise ValueError("percentile must be in the range [0, 100].")

    strategy = strategy.lower()
    if strategy == "percentile":
        threshold = float(np.percentile(scores, percentile))
    elif strategy == "mean_minus_std":
        threshold = float(scores.mean() - 0.5 * scores.std())
    elif strategy == "iqr":
        q1 = float(np.percentile(scores, 25))
        q3 = float(np.percentile(scores, 75))
        threshold = q1 - 1.5 * (q3 - q1)
    else:
        raise ValueError("strategy must be one of: 'percentile', 'mean_minus_std', 'iqr'.")

    threshold = max(0.0, threshold)
    minimum_score = float(scores.min())
    if threshold == minimum_score and np.sum(scores == minimum_score) > 1:
        # Flat idle plateaus often produce many identical zero-motion scores.
        # Use the next observed score when possible so exact-idle windows fall
        # below the strict cutoff while the printed threshold remains readable.
        scores_above_minimum = scores[scores > minimum_score]
        if len(scores_above_minimum):
            threshold = float(scores_above_minimum.min())
        else:
            threshold = float(np.nextafter(threshold, np.inf))

    return threshold


def sample_temporally_diverse_indices(
    indices: np.ndarray,
    sample_count: int,
    random_seed: int = 42,
) -> np.ndarray:
    """
    Sample indices across the full recording timeline.

    Low-motion windows are often clustered because a 50% overlap turns one idle
    segment into many near-identical examples. Stratifying by time preserves
    stationary behavior from different parts of the drive without retaining the
    whole redundant block.
    """
    indices = np.asarray(indices, dtype=np.int64)
    if sample_count <= 0 or len(indices) == 0:
        return np.array([], dtype=np.int64)
    if sample_count >= len(indices):
        return np.sort(indices)

    rng = np.random.default_rng(random_seed)
    ordered_indices = np.sort(indices)
    buckets = np.array_split(ordered_indices, sample_count)
    sampled = [int(rng.choice(bucket)) for bucket in buckets if len(bucket)]
    return np.sort(np.asarray(sampled, dtype=np.int64))


def apply_motion_balancing(
    low_motion_indices: np.ndarray,
    high_motion_indices: np.ndarray,
    retain_ratio: float,
    random_seed: int = 42,
    low_motion_target_fraction: float = 0.4,
) -> np.ndarray:
    """
    Select a healthier low/high-motion mix for training.

    Motion balancing improves anomaly detection because reconstruction and
    representation models see enough dynamic normal behavior to calibrate
    thresholds, while still keeping realistic idle examples as normal data.
    """
    if not 0 <= low_motion_target_fraction < 1:
        raise ValueError("low_motion_target_fraction must be in the range [0, 1).")

    low_motion_indices = np.asarray(low_motion_indices, dtype=np.int64)
    high_motion_indices = np.asarray(high_motion_indices, dtype=np.int64)

    ratio_count = int(round(len(low_motion_indices) * retain_ratio))
    if len(high_motion_indices) and low_motion_target_fraction > 0:
        balanced_count = int(round(
            len(high_motion_indices)
            * low_motion_target_fraction
            / (1.0 - low_motion_target_fraction)
        ))
        target_count = min(len(low_motion_indices), max(ratio_count, balanced_count))
    else:
        target_count = ratio_count

    return sample_temporally_diverse_indices(
        low_motion_indices,
        sample_count=target_count,
        random_seed=random_seed,
    )


def filter_low_motion_windows(
    windows: np.ndarray,
    motion_scores: np.ndarray,
    threshold: float,
    retain_ratio: float = 0.2,
    random_seed: int = 42,
    enable_motion_balancing: bool = True,
) -> tuple[np.ndarray, np.ndarray]:
    """
    Keep all high-motion windows and retain a reproducible sample of idle windows.

    Preserving some stationary data is important: phone IMU models still need to
    learn normal idle behavior, but they do not need thousands of nearly
    identical flat windows dominating the training distribution.
    """
    validate_window_array(windows)
    scores = np.asarray(motion_scores)
    if scores.ndim != 1 or len(scores) != len(windows):
        raise ValueError("motion_scores must be 1D with one score per window.")
    if not np.isfinite(scores).all():
        raise ValueError("motion_scores contains NaN or infinite values.")
    if not np.isfinite(threshold):
        raise ValueError("threshold must be finite.")
    if not 0 <= retain_ratio <= 1:
        raise ValueError("retain_ratio must be in the range [0, 1].")

    high_motion_indices = np.flatnonzero(scores >= threshold)
    low_motion_indices = np.flatnonzero(scores < threshold)

    if enable_motion_balancing:
        retained_low_indices = apply_motion_balancing(
            low_motion_indices,
            high_motion_indices,
            retain_ratio=retain_ratio,
            random_seed=random_seed,
        )
    else:
        low_retain_count = int(round(len(low_motion_indices) * retain_ratio))
        retained_low_indices = sample_temporally_diverse_indices(
            low_motion_indices,
            sample_count=low_retain_count,
            random_seed=random_seed,
        )

    retained_indices = np.sort(np.concatenate([high_motion_indices, retained_low_indices]))
    return windows[retained_indices], retained_indices


def get_near_duplicate_retained_indices(
    windows: np.ndarray,
    similarity_threshold: float = 0.995,
) -> np.ndarray:
    """
    Greedily keep windows that are not almost identical to prior kept windows.

    With 50% overlap, consecutive stationary windows can have cosine similarity
    very close to one. Removing those duplicates reduces redundant idle examples
    without discarding distinct driving dynamics.
    """
    validate_window_array(windows)
    if not -1 <= similarity_threshold <= 1:
        raise ValueError("similarity_threshold must be in the range [-1, 1].")

    flattened = windows.reshape(len(windows), -1).astype(np.float64)
    flattened -= flattened.mean(axis=1, keepdims=True)
    norms = np.linalg.norm(flattened, axis=1)
    nonzero = norms > 1e-12

    normalized = np.zeros_like(flattened)
    normalized[nonzero] = flattened[nonzero] / norms[nonzero, None]

    kept_indices: list[int] = []
    kept_vectors: list[np.ndarray] = []
    kept_zero_norm = False
    for index, vector in enumerate(normalized):
        if not nonzero[index]:
            if kept_zero_norm:
                continue
            kept_indices.append(index)
            kept_zero_norm = True
            continue

        if kept_vectors:
            similarities = np.asarray(kept_vectors) @ vector
            if float(similarities.max()) >= similarity_threshold:
                continue

        kept_indices.append(index)
        kept_vectors.append(vector)

    return np.asarray(kept_indices, dtype=np.int64)


def remove_near_duplicate_windows(
    windows: np.ndarray,
    similarity_threshold: float = 0.995,
) -> np.ndarray:
    """Return windows after suppressing near-duplicates."""
    retained_indices = get_near_duplicate_retained_indices(
        windows,
        similarity_threshold=similarity_threshold,
    )
    return windows[retained_indices]


def non_overwriting_path(path: Path) -> Path:
    """Return a path that will not overwrite an existing artifact."""
    if not path.exists():
        return path

    counter = 1
    while True:
        candidate = path.with_name(f"{path.stem}_{counter}{path.suffix}")
        if not candidate.exists():
            return candidate
        counter += 1


def plot_motion_score_distribution(
    motion_scores: np.ndarray,
    threshold: float,
    output_path: Path,
    title: str = "IMU Window Motion Score Distribution",
) -> None:
    """Save a histogram of per-window motion scores with the chosen threshold."""
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    scores = np.asarray(motion_scores)
    if scores.ndim != 1 or len(scores) < 1:
        raise ValueError("motion_scores must be a non-empty 1D NumPy array.")
    if not np.isfinite(scores).all():
        raise ValueError("motion_scores contains NaN or infinite values.")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig, ax = plt.subplots(figsize=(10, 6))
    ax.hist(scores, bins=50, color="#3B82F6", alpha=0.82, edgecolor="white")
    ax.axvline(
        threshold,
        color="#DC2626",
        linestyle="--",
        linewidth=2,
        label=f"Threshold: {threshold:.6g}",
    )
    ax.set_title(title)
    ax.set_xlabel("Motion score")
    ax.set_ylabel("Window count")
    ax.grid(True, alpha=0.25)
    ax.legend(loc="upper right")
    fig.tight_layout()
    fig.savefig(output_path, dpi=160)
    plt.close(fig)


def plot_motion_score_boxplot(
    motion_scores: np.ndarray,
    threshold: float,
    output_path: Path,
) -> None:
    """Save a boxplot to show score spread and outliers."""
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    scores = np.asarray(motion_scores, dtype=np.float64)
    if scores.ndim != 1 or len(scores) < 1:
        raise ValueError("motion_scores must be a non-empty 1D NumPy array.")
    if not np.isfinite(scores).all():
        raise ValueError("motion_scores contains NaN or infinite values.")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig, ax = plt.subplots(figsize=(8, 5))
    ax.boxplot(scores, vert=False, showmeans=True)
    ax.axvline(threshold, color="#DC2626", linestyle="--", linewidth=2, label="Threshold")
    ax.set_title("IMU Motion Score Boxplot")
    ax.set_xlabel("Motion score")
    ax.grid(True, axis="x", alpha=0.25)
    ax.legend(loc="upper right")
    fig.tight_layout()
    fig.savefig(output_path, dpi=160)
    plt.close(fig)


def select_representative_motion_indices(motion_scores: np.ndarray) -> dict[str, int]:
    """Pick stationary, medium-motion, and high-motion examples by score rank."""
    scores = np.asarray(motion_scores, dtype=np.float64)
    if scores.ndim != 1 or len(scores) < 1:
        raise ValueError("motion_scores must be a non-empty 1D NumPy array.")

    sorted_indices = np.argsort(scores)
    return {
        "stationary": int(sorted_indices[0]),
        "medium": int(sorted_indices[len(sorted_indices) // 2]),
        "high": int(sorted_indices[-1]),
    }


def print_motion_filter_report(
    windows: np.ndarray,
    filtered_windows: np.ndarray,
    motion_scores: np.ndarray,
    threshold: float,
    retained_indices: np.ndarray,
) -> None:
    """Print diagnostics for the motion-filtering stage."""
    total_windows = len(windows)
    total_filtered = len(filtered_windows)
    removed_count = total_windows - total_filtered
    removed_percent = 100.0 * removed_count / total_windows

    retained_scores = motion_scores[retained_indices]
    retained_low_count = int(np.sum(retained_scores < threshold))
    retained_high_count = int(np.sum(retained_scores >= threshold))

    print("Motion filtering diagnostics:")
    print(f"  Total windows before filtering: {total_windows}")
    print(f"  Total windows after filtering: {total_filtered}")
    print(f"  Percentage removed: {removed_percent:.2f}%")
    print(f"  Motion score min: {motion_scores.min():.6g}")
    print(f"  Motion score max: {motion_scores.max():.6g}")
    print(f"  Motion score mean: {motion_scores.mean():.6g}")
    print(f"  Chosen threshold: {threshold:.6g}")
    print(f"  Retained low-motion count: {retained_low_count}")
    print(f"  Retained high-motion count: {retained_high_count}")


def plot_sample_window(
    windows: np.ndarray,
    feature_columns: Iterable[str] = FEATURE_COLUMNS,
    window_index: int = 0,
    output_path: Path | None = DEFAULT_PLOT,
) -> None:
    """Visualize accelerometer and gyroscope channels for one window."""
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    if windows.ndim != 3:
        raise ValueError("windows must have shape [num_windows, timesteps, features].")
    if not 0 <= window_index < len(windows):
        raise IndexError(f"window_index must be between 0 and {len(windows) - 1}.")

    feature_columns = tuple(feature_columns)
    sample = windows[window_index]
    time_index = np.arange(sample.shape[0])

    fig, axes = plt.subplots(2, 1, figsize=(12, 7), sharex=True)
    for axis_name in ("ax", "ay", "az"):
        axes[0].plot(time_index, sample[:, feature_columns.index(axis_name)], label=axis_name)
    axes[0].set_title(f"Accelerometer signals - window {window_index}")
    axes[0].set_ylabel("Acceleration")
    axes[0].legend(loc="upper right")
    axes[0].grid(True, alpha=0.25)

    for axis_name in ("gx", "gy", "gz"):
        axes[1].plot(time_index, sample[:, feature_columns.index(axis_name)], label=axis_name)
    axes[1].set_title(f"Gyroscope signals - window {window_index}")
    axes[1].set_xlabel("Timestep")
    axes[1].set_ylabel("Angular velocity")
    axes[1].legend(loc="upper right")
    axes[1].grid(True, alpha=0.25)

    fig.tight_layout()
    if output_path:
        fig.savefig(output_path, dpi=160)
    else:
        plt.show()
    plt.close(fig)


def build_imu_windows(
    input_csv: Path = DEFAULT_INPUT_CSV,
    output_npy: Path = DEFAULT_OUTPUT_NPY,
    plot_path: Path | None = DEFAULT_PLOT,
    config: WindowConfig = WindowConfig(),
) -> tuple[np.ndarray, bool]:
    """End-to-end helper: load CSV, create windows, save `.npy`, and plot one window."""
    imu_df = load_imu_data(input_csv, config.feature_columns)
    windows = create_sliding_windows(imu_df, config)
    np.save(output_npy, windows)
    plot_saved = False
    if plot_path:
        try:
            plot_sample_window(windows, config.feature_columns, window_index=0, output_path=plot_path)
            plot_saved = True
        except ModuleNotFoundError as exc:
            if exc.name != "matplotlib":
                raise
            print("Skipped sample-window plot because matplotlib is not installed.")
    return windows, plot_saved


def build_imu_window_artifacts(
    input_csv: Path = DEFAULT_INPUT_CSV,
    output_npy: Path = DEFAULT_ORIGINAL_OUTPUT_NPY,
    filtered_output_npy: Path = DEFAULT_FILTERED_OUTPUT_NPY,
    motion_scores_npy: Path = DEFAULT_MOTION_SCORES_NPY,
    retained_indices_npy: Path = DEFAULT_RETAINED_INDICES_NPY,
    plot_path: Path | None = DEFAULT_OUTPUT_DIR / "sample_imu_window.png",
    motion_plot_path: Path | None = DEFAULT_MOTION_PLOT,
    motion_boxplot_path: Path | None = DEFAULT_MOTION_BOXPLOT,
    filtered_motion_plot_path: Path | None = DEFAULT_FILTERED_MOTION_PLOT,
    config: WindowConfig = WindowConfig(),
) -> dict[str, object]:
    """
    Build original windows plus motion-filtered windows and diagnostics.

    The original window file is still saved unchanged. The filtered dataset is
    an additional artifact for model-training experiments that should benefit
    from less stationary redundancy.
    """
    output_npy = non_overwriting_path(output_npy)
    filtered_output_npy = non_overwriting_path(filtered_output_npy)
    motion_scores_npy = non_overwriting_path(motion_scores_npy)
    retained_indices_npy = non_overwriting_path(retained_indices_npy)
    plot_path = non_overwriting_path(plot_path) if plot_path else None
    motion_plot_path = non_overwriting_path(motion_plot_path) if motion_plot_path else None
    motion_boxplot_path = non_overwriting_path(motion_boxplot_path) if motion_boxplot_path else None
    filtered_motion_plot_path = (
        non_overwriting_path(filtered_motion_plot_path) if filtered_motion_plot_path else None
    )

    imu_df = load_imu_data(input_csv, config.feature_columns)
    windows = create_sliding_windows(imu_df, config)
    output_npy.parent.mkdir(parents=True, exist_ok=True)
    np.save(output_npy, windows)

    motion_scores = compute_window_motion_scores(windows, method=config.motion_metric)
    motion_scores_npy.parent.mkdir(parents=True, exist_ok=True)
    np.save(motion_scores_npy, motion_scores)
    print_motion_score_statistics(motion_scores, title="Motion score statistics before filtering")

    threshold = estimate_motion_threshold(
        motion_scores,
        strategy=config.motion_threshold_strategy,
        percentile=config.motion_percentile_threshold,
    )
    filtered_windows, retained_indices = filter_low_motion_windows(
        windows,
        motion_scores,
        threshold=threshold,
        retain_ratio=config.low_motion_retain_ratio,
        enable_motion_balancing=config.enable_motion_balancing,
    )

    duplicates_removed = 0
    if config.remove_duplicates and len(filtered_windows):
        retained_scores_before_duplicates = motion_scores[retained_indices]
        low_retained_indices = retained_indices[retained_scores_before_duplicates < threshold]
        high_retained_indices = retained_indices[retained_scores_before_duplicates >= threshold]

        deduped_low_indices = np.array([], dtype=np.int64)
        if len(low_retained_indices):
            low_local_indices = get_near_duplicate_retained_indices(
                windows[low_retained_indices],
                similarity_threshold=config.duplicate_similarity_threshold,
            )
            deduped_low_indices = low_retained_indices[low_local_indices]

        deduped_high_indices = np.array([], dtype=np.int64)
        if len(high_retained_indices):
            high_local_indices = get_near_duplicate_retained_indices(
                windows[high_retained_indices],
                similarity_threshold=config.duplicate_similarity_threshold,
            )
            deduped_high_indices = high_retained_indices[high_local_indices]

        if config.enable_motion_balancing and len(low_retained_indices) and len(deduped_high_indices):
            target_low_count = int(round(len(deduped_high_indices) * 0.4 / 0.6))
            target_low_count = min(len(low_retained_indices), max(1, target_low_count))
            if len(deduped_low_indices) < target_low_count:
                remaining_low_indices = np.setdiff1d(
                    low_retained_indices,
                    deduped_low_indices,
                    assume_unique=False,
                )
                refill_indices = sample_temporally_diverse_indices(
                    remaining_low_indices,
                    sample_count=target_low_count - len(deduped_low_indices),
                    random_seed=43,
                )
                deduped_low_indices = np.sort(
                    np.concatenate([deduped_low_indices, refill_indices])
                )

        retained_indices = np.sort(np.concatenate([deduped_low_indices, deduped_high_indices]))
        duplicates_removed = len(filtered_windows) - len(retained_indices)
        filtered_windows = windows[retained_indices]

    filtered_output_npy.parent.mkdir(parents=True, exist_ok=True)
    np.save(filtered_output_npy, filtered_windows)
    retained_indices_npy.parent.mkdir(parents=True, exist_ok=True)
    np.save(retained_indices_npy, retained_indices)
    filtered_motion_scores = motion_scores[retained_indices]
    print_motion_score_statistics(
        filtered_motion_scores,
        title="Motion score statistics after filtering",
    )

    sample_plot_saved = False
    if plot_path:
        try:
            plot_sample_window(windows, config.feature_columns, window_index=0, output_path=plot_path)
            sample_plot_saved = True
        except ModuleNotFoundError as exc:
            if exc.name != "matplotlib":
                raise
            print("Skipped sample-window plot because matplotlib is not installed.")

    motion_plot_saved = False
    if motion_plot_path:
        try:
            plot_motion_score_distribution(
                motion_scores,
                threshold,
                motion_plot_path,
                title="Motion Scores Before Filtering",
            )
            motion_plot_saved = True
        except ModuleNotFoundError as exc:
            if exc.name != "matplotlib":
                raise
            print("Skipped motion-score plot because matplotlib is not installed.")

    motion_boxplot_saved = False
    if motion_boxplot_path:
        try:
            plot_motion_score_boxplot(motion_scores, threshold, motion_boxplot_path)
            motion_boxplot_saved = True
        except ModuleNotFoundError as exc:
            if exc.name != "matplotlib":
                raise
            print("Skipped motion-score boxplot because matplotlib is not installed.")

    filtered_motion_plot_saved = False
    if filtered_motion_plot_path:
        try:
            plot_motion_score_distribution(
                filtered_motion_scores,
                threshold,
                filtered_motion_plot_path,
                title="Motion Scores After Filtering",
            )
            filtered_motion_plot_saved = True
        except ModuleNotFoundError as exc:
            if exc.name != "matplotlib":
                raise
            print("Skipped filtered motion-score plot because matplotlib is not installed.")

    representative_plot_paths: dict[str, Path] = {}
    representative_plot_saved: dict[str, bool] = {}
    if len(filtered_windows):
        representative_indices = select_representative_motion_indices(filtered_motion_scores)
        representative_targets = {
            "stationary": DEFAULT_STATIONARY_SAMPLE_PLOT,
            "medium": DEFAULT_MEDIUM_SAMPLE_PLOT,
            "high": DEFAULT_HIGH_SAMPLE_PLOT,
        }
        for label, target_path in representative_targets.items():
            output_path = non_overwriting_path(target_path)
            representative_plot_paths[label] = output_path
            try:
                plot_sample_window(
                    filtered_windows,
                    config.feature_columns,
                    window_index=representative_indices[label],
                    output_path=output_path,
                )
                representative_plot_saved[label] = True
            except ModuleNotFoundError as exc:
                if exc.name != "matplotlib":
                    raise
                print(f"Skipped {label} sample plot because matplotlib is not installed.")
                representative_plot_saved[label] = False

    print_motion_filter_report(
        windows=windows,
        filtered_windows=filtered_windows,
        motion_scores=motion_scores,
        threshold=threshold,
        retained_indices=retained_indices,
    )

    return {
        "windows": windows,
        "filtered_windows": filtered_windows,
        "motion_scores": motion_scores,
        "filtered_motion_scores": filtered_motion_scores,
        "motion_threshold": threshold,
        "retained_indices": retained_indices,
        "duplicates_removed": duplicates_removed,
        "sample_plot_saved": sample_plot_saved,
        "motion_plot_saved": motion_plot_saved,
        "motion_boxplot_saved": motion_boxplot_saved,
        "filtered_motion_plot_saved": filtered_motion_plot_saved,
        "representative_plot_saved": representative_plot_saved,
        "output_npy": output_npy,
        "filtered_output_npy": filtered_output_npy,
        "motion_scores_npy": motion_scores_npy,
        "retained_indices_npy": retained_indices_npy,
        "plot_path": plot_path,
        "motion_plot_path": motion_plot_path,
        "motion_boxplot_path": motion_boxplot_path,
        "filtered_motion_plot_path": filtered_motion_plot_path,
        "representative_plot_paths": representative_plot_paths,
    }


def main() -> None:
    config = WindowConfig(window_size=200, overlap=0.50)
    artifacts = build_imu_window_artifacts(config=config)

    windows = artifacts["windows"]
    filtered_windows = artifacts["filtered_windows"]
    retained_indices = artifacts["retained_indices"]
    threshold = float(artifacts["motion_threshold"])
    motion_scores = artifacts["motion_scores"]
    filtered_motion_scores = artifacts["filtered_motion_scores"]
    original_low_motion = int(np.sum(motion_scores < threshold))
    retained_low_motion = int(np.sum(filtered_motion_scores < threshold))
    removed_low_motion = original_low_motion - retained_low_motion
    retained_high_motion = int(np.sum(filtered_motion_scores >= threshold))
    total_low_motion = int(np.sum(motion_scores < threshold))
    low_motion_removed_percent = (
        100.0 * removed_low_motion / total_low_motion if total_low_motion else 0.0
    )
    total_removed_percent = 100.0 * (len(windows) - len(filtered_windows)) / len(windows)
    final_summary = summarize_motion_scores(filtered_motion_scores)

    print("Final output summary:")
    print(f"  Original dataset shape: {windows.shape}")
    print(f"  Filtered dataset shape: {filtered_windows.shape}")
    print(f"  Percentage removed: {total_removed_percent:.2f}%")
    print(f"  Percentage of low-motion windows removed: {low_motion_removed_percent:.2f}%")
    print(f"  Number of low-motion windows removed: {removed_low_motion}")
    print(f"  Number of high-motion windows retained: {retained_high_motion}")
    print(f"  Duplicate windows removed: {artifacts['duplicates_removed']}")
    print("  Final motion distribution summary:")
    print(f"    min={final_summary['min']:.6g}")
    print(f"    max={final_summary['max']:.6g}")
    print(f"    mean={final_summary['mean']:.6g}")
    print(f"    median={final_summary['median']:.6g}")
    print(f"    std={final_summary['std']:.6g}")
    print(f"  Saved original windows: {artifacts['output_npy']}")
    print(f"  Saved filtered windows: {artifacts['filtered_output_npy']}")
    print(f"  Saved motion scores: {artifacts['motion_scores_npy']}")
    print(f"  Saved retained indices: {artifacts['retained_indices_npy']}")
    if artifacts["sample_plot_saved"]:
        print(f"  Saved sample-window plot: {artifacts['plot_path']}")
    if artifacts["motion_plot_saved"]:
        print(f"  Saved motion-score histogram: {artifacts['motion_plot_path']}")
    if artifacts["motion_boxplot_saved"]:
        print(f"  Saved motion-score boxplot: {artifacts['motion_boxplot_path']}")
    if artifacts["filtered_motion_plot_saved"]:
        print(f"  Saved filtered motion-score histogram: {artifacts['filtered_motion_plot_path']}")
    for label, path in artifacts["representative_plot_paths"].items():
        if artifacts["representative_plot_saved"].get(label):
            print(f"  Saved {label} sample plot: {path}")


if __name__ == "__main__":
    main()
