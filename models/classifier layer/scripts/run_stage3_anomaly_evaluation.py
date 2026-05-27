from __future__ import annotations

import argparse
import logging
from pathlib import Path
import sys

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from imu_crash_pipeline.anomaly import (
    build_score_table,
    compute_thresholds,
    evaluate_anomaly_separation,
    plot_error_distributions,
    plot_input_vs_reconstruction,
    plot_latent_pca,
    save_anomaly_outputs,
    score_groups,
)
from imu_crash_pipeline.config import load_config
from imu_crash_pipeline.model import load_autoencoder
from imu_crash_pipeline.utils import ensure_dir, get_device, load_windows, setup_logging, set_seed


def _risk_like_windows(windows: np.ndarray, severity: float, seed: int) -> np.ndarray:
    rng = np.random.default_rng(seed)
    risky = windows.astype(np.float32, copy=True)
    accel = risky[:, :, :3]
    gyro = risky[:, :, 3:]
    n, t, _ = risky.shape
    pulse_len = max(4, t // 12)
    for i in range(n):
        start = int(rng.integers(max(1, t // 6), max(2, t - pulse_len - 1)))
        envelope = np.hanning(pulse_len)[:, None].astype(np.float32)
        axis = rng.normal(size=3)
        axis = axis / (np.linalg.norm(axis) + 1e-8)
        accel[i, start:start + pulse_len] += envelope * axis[None, :] * severity
        gyro[i, start:start + pulse_len] += envelope * rng.normal(size=(1, 3)) * severity * 0.65
    return risky


def _load_optional_groups(cfg, normal: np.ndarray) -> dict[str, np.ndarray]:
    groups = {"normal": normal}
    if cfg.paths.aggressive_windows and Path(cfg.paths.aggressive_windows).exists():
        groups["aggressive"] = load_windows(cfg.paths.aggressive_windows, cfg.model.input_size)
    else:
        groups["aggressive"] = _risk_like_windows(normal, severity=0.75, seed=cfg.seed + 10)
    if cfg.paths.risky_windows and Path(cfg.paths.risky_windows).exists():
        groups["risky"] = load_windows(cfg.paths.risky_windows, cfg.model.input_size)
    else:
        groups["risky"] = _risk_like_windows(normal, severity=1.25, seed=cfg.seed + 20)
    return groups


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate LSTM autoencoder anomaly separation.")
    parser.add_argument("--config", default="configs/pipeline_config.json")
    args = parser.parse_args()

    setup_logging()
    cfg = load_config(args.config)
    set_seed(cfg.seed)
    output_dir = ensure_dir(cfg.paths.output_dir / "stage3_anomaly_evaluation")

    normal = load_windows(cfg.paths.normal_windows, cfg.model.input_size)
    synthetic_path = cfg.paths.output_dir / "stage2_synthetic_crashes" / "synthetic_crash_windows.npy"
    if not synthetic_path.exists():
        raise FileNotFoundError(f"Run stage 2 first, missing: {synthetic_path}")
    groups = _load_optional_groups(cfg, normal)
    groups["synthetic_crash"] = load_windows(synthetic_path, cfg.model.input_size)

    device = get_device()
    model = load_autoencoder(cfg.paths.checkpoint, cfg.model, device)
    results = score_groups(model, groups, cfg.model.batch_size, device)
    thresholds = compute_thresholds(results["normal"]["anomaly_score"], cfg.thresholds)
    threshold = thresholds["adaptive_global"]
    score_table = build_score_table(results, threshold)
    metrics = evaluate_anomaly_separation(score_table, threshold)

    save_anomaly_outputs(results, score_table, thresholds, metrics, output_dir)
    plot_error_distributions(score_table, output_dir)
    plot_input_vs_reconstruction(results, output_dir)
    plot_latent_pca(results, output_dir)
    logging.info("Saved anomaly evaluation to %s", output_dir)


if __name__ == "__main__":
    main()
