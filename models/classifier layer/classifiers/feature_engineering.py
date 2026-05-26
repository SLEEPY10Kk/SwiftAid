"""
classifiers/feature_engineering.py
====================================
Stage 4 — Feature Engineering for Crash Confirmation Classifier

Extracts a fixed-length feature vector from each IMU window + autoencoder output.
These handcrafted features supplement the autoencoder's reconstruction error
with interpretable crash-physics features that classical models can use directly.

Feature Groups
--------------
1. AUTOENCODER FEATURES    — reconstruction error, per-feature error
2. MAGNITUDE STATISTICS    — peak, mean, std of acc/gyro magnitudes
3. JERK FEATURES           — peak jerk, jerk energy
4. IMPACT FEATURES         — duration above threshold, peak acceleration
5. ROTATION ENERGY         — integrated gyroscope squared
6. STATISTICAL IMU         — skew, kurtosis, percentiles per axis
7. FREQUENCY DOMAIN        — dominant FFT frequency, spectral entropy
"""

import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import numpy as np
import pandas as pd
from scipy.stats import skew, kurtosis
from scipy.fft   import rfft, rfftfreq
from typing      import List, Optional

from config.config import SAMPLE_RATE_HZ, FEATURES, N_FEATURES
from utils.utils   import get_logger, acc_magnitude, gyro_magnitude, compute_jerk

log = get_logger("feature_engineering")

# Impact threshold: accelerations above this (g) are counted as "impact" samples
IMPACT_ACC_THRESHOLD_G = 4.0


def extract_window_features(
    window         : np.ndarray,       # [T, F]
    recon_error    : Optional[float] = None,
    per_feat_error : Optional[np.ndarray] = None,  # [F]
    dt             : float = 1.0 / SAMPLE_RATE_HZ,
) -> dict:
    """
    Extract all crash-discriminative features from a single IMU window.

    Args:
        window        : [T, F] normalized IMU data
        recon_error   : autoencoder reconstruction MSE for this window
        per_feat_error: per-feature reconstruction MSE [F]
        dt            : sampling interval (seconds)

    Returns:
        Dict of named scalar features.
    """
    T = len(window)
    features = {}

    # ── 1. Autoencoder features ──────────────────────────────────────────────
    if recon_error is not None:
        features["ae_recon_error"] = float(recon_error)

    if per_feat_error is not None:
        for i, fname in enumerate(FEATURES):
            features[f"ae_feat_err_{fname}"] = float(per_feat_error[i])

    # ── 2. Magnitude statistics ───────────────────────────────────────────────
    acc_mag  = np.sqrt(np.sum(window[:, :3] ** 2, axis=-1))   # [T]
    gyro_mag = np.sqrt(np.sum(window[:, 3:] ** 2, axis=-1))   # [T]

    for prefix, mag in [("acc_mag", acc_mag), ("gyro_mag", gyro_mag)]:
        features[f"{prefix}_peak"]   = float(np.max(mag))
        features[f"{prefix}_mean"]   = float(np.mean(mag))
        features[f"{prefix}_std"]    = float(np.std(mag))
        features[f"{prefix}_p95"]    = float(np.percentile(mag, 95))
        features[f"{prefix}_range"]  = float(np.max(mag) - np.min(mag))

    # ── 3. Jerk features ─────────────────────────────────────────────────────
    # Jerk = d(acceleration)/dt; crash spikes cause extreme jerk
    jerk_mag = compute_jerk(window[np.newaxis], dt)[0]  # [T]
    features["jerk_peak"]     = float(np.max(jerk_mag))
    features["jerk_mean"]     = float(np.mean(jerk_mag))
    features["jerk_energy"]   = float(np.sum(jerk_mag ** 2) * dt)
    features["jerk_p99"]      = float(np.percentile(jerk_mag, 99))

    # ── 4. Impact duration ────────────────────────────────────────────────────
    # Counts samples where acceleration magnitude exceeds crash threshold
    # Short high-amplitude bursts = impact; prolonged = aggressive driving
    impact_mask = acc_mag > IMPACT_ACC_THRESHOLD_G
    features["impact_duration_samples"] = int(np.sum(impact_mask))
    features["impact_duration_frac"]    = float(np.mean(impact_mask))

    # Peak acceleration (signed, per axis)
    for i, axis in enumerate(["x", "y", "z"]):
        features[f"peak_acc_{axis}"]  = float(np.max(np.abs(window[:, i])))
        features[f"peak_gyro_{axis}"] = float(np.max(np.abs(window[:, 3 + i])))

    # ── 5. Rotational energy ──────────────────────────────────────────────────
    # Integral of gyro² — high for rollovers, very low for straight impacts
    features["rotational_energy"] = float(np.sum(gyro_mag ** 2) * dt)
    features["rot_energy_x"]      = float(np.sum(window[:, 3] ** 2) * dt)
    features["rot_energy_y"]      = float(np.sum(window[:, 4] ** 2) * dt)
    features["rot_energy_z"]      = float(np.sum(window[:, 5] ** 2) * dt)

    # ── 6. Statistical IMU features ───────────────────────────────────────────
    for i, fname in enumerate(FEATURES):
        col = window[:, i]
        features[f"stat_mean_{fname}"]      = float(np.mean(col))
        features[f"stat_std_{fname}"]       = float(np.std(col))
        features[f"stat_skew_{fname}"]      = float(skew(col))
        features[f"stat_kurt_{fname}"]      = float(kurtosis(col))
        features[f"stat_max_abs_{fname}"]   = float(np.max(np.abs(col)))

    # ── 7. Frequency domain features ─────────────────────────────────────────
    # Crash signals have broadband energy; normal driving is mostly low-frequency
    freqs = rfftfreq(T, d=dt)
    for i, fname in enumerate(["acc_x", "acc_y", "acc_z"]):
        col      = window[:, i]
        spectrum = np.abs(rfft(col)) ** 2
        # Dominant frequency
        dom_freq = freqs[np.argmax(spectrum[1:]) + 1] if len(spectrum) > 1 else 0.0
        features[f"fft_dom_freq_{fname}"]     = float(dom_freq)
        # Spectral entropy (uniformly distributed spectrum = high entropy = broadband)
        p = spectrum / (spectrum.sum() + 1e-10)
        features[f"fft_entropy_{fname}"]      = float(-np.sum(p * np.log(p + 1e-10)))
        # Energy in high-frequency band (> 10 Hz) — crash broadens spectrum
        hf_mask = freqs > 10.0
        features[f"fft_hf_energy_{fname}"]    = float(spectrum[hf_mask].sum())

    return features


