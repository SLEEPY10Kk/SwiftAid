

import argparse
import logging
import os
import sys

import numpy as np
import torch

# Project root on path
sys.path.insert(0, os.path.dirname(__file__))

from config.config import (
    RANDOM_SEED, WINDOW_SIZE, N_FEATURES, AUTOENCODER, DEVICE,
    MODELS_DIR, PLOTS_DIR, REPORTS_DIR,
)
from utils.utils import set_seed, get_logger
from augmentation.crash_generator import (
    SyntheticCrashGenerator, plot_crash_comparison, plot_severity_comparison,
    _CRASH_FN,
)
from models.lstm_imu_autoencoder import LSTMAutoencoder, load
from evaluation.anomaly_evaluator import AnomalyEvaluator
from classifiers.feature_engineering import build_labeled_dataset
from classifiers.crash_classifier import CrashConfirmationPipeline

log = get_logger("pipeline", level=logging.INFO)

def make_synthetic_normal(n: int = 300,
                          T: int = WINDOW_SIZE,
                          F: int = N_FEATURES) -> np.ndarray:
    
    rng = np.random.default_rng(RANDOM_SEED)
    base = rng.normal(0, 0.15, size=(n, T, F)).astype(np.float32)

    # Add gravity component (phone lying flat: acc_z ≈ 1 g)
    base[:, :, 2] += 1.0

    # Add slow road vibration (< 5 Hz) to acc channels
    t = np.linspace(0, WINDOW_SIZE / 100, T)
    for freq in [1.5, 3.0, 4.5]:
        amp = rng.uniform(0.05, 0.15, n)
        base[:, :, 1] += (amp[:, None] * np.sin(2 * np.pi * freq * t)).astype(np.float32)

    return base


def make_synthetic_aggressive(n: int = 150,
                               T: int = WINDOW_SIZE,
                               F: int = N_FEATURES) -> np.ndarray:
    """
    Aggressive driving: hard acceleration/braking but no crash-level spikes.
    Peaks ~2–5 g, some gyro activity.
    """
    rng  = np.random.default_rng(RANDOM_SEED + 1)
    base = make_synthetic_normal(n, T, F)

    # Occasional hard braking events
    for i in range(n):
        start = rng.integers(20, 70)
        dur   = rng.integers(10, 25)
        peak  = rng.uniform(2.0, 5.0)
        end   = min(start + dur, T)
        base[i, start:end, 1] -= peak * np.sin(np.linspace(0, np.pi, end - start))
        base[i, start:end, 5] += rng.uniform(0.5, 1.5)

    return base.astype(np.float32)


def make_synthetic_risky(n: int = 150,
                          T: int = WINDOW_SIZE,
                          F: int = N_FEATURES) -> np.ndarray:
    """
    Risky driving: sharp lane changes, near-crash maneuvering.
    High gyro + moderate acc spikes, but recovers within window.
    """
    rng  = np.random.default_rng(RANDOM_SEED + 2)
    base = make_synthetic_normal(n, T, F)

    for i in range(n):
        start = rng.integers(15, 60)
        dur   = rng.integers(8, 20)
        end   = min(start + dur, T)
        # Lateral swerve
        base[i, start:end, 0] += rng.uniform(3.0, 7.0) * np.sin(np.linspace(0, np.pi, end - start))
        # Yaw rotation
        base[i, start:end, 5] += rng.uniform(2.0, 5.0)

    return base.astype(np.float32)


def load_my_autoencoder(checkpoint_path: str):
    model = LSTMAutoencoder(
        input_size=6,
        hidden_size=AUTOENCODER["hidden_size"],
        latent_size=AUTOENCODER["latent_size"],
        num_layers=AUTOENCODER["num_layers"],
        dropout=AUTOENCODER["dropout"],
        
    ).to(DEVICE)

    checkpoint = torch.load(checkpoint_path, map_location=DEVICE)
    model.load_state_dict(checkpoint)

    model.eval()
    return model


