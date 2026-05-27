from __future__ import annotations

from dataclasses import asdict, dataclass
import logging
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from .config import FEATURE_COLUMNS, SyntheticCrashConfig
from .utils import ensure_dir, validate_windows

LOGGER = logging.getLogger(__name__)

CRASH_TYPES = ("frontal_collision", "side_collision", "rollover_like", "abrupt_stop_impact")

SEVERITY_PARAMS = {
    "minor": {"accel": (1.8, 3.2), "gyro": (1.5, 3.0), "damping": (0.25, 0.45), "osc": (0.10, 0.22)},
    "moderate": {"accel": (3.0, 5.5), "gyro": (2.8, 5.5), "damping": (0.45, 0.68), "osc": (0.18, 0.38)},
    "severe": {"accel": (5.0, 8.5), "gyro": (5.0, 9.5), "damping": (0.65, 0.86), "osc": (0.30, 0.58)},
}


@dataclass(frozen=True)
class CrashMetadata:
    source_index: int
    crash_type: str
    severity: str
    severity_score: float
    impact_start: int
    impact_end: int
    peak_acceleration_delta: float
    peak_gyro_delta: float
    post_impact_end: int


def _unit(direction: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(direction)
    if norm < 1e-8:
        return np.array([1.0, 0.0, 0.0], dtype=np.float32)
    return (direction / norm).astype(np.float32)


def _impact_axes(crash_type: str, rng: np.random.Generator) -> tuple[np.ndarray, np.ndarray]:
    jitter = rng.normal(0.0, 0.12, size=3)
    if crash_type == "frontal_collision":
        return _unit(np.array([-1.0, 0.05, 0.0]) + jitter), _unit(np.array([0.1, 0.6, 0.25]) + jitter)
    if crash_type == "side_collision":
        side = rng.choice([-1.0, 1.0])
        return _unit(np.array([0.05, side, 0.0]) + jitter), _unit(np.array([0.5, 0.1, side]) + jitter)
    if crash_type == "rollover_like":
        roll = rng.choice([-1.0, 1.0])
        return _unit(np.array([0.2, 0.2, roll]) + jitter), _unit(np.array([roll, 0.35, 0.15]) + jitter)
    if crash_type == "abrupt_stop_impact":
        return _unit(np.array([-1.0, 0.0, 0.05]) + jitter), _unit(np.array([0.0, 0.35, 0.15]) + jitter)
    raise ValueError(f"Unsupported crash type: {crash_type}")


def _raised_cosine(length: int) -> np.ndarray:
    if length <= 2:
        return np.ones(length, dtype=np.float32)
    return np.hanning(length).astype(np.float32)


def _smooth_delta(delta: np.ndarray, limit: float) -> np.ndarray:
    smoothed = delta.copy()
    for i in range(1, len(smoothed)):
        step = smoothed[i] - smoothed[i - 1]
        norm = float(np.linalg.norm(step))
        if norm > limit:
            smoothed[i] = smoothed[i - 1] + step * (limit / (norm + 1e-8))
    return smoothed


def _choose_impact(timesteps: int, sample_rate_hz: float, cfg: SyntheticCrashConfig, rng: np.random.Generator) -> slice:
    min_len = max(4, int(round(cfg.min_impact_duration_s * sample_rate_hz)))
    max_len = max(min_len, int(round(cfg.max_impact_duration_s * sample_rate_hz)))
    impact_len = int(rng.integers(min_len, min(max_len, timesteps // 3) + 1))
    earliest = max(1, timesteps // 5)
    latest = max(earliest + 1, timesteps - impact_len - 2)
    start = int(rng.integers(earliest, latest))
    return slice(start, start + impact_len)


def _post_impact_oscillation(length: int, frequency_hz: float, sample_rate_hz: float, decay: float) -> np.ndarray:
    t = np.arange(length, dtype=np.float32) / sample_rate_hz
    return (np.sin(2.0 * np.pi * frequency_hz * t) * np.exp(-decay * t)).astype(np.float32)


def inject_crash(
    normal_window: np.ndarray,
    crash_type: str,
    severity: str,
    cfg: SyntheticCrashConfig,
    sample_rate_hz: float,
    rng: np.random.Generator,
    source_index: int,
) -> tuple[np.ndarray, CrashMetadata]:
    window = normal_window.astype(np.float32, copy=True)
    timesteps = window.shape[0]
    impact = _choose_impact(timesteps, sample_rate_hz, cfg, rng)
    params = SEVERITY_PARAMS[severity]
    severity_score = {"minor": 0.35, "moderate": 0.65, "severe": 0.95}[severity]
    accel_axis, gyro_axis = _impact_axes(crash_type, rng)

    accel_g = float(rng.uniform(*params["accel"]))
    gyro_mag = float(rng.uniform(*params["gyro"]))
    damping = float(rng.uniform(*params["damping"]))
    oscillation_scale = float(rng.uniform(*params["osc"]))
    impact_len = impact.stop - impact.start
    impulse = _raised_cosine(impact_len)[:, None]

    accel_delta = np.zeros_like(window[:, :3])
    gyro_delta = np.zeros_like(window[:, 3:])
    accel_delta[impact] += impulse * accel_axis[None, :] * accel_g
    gyro_delta[impact] += impulse * gyro_axis[None, :] * gyro_mag

    post_len = min(timesteps - impact.stop, int(round(cfg.post_impact_duration_s * sample_rate_hz)))
    post_end = impact.stop + post_len
    if post_len > 0:
        osc = _post_impact_oscillation(
            post_len,
            frequency_hz=float(rng.uniform(2.2, 5.0)),
            sample_rate_hz=sample_rate_hz,
            decay=float(rng.uniform(1.6, 3.1)),
        )[:, None]
        accel_delta[impact.stop:post_end] += osc * accel_axis[None, :] * accel_g * oscillation_scale
        gyro_delta[impact.stop:post_end] += osc * gyro_axis[None, :] * gyro_mag * oscillation_scale

        baseline_acc = np.median(window[:, :3], axis=0, keepdims=True)
        baseline_gyro = np.median(window[:, 3:], axis=0, keepdims=True)
        damping_curve = np.linspace(1.0 - damping, 1.0 - 0.35 * damping, timesteps - impact.stop)[:, None]
        window[impact.stop:, :3] = baseline_acc + (window[impact.stop:, :3] - baseline_acc) * damping_curve
        window[impact.stop:, 3:] = baseline_gyro + (window[impact.stop:, 3:] - baseline_gyro) * damping_curve

    accel_delta = _smooth_delta(accel_delta, cfg.max_delta_per_step)
    gyro_delta = _smooth_delta(gyro_delta, cfg.max_delta_per_step)
    window[:, :3] += accel_delta
    window[:, 3:] += gyro_delta

    feature_std = np.maximum(np.std(normal_window, axis=0, keepdims=True), 1e-5)
    window += (rng.normal(0.0, cfg.sensor_noise_scale, size=window.shape) * feature_std).astype(np.float32)

    meta = CrashMetadata(
        source_index=source_index,
        crash_type=crash_type,
        severity=severity,
        severity_score=severity_score,
        impact_start=impact.start,
        impact_end=impact.stop,
        peak_acceleration_delta=float(np.max(np.linalg.norm(accel_delta, axis=1))),
        peak_gyro_delta=float(np.max(np.linalg.norm(gyro_delta, axis=1))),
        post_impact_end=post_end,
    )
    return window, meta


def generate_synthetic_crashes(
    windows: np.ndarray,
    cfg: SyntheticCrashConfig,
    sample_rate_hz: float,
    seed: int,
) -> tuple[np.ndarray, np.ndarray, pd.DataFrame]:
    windows = validate_windows(windows)
    rng = np.random.default_rng(seed)
    count = max(1, int(round(len(windows) * cfg.crash_fraction)))
    source_indices = rng.choice(len(windows), size=count, replace=len(windows) < count)

    synthetic = np.empty((count, windows.shape[1], windows.shape[2]), dtype=np.float32)
    labels = np.ones(count, dtype=np.int64)
    metadata: list[dict[str, object]] = []

    for out_idx, source_idx in enumerate(source_indices):
        crash_type = str(rng.choice(CRASH_TYPES))
        severity = str(rng.choice(cfg.severity_levels))
        synthetic[out_idx], meta = inject_crash(
            windows[int(source_idx)],
            crash_type=crash_type,
            severity=severity,
            cfg=cfg,
            sample_rate_hz=sample_rate_hz,
            rng=rng,
            source_index=int(source_idx),
        )
        row = asdict(meta)
        row["label"] = 1
        row["synthetic_index"] = out_idx
        metadata.append(row)

    return synthetic, labels, pd.DataFrame(metadata)


def save_synthetic_outputs(
    synthetic_windows: np.ndarray,
    labels: np.ndarray,
    metadata: pd.DataFrame,
    output_dir: Path,
) -> None:
    output_dir = ensure_dir(output_dir)
    np.save(output_dir / "synthetic_crash_windows.npy", synthetic_windows)
    np.save(output_dir / "synthetic_crash_labels.npy", labels)
    metadata.to_csv(output_dir / "synthetic_crash_metadata.csv", index=False)


def plot_synthetic_examples(original: np.ndarray, synthetic: np.ndarray, metadata: pd.DataFrame, output_dir: Path, max_examples: int = 4) -> None:
    plot_dir = ensure_dir(output_dir / "plots")
    for _, row in metadata.head(max_examples).iterrows():
        syn_idx = int(row["synthetic_index"])
        src_idx = int(row["source_index"])
        fig, axes = plt.subplots(2, 1, figsize=(12, 6), sharex=True)
        t = np.arange(synthetic.shape[1])
        axes[0].plot(t, np.linalg.norm(original[src_idx, :, :3], axis=1), label="normal", linewidth=1.2)
        axes[0].plot(t, np.linalg.norm(synthetic[syn_idx, :, :3], axis=1), label="synthetic crash", linewidth=1.2)
        axes[0].axvspan(int(row["impact_start"]), int(row["impact_end"]), color="tab:red", alpha=0.15)
        axes[0].set_ylabel("accel magnitude")
        axes[0].legend()
        axes[0].grid(alpha=0.25)
        axes[1].plot(t, np.linalg.norm(original[src_idx, :, 3:], axis=1), label="normal", linewidth=1.2)
        axes[1].plot(t, np.linalg.norm(synthetic[syn_idx, :, 3:], axis=1), label="synthetic crash", linewidth=1.2)
        axes[1].axvspan(int(row["impact_start"]), int(row["impact_end"]), color="tab:red", alpha=0.15)
        axes[1].set_xlabel("timestep")
        axes[1].set_ylabel("gyro magnitude")
        axes[1].grid(alpha=0.25)
        fig.suptitle(f"{row['crash_type']} | {row['severity']}")
        fig.tight_layout()
        fig.savefig(plot_dir / f"synthetic_example_{syn_idx:03d}.png", dpi=160)
        plt.close(fig)
