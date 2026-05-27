from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from imu_crash_pipeline.anomaly import score_groups
from imu_crash_pipeline.classifier import build_labeled_feature_table, train_confirmation_classifiers
from imu_crash_pipeline.config import load_config
from imu_crash_pipeline.features import extract_window_features
from imu_crash_pipeline.model import load_autoencoder
from imu_crash_pipeline.utils import ensure_dir, get_device, load_windows, setup_logging, set_seed
from scripts.run_stage3_anomaly_evaluation import _load_optional_groups


def main() -> None:
    parser = argparse.ArgumentParser(description="Train second-stage crash confirmation classifiers.")
    parser.add_argument("--config", default="configs/pipeline_config.json")
    args = parser.parse_args()

    setup_logging()
    cfg = load_config(args.config)
    set_seed(cfg.seed)
    output_dir = ensure_dir(cfg.paths.output_dir / "stage4_confirmation_classifier")

    normal = load_windows(cfg.paths.normal_windows, cfg.model.input_size)
    groups = _load_optional_groups(cfg, normal)
    groups["synthetic_crash"] = load_windows(
        cfg.paths.output_dir / "stage2_synthetic_crashes" / "synthetic_crash_windows.npy",
        cfg.model.input_size,
    )

    device = get_device()
    model = load_autoencoder(cfg.paths.checkpoint, cfg.model, device)
    reconstruction = score_groups(model, groups, cfg.model.batch_size, device)

    group_features = {}
    for group, values in reconstruction.items():
        group_features[group] = extract_window_features(
            values["windows"],
            sample_rate_hz=cfg.sample_rate_hz,
            reconstruction_errors=values["anomaly_score"],
            per_feature_errors=values["per_feature_error"],
        )
    feature_table = build_labeled_feature_table(group_features)
    feature_table.to_csv(output_dir / "engineered_features.csv", index=False)

    report, predictions = train_confirmation_classifiers(feature_table, cfg.classifier, cfg.seed, output_dir)
    logging.info("Classifier report:\n%s", report.to_string(index=False))
    if predictions.empty:
        logging.warning("No classifier predictions were produced.")


if __name__ == "__main__":
    main()