def run(args) -> None:
    set_seed(RANDOM_SEED)

    # ── Prepare base windows ─────────────────────────────────────────────────
    log.info("Preparing base IMU windows…")
    normal_windows     = make_synthetic_normal(300)
    aggressive_windows = make_synthetic_aggressive(150)
    risky_windows      = make_synthetic_risky(150)

    log.info(
        f"  Normal: {normal_windows.shape}  |  "
        f"Aggressive: {aggressive_windows.shape}  |  "
        f"Risky: {risky_windows.shape}"
    )

    # ═══════════════════════════════════════════════════════════════════════
    # STAGE 2: Synthetic Crash Generation
    # ═══════════════════════════════════════════════════════════════════════
    log.info("\n" + "=" * 60)
    log.info("STAGE 2: Synthetic Crash Generation")
    log.info("=" * 60)

    generator = SyntheticCrashGenerator(
        normal_windows          = normal_windows,
        n_per_type_per_severity = 30,   # reduce for quick demo
        seed                    = RANDOM_SEED,
    )
    crash_windows, crash_labels, crash_meta = generator.generate_all()
    meta_df = generator.meta_to_dataframe(crash_meta)

    log.info(f"Generated {len(crash_windows)} synthetic crash windows")
    log.info(f"\nCrash type distribution:\n{meta_df['crash_type'].value_counts()}")
    log.info(f"\nSeverity distribution:\n{meta_df['severity'].value_counts()}")

    # Visualise one normal vs all crash types
    one_normal = normal_windows[0]
    crash_examples = {}
    for crash_type in _CRASH_FN.keys():
        mask = meta_df["crash_type"] == crash_type
        idx  = meta_df[mask].index[0]
        crash_examples[crash_type] = crash_windows[idx]

    plot_crash_comparison(
        normal_window = one_normal,
        crash_windows = crash_examples,
        save_path     = os.path.join(PLOTS_DIR, "crash_comparison.png"),
    )

    # Visualise severity levels for frontal crashes
    frontal_mask = meta_df["crash_type"] == "frontal"
    sev_examples = {}
    for sev in ["low", "medium", "high", "extreme"]:
        sub = meta_df[frontal_mask & (meta_df["severity"] == sev)]
        if len(sub) > 0:
            sev_examples[sev] = crash_windows[sub.index[0]]

    plot_severity_comparison(
        crash_type               = "frontal",
        crash_windows_by_severity= sev_examples,
        save_path                = os.path.join(PLOTS_DIR, "severity_comparison.png"),
    )

    # Save crash metadata
    from utils.utils import save_csv
    save_csv(meta_df, "outputs/crash_metadata.csv")

    # ═══════════════════════════════════════════════════════════════════════
    # STAGE 3: Anomaly Evaluation
    # ═══════════════════════════════════════════════════════════════════════
    log.info("\n" + "=" * 60)
    log.info("STAGE 3: Anomaly Evaluation")
    log.info("=" * 60)

    model = load_my_autoencoder(args.autoencoder)

    windows_dict = {
        "normal"    : normal_windows,
        "aggressive": aggressive_windows,
        "risky"     : risky_windows,
        "crash"     : crash_windows,
    }
    labels_dict = {
        "normal"    : np.zeros(len(normal_windows),     dtype=int),
        "aggressive": np.zeros(len(aggressive_windows), dtype=int),
        "risky"     : np.zeros(len(risky_windows),      dtype=int),
        "crash"     : np.ones(len(crash_windows),       dtype=int),
    }

    evaluator = AnomalyEvaluator(model, device=DEVICE)
    stage3    = evaluator.run(windows_dict, labels_dict)

    evaluator.generate_all_plots(stage3, windows_dict)
    evaluator.save_outputs(stage3)

    log.info("\nStage 3 metrics:")
    for k, v in stage3["metrics"].items():
        if isinstance(v, float):
            log.info(f"  {k:20s}: {v:.4f}")

    # ═══════════════════════════════════════════════════════════════════════
    # STAGE 4: Crash Confirmation Classifier
    # ═══════════════════════════════════════════════════════════════════════
    log.info("\n" + "=" * 60)
    log.info("STAGE 4: Crash Confirmation Classifier")
    log.info("=" * 60)

    # Build feature dataset from inference results
    inf = stage3["inference"]
    feature_df = build_labeled_dataset(
        windows_dict         = windows_dict,
        recon_errors_dict    = {cat: inf[cat]["recon_error"]       for cat in inf},
        per_feat_errors_dict = {cat: inf[cat]["per_feature_error"] for cat in inf},
    )

    log.info(f"Feature dataset: {feature_df.shape}  |  "
             f"Crash rate: {feature_df['is_crash'].mean():.2%}")

    stage4_pipeline = CrashConfirmationPipeline(device=DEVICE)
    stage4 = stage4_pipeline.run(feature_df)

    stage4_pipeline.generate_all_plots(stage4)
    stage4_pipeline.save_outputs(stage4)

    # Final summary
    log.info("\n" + "=" * 60)
    log.info("PIPELINE COMPLETE — Summary")
    log.info("=" * 60)
    log.info(f"\nStage 3 (Autoencoder Anomaly Detection):")
    log.info(f"  ROC-AUC  : {stage3['metrics']['roc_auc']:.3f}")
    log.info(f"  F1 Score : {stage3['metrics']['f1_score']:.3f}")

    log.info(f"\nStage 4 (Crash Confirmation Classifiers):")
    for m in stage4["metrics_list"]:
        log.info(
            f"  {m['classifier']:14s}  "
            f"AUC={m['roc_auc']:.3f}  F1={m['f1_score']:.3f}  "
            f"P={m['precision']:.3f}  R={m['recall']:.3f}"
        )

    log.info(f"\nOutputs saved to: outputs/")
    log.info(f"Plots  saved to: {PLOTS_DIR}/")
    log.info(f"Models saved to: {MODELS_DIR}/")


# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="IMU Crash Detection Pipeline")
    parser.add_argument(
        "--autoencoder", type=str, default=None,
        help="Path to trained LSTM autoencoder .pt checkpoint"
    )
    args = parser.parse_args()
    run(args)
