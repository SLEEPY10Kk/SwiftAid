"""
augmentation/crash_generator.py
================================
STAGE 2 — Synthetic Crash Generation
=====================================

Physics rationale
-----------------
A real vehicular collision produces a characteristic IMU signature:

  1. PRE-IMPACT  (~50 ms):  mild braking deceleration along acc_y (forward axis).
  2. IMPACT      (~50-150 ms): sharp impulse spike — largest signal in the window.
     - Frontal:  dominant negative acc_y (forward decel), minor acc_z, small gyro.
     - Side:     dominant acc_x (lateral), large gyro_z (yaw impulse).
     - Rollover: large acc_x + acc_z, very large gyro_x / gyro_y (roll + pitch).
     - Abrupt stop: large negative acc_y only, minimal rotation.
  3. POST-IMPACT (~0.5–2 s): damped oscillations from vehicle structure/body dynamics.
     Modelled as exponentially decaying sinusoids.

All values in g-units (acc) and rad/s (gyro).
Normal driving acc ≈ 0.2–1.5 g; crash peaks typically 10–80 g on phone.
We scale peaks by a configurable severity factor.
"""

import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
from dataclasses import dataclass, field
from typing import List, Tuple, Dict

from config.config import (
    RANDOM_SEED, WINDOW_SIZE, N_FEATURES, FEATURES,
    CRASH_PHYSICS, CRASH_SEVERITY_LEVELS,
    N_SYNTHETIC_PER_TYPE, SAMPLE_RATE_HZ, PLOTS_DIR,
)
from utils.utils import set_seed, get_logger, acc_magnitude, gyro_magnitude

log = get_logger("crash_generator")


# ─────────────────────────────────────────────────────────────────────────────
# DATA CLASSES
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class CrashMeta:
    """Metadata attached to every generated crash window."""
    crash_type  : str          # e.g. "frontal"
    severity    : str          # e.g. "high"
    severity_factor: float     # numeric scale
    impact_start: int          # sample index where impact begins
    impact_duration: int       # samples
    peak_acc_g  : float        # maximum acc magnitude in window
    peak_gyro   : float        # maximum gyro magnitude in window
    label       : int = 1      # 1 = crash, 0 = normal


# ─────────────────────────────────────────────────────────────────────────────
# LOW-LEVEL WAVEFORM BUILDERS
# ─────────────────────────────────────────────────────────────────────────────

def _impact_pulse(n: int, peak: float, duration: int, start: int) -> np.ndarray:
    """
    One-sided half-sine impulse representing the impact force.
    Half-sine avoids physically impossible instantaneous step changes.

    Args:
        n        : total window length (samples)
        peak     : peak value of the pulse
        duration : impulse width in samples
        start    : sample index where impulse begins

    Returns:
        1-D array of length n
    """
    signal = np.zeros(n)
    t      = np.linspace(0, np.pi, duration)
    end    = min(start + duration, n)
    actual = end - start
    signal[start:end] = peak * np.sin(t[:actual])
    return signal


def _damped_oscillation(n: int, amplitude: float, start: int,
                        freq_hz: float, decay: float,
                        sample_rate: int = SAMPLE_RATE_HZ) -> np.ndarray:
    """
    Exponentially decaying sinusoid — models post-impact vibration.

    Physics: after collision energy is partially absorbed by vehicle structure
    and partially causes resonant vibration at chassis natural frequencies
    (typically 5–15 Hz for passenger cars).

    Args:
        n          : total window length
        amplitude  : initial oscillation amplitude
        start      : sample index where oscillation begins
        freq_hz    : oscillation frequency
        decay      : per-sample decay factor (< 1)
        sample_rate: Hz

    Returns:
        1-D array of length n
    """
    signal  = np.zeros(n)
    length  = n - start
    if length <= 0:
        return signal
    t       = np.arange(length) / sample_rate
    envelope= amplitude * (decay ** np.arange(length))
    signal[start:] = envelope * np.sin(2 * np.pi * freq_hz * t)
    return signal


def _pre_impact_braking(n: int, brake_g: float, end: int) -> np.ndarray:
    """
    Mild linear deceleration ramp before impact (driver reaction / ABS).
    Provides temporal continuity so the signal does not start at the crash.
    """
    signal = np.zeros(n)
    ramp   = np.linspace(0, -brake_g, end)
    signal[:end] = ramp
    return signal


