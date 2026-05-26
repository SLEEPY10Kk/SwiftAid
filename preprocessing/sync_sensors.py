from pathlib import Path

import numpy as np
import pandas as pd


WORKDIR = Path(__file__).resolve().parent
ACCEL_CSV = Path(r"C:\Users\HP\Desktop\MAIN MODEL\pretrain datasets\Accelerometer.csv")
GYRO_CSV = Path(r"C:\Users\HP\Desktop\MAIN MODEL\pretrain datasets\Gyroscope.csv")
OUTPUT_CSV = WORKDIR / "merged_synchronized_50hz.csv"
OUTPUT_PLOT = WORKDIR / "synchronization_quality.png"
TARGET_HZ = 50.0


def load_sensor_csv(path: Path, prefixes: tuple[str, str, str]) -> pd.DataFrame:
    """Load either timestamp/axis CSVs or Timestamp/Milliseconds/X/Y/Z exports."""
    df = pd.read_csv(path)
    lower_to_original = {col.lower(): col for col in df.columns}

    if {"timestamp", *prefixes}.issubset(lower_to_original):
        time_col = lower_to_original["timestamp"]
        axis_cols = [lower_to_original[prefix] for prefix in prefixes]
        out = df[[time_col, *axis_cols]].copy()
        out.columns = ["time_s", *prefixes]
        out["time_s"] = pd.to_numeric(out["time_s"], errors="coerce") / 1000.0
    elif {"milliseconds", "x", "y", "z"}.issubset(lower_to_original):
        ms_col = lower_to_original["milliseconds"]
        axis_cols = [lower_to_original[axis] for axis in ("x", "y", "z")]
        out = df[[ms_col, *axis_cols]].copy()
        out.columns = ["time_s", *prefixes]
        out["time_s"] = pd.to_numeric(out["time_s"], errors="coerce") / 1000.0
    else:
        raise ValueError(
            f"{path.name} must contain timestamp + axes or Timestamp/Milliseconds/X/Y/Z columns"
        )

    out = out.apply(pd.to_numeric, errors="coerce")
    out = out.dropna().drop_duplicates("time_s").sort_values("time_s")
    return out


def resample_to_timeline(df: pd.DataFrame, timeline: np.ndarray) -> pd.DataFrame:
    values = {"time_s": timeline}
    for col in df.columns:
        if col == "time_s":
            continue
        values[col] = np.interp(timeline, df["time_s"].to_numpy(), df[col].to_numpy())
    return pd.DataFrame(values)


def plot_sync_quality(
    accel: pd.DataFrame,
    gyro: pd.DataFrame,
    merged: pd.DataFrame,
    output_plot: Path,
) -> None:
    import matplotlib.pyplot as plt

    fig, axes = plt.subplots(3, 1, figsize=(13, 9), sharex=False)

    axes[0].plot(np.diff(accel["time_s"]), label="Accelerometer raw dt", alpha=0.8)
    axes[0].plot(np.diff(gyro["time_s"]), label="Gyroscope raw dt", alpha=0.8)
    axes[0].axhline(1 / TARGET_HZ, color="black", linestyle="--", linewidth=1, label="50 Hz target")
    axes[0].set_title("Raw sample spacing")
    axes[0].set_ylabel("Seconds")
    axes[0].legend(loc="upper right")
    axes[0].grid(True, alpha=0.25)

    axes[1].plot(merged["time_s"], merged["ax"], label="ax")
    axes[1].plot(merged["time_s"], merged["gx"], label="gx", alpha=0.8)
    axes[1].set_title("Synchronized streams on common 50 Hz timeline")
    axes[1].set_ylabel("Signal value")
    axes[1].legend(loc="upper right")
    axes[1].grid(True, alpha=0.25)

    merged_dt = np.diff(merged["time_s"])
    axes[2].plot(merged["time_s"].iloc[1:], merged_dt)
    axes[2].axhline(1 / TARGET_HZ, color="black", linestyle="--", linewidth=1)
    axes[2].set_title("Merged dataframe sample spacing")
    axes[2].set_xlabel("Time (s)")
    axes[2].set_ylabel("Seconds")
    axes[2].grid(True, alpha=0.25)

    fig.tight_layout()
    fig.savefig(output_plot, dpi=160)
    plt.close(fig)


def main() -> None:
    accel = load_sensor_csv(ACCEL_CSV, ("ax", "ay", "az"))
    gyro = load_sensor_csv(GYRO_CSV, ("gx", "gy", "gz"))

    start_s = max(accel["time_s"].min(), gyro["time_s"].min())
    end_s = min(accel["time_s"].max(), gyro["time_s"].max())
    step_s = 1.0 / TARGET_HZ
    timeline = np.arange(start_s, end_s + step_s / 2, step_s)

    accel_sync = resample_to_timeline(accel, timeline)
    gyro_sync = resample_to_timeline(gyro, timeline)
    merged = accel_sync.merge(gyro_sync, on="time_s", how="inner")

    merged.to_csv(OUTPUT_CSV, index=False)
    try:
        plot_sync_quality(accel, gyro, merged, OUTPUT_PLOT)
        print(f"Saved sync-quality plot: {OUTPUT_PLOT}")
    except ModuleNotFoundError as exc:
        if exc.name != "matplotlib":
            raise
        print("Skipped sync-quality plot because matplotlib is not installed in this Python runtime.")

    print(f"Saved merged CSV: {OUTPUT_CSV}")
    print(f"Rows: {len(merged):,}")
    print(f"Time range: {merged['time_s'].iloc[0]:.3f}s to {merged['time_s'].iloc[-1]:.3f}s")


if __name__ == "__main__":
    main()
