from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from imu_crash_pipeline.anomaly import compute_thresholds, reconstruct_windows, reconstruction_statistics
from imu_crash_pipeline.classifier import load_classifier_bundle, predict_crash_confirmation
from imu_crash_pipeline.config import load_config
from imu_crash_pipeline.events import save_confirmed_crash_events
from imu_crash_pipeline.features import extract_window_features
from imu_crash_pipeline.model import load_autoencoder
from imu_crash_pipeline.utils import ensure_dir, get_device, load_windows, setup_logging, set_seed


def main() -> None:
    parser = argparse.ArgumentParser(description="Confirm only LSTM-flagged anomaly candidates and save crash events.")
    parser.add_argument("--config", default="configs/pipeline_config.json")
    parser.add_argument("--windows", default=None, help="Optional .npy windows to score. Defaults to configured normal_windows.")
    parser.add_argument("--classifier", default=None, help="Optional trained .joblib classifier bundle.")
    args = parser.parse_args()

    setup_logging()
    cfg = load_config(args.config)
    set_seed(cfg.seed)

    windows_path = Path(args.windows) if args.windows else cfg.paths.normal_windows
    windows = load_windows(windows_path, cfg.model.input_size)
    device = get_device()
    autoencoder = load_autoencoder(cfg.paths.checkpoint, cfg.model, device)
    reconstructions, _ = reconstruct_windows(autoencoder, windows, cfg.model.batch_size, device)
    stats = reconstruction_statistics(windows, reconstructions)

    thresholds_path = cfg.paths.output_dir / "stage3_anomaly_evaluation" / "thresholds.json"
    if thresholds_path.exists():
        with thresholds_path.open("r", encoding="utf-8") as handle:
            thresholds = json.load(handle)
    else:
        thresholds = compute_thresholds(stats["anomaly_score"], cfg.thresholds)
    anomaly_threshold = float(thresholds["adaptive_global"])
    candidate_mask = stats["anomaly_score"] >= anomaly_threshold

    output_dir = ensure_dir(cfg.paths.output_dir / "candidate_confirmation")
    if not candidate_mask.any():
        logging.info("No anomaly candidates above threshold %.6f. Nothing saved.", anomaly_threshold)
        return

    candidate_windows = windows[candidate_mask]
    candidate_indices = candidate_mask.nonzero()[0]
    features = extract_window_features(
        candidate_windows,
        sample_rate_hz=cfg.sample_rate_hz,
        reconstruction_errors=stats["anomaly_score"][candidate_mask],
        per_feature_errors=stats["per_feature_error"][candidate_mask],
    )
    features["window_index"] = candidate_indices

    classifier_path = Path(args.classifier) if args.classifier else cfg.paths.output_dir / "stage4_confirmation_classifier" / "random_forest.joblib"
    confirmations = predict_crash_confirmation(features, load_classifier_bundle(classifier_path))
    confirmations.to_csv(output_dir / "candidate_confirmations.csv", index=False)

    saved_events = save_confirmed_crash_events(
        windows=windows,
        confirmations=confirmations,
        event_dir=cfg.paths.event_log_dir,
        model_version=cfg.paths.model_version,
        source=str(windows_path),
    )
    logging.info("Confirmed %s crashes from %s anomaly candidates.", int(confirmations["confirmed_crash"].sum()), len(confirmations))
    logging.info("Event log: %s", cfg.paths.event_log_dir / "confirmed_crash_event_log.csv")
    if saved_events.empty:
        logging.info("No confirmed crash events were saved.")


if __name__ == "__main__":
    main()