def _add_sensor_noise(signal: np.ndarray,
                      noise_std: float = 0.03) -> np.ndarray:
    """
    Add realistic white Gaussian noise to mimic MEMS sensor noise floor.
    Typical phone IMU noise: ~0.01–0.05 g RMS at 100 Hz.
    """
    return signal + np.random.normal(0, noise_std, size=signal.shape)


# ─────────────────────────────────────────────────────────────────────────────
# PER-TYPE CRASH GENERATORS
# ─────────────────────────────────────────────────────────────────────────────

def _generate_frontal(base_window: np.ndarray, severity_factor: float,
                      impact_start: int) -> np.ndarray:
    """
    Frontal collision: primary force along vehicle forward axis (acc_y here).
    Secondary crumple-zone dynamics produce minor vertical (acc_z) component.
    Rotation is minimal but not zero — head restraint oscillation causes gyro_z spike.
    """
    n      = len(base_window)
    p      = CRASH_PHYSICS
    w      = base_window.copy()
    dur    = p["impact_duration_samples"]
    peak   = p["frontal_peak_g"] * severity_factor
    decay  = p["oscillation_decay"]
    freq   = p["oscillation_freq_hz"]

    # Pre-impact braking (small ramp)
    w[:, 1] += _pre_impact_braking(n, 0.5, impact_start)

    # Impact: dominant deceleration on acc_y, minor on acc_z
    w[:, 1] += _impact_pulse(n, -peak,       dur, impact_start)
    w[:, 2] += _impact_pulse(n,  peak * 0.3, dur, impact_start)

    # Gyro: small yaw & pitch oscillation from vehicle body response
    gyro_peak = p["frontal_gyro_peak"] * severity_factor
    post = impact_start + dur
    w[:, 4] += _damped_oscillation(n, gyro_peak * 0.5, post, freq, decay)
    w[:, 5] += _damped_oscillation(n, gyro_peak,       post, freq * 1.2, decay)

    # Post-impact oscillations on accelerometers
    w[:, 1] += _damped_oscillation(n, peak * 0.4, post, freq, decay)
    w[:, 2] += _damped_oscillation(n, peak * 0.15, post, freq * 1.5, decay)

    return _add_sensor_noise(w)


def _generate_side(base_window: np.ndarray, severity_factor: float,
                   impact_start: int) -> np.ndarray:
    """
    Side (T-bone) collision: primary force on lateral axis (acc_x).
    Large yaw rotation (gyro_z) as vehicle is pushed sideways.
    Also produces roll impulse (gyro_x) from weight transfer.
    """
    n      = len(base_window)
    p      = CRASH_PHYSICS
    w      = base_window.copy()
    dur    = p["impact_duration_samples"]
    peak   = p["side_peak_g"] * severity_factor
    decay  = p["oscillation_decay"]
    freq   = p["oscillation_freq_hz"]

    # Impact: dominant lateral acceleration
    w[:, 0] += _impact_pulse(n,  peak,       dur, impact_start)
    w[:, 2] += _impact_pulse(n,  peak * 0.2, dur, impact_start)

    # Gyro: large yaw spike + roll component
    gyro_peak = p["side_gyro_peak"] * severity_factor
    post = impact_start + dur
    w[:, 3] += _damped_oscillation(n, gyro_peak * 0.6, post, freq,       decay)
    w[:, 5] += _damped_oscillation(n, gyro_peak,       post, freq * 0.8, decay)

    # Post-impact lateral oscillation
    w[:, 0] += _damped_oscillation(n, peak * 0.35, post, freq, decay)

    return _add_sensor_noise(w)


