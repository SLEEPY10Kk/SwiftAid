"""
config/config.py
================
Central configuration for the IMU crash detection pipeline.
All hyperparameters, paths, and physics constants live here.
"""

import os

# ─────────────────────────────────────────────
# REPRODUCIBILITY
# ─────────────────────────────────────────────
RANDOM_SEED = 42

# ─────────────────────────────────────────────
# DATA / SIGNAL
# ─────────────────────────────────────────────
SAMPLE_RATE_HZ   = 100          # IMU sampling rate (100 Hz → 10 ms per sample)
WINDOW_SIZE      = 100          # timesteps per window  (= 1 second)
FEATURES         = ["acc_x", "acc_y", "acc_z", "gyro_x", "gyro_y", "gyro_z"]
N_FEATURES       = len(FEATURES)

# ─────────────────────────────────────────────
# CRASH PHYSICS CONSTANTS
# ─────────────────────────────────────────────
# Accelerometer values are in g (1 g ≈ 9.81 m/s²)
# Phone IMU typical crash peaks: 10–80 g
CRASH_PHYSICS = {
    # Peak linear acceleration magnitudes (g)
    "frontal_peak_g"        : 25.0,
    "side_peak_g"           : 20.0,
    "rollover_peak_g"       : 15.0,
    "abrupt_stop_peak_g"    : 18.0,

    # Gyroscope peaks (rad/s) — rollover can reach 15+ rad/s
    "frontal_gyro_peak"     : 4.0,
    "side_gyro_peak"        : 6.0,
    "rollover_gyro_peak"    : 14.0,
    "abrupt_stop_gyro_peak" : 3.0,

    # Impact impulse duration (samples at 100 Hz)
    "impact_duration_samples": 10,   # ~100 ms peak

    # Post-impact oscillation decay constant
    "oscillation_decay"     : 0.85,
    "oscillation_freq_hz"   : 8.0,
}

# ─────────────────────────────────────────────
# SYNTHETIC CRASH GENERATION
# ─────────────────────────────────────────────
CRASH_SEVERITY_LEVELS = {
    "low"    : 0.4,   # 40% of nominal peak
    "medium" : 0.7,
    "high"   : 1.0,
    "extreme": 1.4,
}

N_SYNTHETIC_PER_TYPE = 50   # windows to generate per crash type per severity

# ─────────────────────────────────────────────
# LSTM AUTOENCODER
# ─────────────────────────────────────────────
AUTOENCODER = {
    "hidden_size"   : 64,
    "latent_size"   : 16,
    "num_layers"    : 2,
    "dropout"       : 0.2,
    "checkpoint"    : "models/autoencoder.pt",
}

# ─────────────────────────────────────────────
# ANOMALY DETECTION THRESHOLDS
# ─────────────────────────────────────────────
ANOMALY = {
    "percentile_threshold"  : 95,    # percentile of normal recon errors
    "sigma_multiplier"      : 3.0,   # mean + k*std threshold
    "adaptive_window"       : 200,   # samples for rolling adaptive threshold
}

# ─────────────────────────────────────────────
# CLASSIFIERS
# ─────────────────────────────────────────────
CLASSIFIER = {
    # Random Forest
    "rf_n_estimators"   : 200,
    "rf_max_depth"      : 12,
    # XGBoost
    "xgb_n_estimators"  : 200,
    "xgb_max_depth"     : 6,
    "xgb_lr"            : 0.05,
    # MLP
    "mlp_hidden"        : [128, 64, 32],
    "mlp_lr"            : 1e-3,
    "mlp_epochs"        : 50,
    "mlp_batch_size"    : 64,
    # Threshold tuning — target false-positive rate
    "fp_rate_target"    : 0.05,
}

# ─────────────────────────────────────────────
# PATHS
# ─────────────────────────────────────────────
OUTPUT_DIR      = "outputs"
PLOTS_DIR       = os.path.join(OUTPUT_DIR, "plots")
MODELS_DIR      = "models"
REPORTS_DIR     = os.path.join(OUTPUT_DIR, "reports")

for d in [OUTPUT_DIR, PLOTS_DIR, MODELS_DIR, REPORTS_DIR]:
    os.makedirs(d, exist_ok=True)

# ─────────────────────────────────────────────
# DEVICE (GPU / CPU)
# ─────────────────────────────────────────────
import torch
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