def extract_features_batch(
    windows          : np.ndarray,       # [N, T, F]
    recon_errors     : Optional[np.ndarray] = None,   # [N]
    per_feat_errors  : Optional[np.ndarray] = None,   # [N, F]
    dt               : float = 1.0 / SAMPLE_RATE_HZ,
) -> pd.DataFrame:
    """
    Extract features for a batch of windows.

    Returns a DataFrame with one row per window, one column per feature.
    """
    rows = []
    N    = len(windows)
    for i in range(N):
        row = extract_window_features(
            window         = windows[i],
            recon_error    = float(recon_errors[i]) if recon_errors is not None else None,
            per_feat_error = per_feat_errors[i]     if per_feat_errors is not None else None,
            dt             = dt,
        )
        rows.append(row)

    df = pd.DataFrame(rows)
    log.info(f"Extracted {len(df.columns)} features from {N} windows")
    return df


def build_labeled_dataset(
    windows_dict        : dict,   # {category: [N, T, F]}
    recon_errors_dict   : dict,   # {category: [N]}
    per_feat_errors_dict: dict,   # {category: [N, F]}
) -> pd.DataFrame:
    """
    Build a labeled feature DataFrame from all categories.

    Label mapping:
        normal     → 0
        aggressive → 1
        risky      → 1   (both are non-crash anomalies; binary crash vs non-crash)
        crash      → 2

    For multi-class training, crash=2. For binary (crash / not-crash), map crash→1, others→0.

    Returns DataFrame with features + 'label' + 'category' columns.
    """
    LABEL_MAP = {"normal": 0, "aggressive": 1, "risky": 1, "crash": 2}

    all_dfs = []
    for cat, windows in windows_dict.items():
        feat_df = extract_features_batch(
            windows         = windows,
            recon_errors    = recon_errors_dict.get(cat),
            per_feat_errors = per_feat_errors_dict.get(cat),
        )
        feat_df["category"]   = cat
        feat_df["label"]      = LABEL_MAP.get(cat, 0)
        feat_df["is_crash"]   = int(cat == "crash")   # binary crash flag
        all_dfs.append(feat_df)

    combined = pd.concat(all_dfs, ignore_index=True)
    log.info(
        f"Combined dataset: {len(combined)} windows, "
        f"{len(combined.columns)} columns"
    )
    return combined