def _generate_rollover(base_window: np.ndarray, severity_factor: float,
                       impact_start: int) -> np.ndarray:
    """
    Rollover event: high gyroscope magnitudes in roll (gyro_x) and pitch (gyro_y).
    Acceleration signal cycles through sign changes as vehicle rotates.
    This is the most distinctive gyro-dominated signature.
    """
    n      = len(base_window)
    p      = CRASH_PHYSICS
    w      = base_window.copy()
    dur    = p["impact_duration_samples"] * 2  # longer initial trigger
    peak   = p["rollover_peak_g"] * severity_factor
    decay  = p["oscillation_decay"] ** 0.5      # slower decay for persistent roll
    freq_roll = 2.5                              # roll ~2–4 Hz

    post = impact_start + dur

    # Trigger impulse: initial road-edge contact
    w[:, 2] += _impact_pulse(n, peak, dur, impact_start)

    # Continuous roll rotation — large sustained gyro_x signal
    gyro_peak = p["rollover_gyro_peak"] * severity_factor
    roll_len  = min(n - post, 60)          # up to 0.6 s of roll
    t_roll    = np.arange(roll_len) / SAMPLE_RATE_HZ
    env       = gyro_peak * np.exp(-0.8 * t_roll)
    w[post:post + roll_len, 3] += env * np.sin(2 * np.pi * freq_roll * t_roll)
    w[post:post + roll_len, 4] += env * 0.7 * np.cos(2 * np.pi * freq_roll * t_roll)

    # Gravity vector projection changes dramatically during roll
    t_acc = np.arange(roll_len) / SAMPLE_RATE_HZ
    w[post:post + roll_len, 0] += peak * 0.5 * np.sin(2 * np.pi * freq_roll * t_acc)
    w[post:post + roll_len, 2] += peak * 0.5 * np.cos(2 * np.pi * freq_roll * t_acc)

    return _add_sensor_noise(w)


def _generate_abrupt_stop(base_window: np.ndarray, severity_factor: float,
                          impact_start: int) -> np.ndarray:
    """
    Abrupt stop / rear-end: sudden strong deceleration along acc_y.
    Unlike frontal collision, the vehicle does NOT crumple significantly.
    Primarily a pure deceleration event with minimal rotation.
    """
    n      = len(base_window)
    p      = CRASH_PHYSICS
    w      = base_window.copy()
    dur    = p["impact_duration_samples"]
    peak   = p["abrupt_stop_peak_g"] * severity_factor
    decay  = p["oscillation_decay"]
    freq   = p["oscillation_freq_hz"]

    # Pre-impact: driver sees obstacle and starts braking
    w[:, 1] += _pre_impact_braking(n, 0.8, impact_start)

    # Sharp deceleration spike on acc_y only
    w[:, 1] += _impact_pulse(n, -peak, dur, impact_start)

    # Minor seat-belt jerk on vertical acc_z
    w[:, 2] += _impact_pulse(n, peak * 0.1, dur, impact_start)

    # Very small gyro (head bob from harness)
    post      = impact_start + dur
    gyro_peak = p["abrupt_stop_gyro_peak"] * severity_factor
    w[:, 4] += _damped_oscillation(n, gyro_peak, post, freq * 1.5, decay)

    # Post-impact body-on-spring oscillation
    w[:, 1] += _damped_oscillation(n, peak * 0.25, post, freq, decay)

    return _add_sensor_noise(w)


# ─────────────────────────────────────────────────────────────────────────────
# MAIN GENERATOR CLASS
# ─────────────────────────────────────────────────────────────────────────────

_CRASH_FN = {
    "frontal"     : _generate_frontal,
    "side"        : _generate_side,
    "rollover"    : _generate_rollover,
    "abrupt_stop" : _generate_abrupt_stop,
}


class SyntheticCrashGenerator:
    """
    Generates synthetic crash IMU windows from a pool of normal driving windows.

    Strategy
    --------
    1. Sample a normal driving window (provides realistic pre-crash context).
    2. Choose a random impact start index in the first 30–60 % of the window
       so post-impact oscillation fits within the window.
    3. Apply crash-type-specific signal engineering.
    4. Record per-window metadata for downstream evaluation.
    """

    def __init__(self,
                 normal_windows: np.ndarray,
                 n_per_type_per_severity: int = N_SYNTHETIC_PER_TYPE,
                 seed: int = RANDOM_SEED):
        """
        Args:
            normal_windows          : [N, T, 6] array of normal driving windows
            n_per_type_per_severity : how many windows to generate per (type, severity) pair
            seed                    : random seed
        """
        set_seed(seed)
        assert normal_windows.shape[-1] == N_FEATURES, \
            f"Expected {N_FEATURES} features, got {normal_windows.shape[-1]}"
        self.normal = normal_windows
        self.n      = n_per_type_per_severity
        self.T      = normal_windows.shape[1]
        log.info(
            f"Generator ready — {len(normal_windows)} base windows, "
            f"{n_per_type_per_severity} per (type, severity)"
        )

    # ------------------------------------------------------------------
    def generate_all(self) -> Tuple[np.ndarray, np.ndarray, List[CrashMeta]]:
        """
        Generate every (crash_type × severity) combination.

        Returns
        -------
        windows   : [M, T, 6]  float32 array of crash windows
        labels    : [M]        int array (all 1)
        meta_list : list of CrashMeta objects
        """
        all_windows, all_labels, all_meta = [], [], []

        for crash_type, fn in _CRASH_FN.items():
            for sev_name, sev_factor in CRASH_SEVERITY_LEVELS.items():
                w_batch, meta_batch = self._generate_batch(
                    fn, crash_type, sev_name, sev_factor
                )
                all_windows.append(w_batch)
                all_labels.extend([1] * len(w_batch))
                all_meta.extend(meta_batch)
                log.info(
                    f"  Generated {len(w_batch):4d} windows  "
                    f"type={crash_type:12s}  severity={sev_name}"
                )

        windows = np.concatenate(all_windows, axis=0).astype(np.float32)
        labels  = np.array(all_labels, dtype=np.int64)
        log.info(f"Total synthetic crash windows: {len(windows)}")
        return windows, labels, all_meta

    # ------------------------------------------------------------------
    def _generate_batch(self, fn, crash_type, sev_name, sev_factor):
        """Generate one batch for a given crash type and severity."""
        windows, meta_list = [], []

        for _ in range(self.n):
            # Sample a random base window
            idx  = np.random.randint(0, len(self.normal))
            base = self.normal[idx].copy()  # [T, 6]

            # Place impact in first 30–55 % of window
            # (leaves room for post-impact oscillation)
            min_start = int(self.T * 0.10)
            max_start = int(self.T * 0.55)
            impact_start = np.random.randint(min_start, max_start)

            # Generate crash signal
            crash_w = fn(base, sev_factor, impact_start)  # [T, 6]

            # Compute peak metrics for metadata
            acc_mag  = np.sqrt(np.sum(crash_w[:, :3] ** 2, axis=-1))
            gyro_mag = np.sqrt(np.sum(crash_w[:, 3:] ** 2, axis=-1))

            meta = CrashMeta(
                crash_type     = crash_type,
                severity       = sev_name,
                severity_factor= sev_factor,
                impact_start   = impact_start,
                impact_duration= CRASH_PHYSICS["impact_duration_samples"],
                peak_acc_g     = float(np.max(acc_mag)),
                peak_gyro      = float(np.max(gyro_mag)),
            )

            windows.append(crash_w)
            meta_list.append(meta)

        return np.stack(windows), meta_list

    # ------------------------------------------------------------------
    def meta_to_dataframe(self, meta_list: List[CrashMeta]) -> pd.DataFrame:
        """Convert list of CrashMeta to a tidy DataFrame."""
        return pd.DataFrame([vars(m) for m in meta_list])


# ─────────────────────────────────────────────────────────────────────────────
# VISUALIZATION
# ─────────────────────────────────────────────────────────────────────────────

def plot_crash_comparison(
    normal_window : np.ndarray,
    crash_windows : Dict[str, np.ndarray],
    sample_rate   : int = SAMPLE_RATE_HZ,
    save_path     : str = None,
) -> None:
    """
    Side-by-side comparison of one normal window vs one window of each crash type.

    Panels per row:
        - Raw acc_x/y/z
        - Raw gyro_x/y/z
        - Acceleration magnitude
        - Gyroscope magnitude
    """
    crash_types = list(crash_windows.keys())
    n_cols      = 1 + len(crash_types)       # normal + crashes
    n_rows      = 4
    t           = np.arange(normal_window.shape[0]) / sample_rate * 1000  # ms

    fig = plt.figure(figsize=(5 * n_cols, 4 * n_rows))
    fig.suptitle("Normal vs Synthetic Crash Signals", fontsize=14, fontweight="bold", y=1.01)
    gs  = gridspec.GridSpec(n_rows, n_cols, hspace=0.5, wspace=0.35)

    row_labels = [
        "Accelerometer (g)",
        "Gyroscope (rad/s)",
        "Acc Magnitude (g)",
        "Gyro Magnitude (rad/s)",
    ]

    all_windows = {"normal": normal_window, **crash_windows}

    for col, (title, win) in enumerate(all_windows.items()):
        acc_mag  = np.sqrt(np.sum(win[:, :3] ** 2, axis=-1))
        gyro_mag = np.sqrt(np.sum(win[:, 3:] ** 2, axis=-1))

        # Row 0: Accelerometer
        ax = fig.add_subplot(gs[0, col])
        for i, label in enumerate(["acc_x", "acc_y", "acc_z"]):
            ax.plot(t, win[:, i], label=label, linewidth=0.8)
        ax.set_title(title.replace("_", " ").title(), fontweight="bold")
        ax.set_ylabel(row_labels[0] if col == 0 else "")
        ax.legend(fontsize=7, loc="upper right")
        ax.set_xlabel("ms")

        # Row 1: Gyroscope
        ax = fig.add_subplot(gs[1, col])
        for i, label in enumerate(["gyro_x", "gyro_y", "gyro_z"]):
            ax.plot(t, win[:, 3 + i], label=label, linewidth=0.8)
        ax.set_ylabel(row_labels[1] if col == 0 else "")
        ax.legend(fontsize=7, loc="upper right")
        ax.set_xlabel("ms")

        # Row 2: Acc magnitude
        ax = fig.add_subplot(gs[2, col])
        color = "steelblue" if title == "normal" else "crimson"
        ax.fill_between(t, acc_mag, alpha=0.4, color=color)
        ax.plot(t, acc_mag, color=color, linewidth=1)
        ax.set_ylabel(row_labels[2] if col == 0 else "")
        ax.set_xlabel("ms")

        # Row 3: Gyro magnitude
        ax = fig.add_subplot(gs[3, col])
        ax.fill_between(t, gyro_mag, alpha=0.4, color=color)
        ax.plot(t, gyro_mag, color=color, linewidth=1)
        ax.set_ylabel(row_labels[3] if col == 0 else "")
        ax.set_xlabel("ms")

    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
        log.info(f"Crash comparison plot saved → {save_path}")
    plt.show()
    plt.close()


def plot_severity_comparison(
    crash_type: str,
    crash_windows_by_severity: Dict[str, np.ndarray],
    sample_rate: int = SAMPLE_RATE_HZ,
    save_path: str = None,
) -> None:
    """
    Show acceleration & gyroscope magnitudes across all severity levels for one crash type.
    Helps verify that severity scaling is physically meaningful.
    """
    t = np.arange(list(crash_windows_by_severity.values())[0].shape[0]) / sample_rate * 1000

    fig, axes = plt.subplots(1, 2, figsize=(12, 4))
    fig.suptitle(f"Severity Levels — {crash_type.replace('_',' ').title()}", fontweight="bold")

    colors = {"low": "#4FC3F7", "medium": "#FFA726", "high": "#EF5350", "extreme": "#7B1FA2"}

    for sev, win in crash_windows_by_severity.items():
        acc_mag  = np.sqrt(np.sum(win[:, :3] ** 2, axis=-1))
        gyro_mag = np.sqrt(np.sum(win[:, 3:] ** 2, axis=-1))
        c = colors.get(sev, "gray")
        axes[0].plot(t, acc_mag,  label=sev, color=c, linewidth=1.2)
        axes[1].plot(t, gyro_mag, label=sev, color=c, linewidth=1.2)

    axes[0].set(title="Acceleration Magnitude (g)",   xlabel="ms", ylabel="g")
    axes[1].set(title="Gyroscope Magnitude (rad/s)", xlabel="ms", ylabel="rad/s")
    for ax in axes:
        ax.legend()
        ax.grid(True, alpha=0.3)

    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
        log.info(f"Severity comparison saved → {save_path}")
    plt.show()
    plt.close()
