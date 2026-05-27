from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from imu_crash_pipeline.config import load_config
from imu_crash_pipeline.synthetic import generate_synthetic_crashes, plot_synthetic_examples, save_synthetic_outputs
from imu_crash_pipeline.utils import load_windows, setup_logging, set_seed


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate realistic synthetic crash IMU windows.")
    parser.add_argument("--config", default="configs/pipeline_config.json")
    args = parser.parse_args()

    setup_logging()
    cfg = load_config(args.config)
    set_seed(cfg.seed)
    output_dir = cfg.paths.output_dir / "stage2_synthetic_crashes"

    windows = load_windows(cfg.paths.normal_windows, expected_features=cfg.model.input_size)
    synthetic, labels, metadata = generate_synthetic_crashes(
        windows=windows,
        cfg=cfg.synthetic_crash,
        sample_rate_hz=cfg.sample_rate_hz,
        seed=cfg.seed,
    )
    save_synthetic_outputs(synthetic, labels, metadata, output_dir)
    plot_synthetic_examples(windows, synthetic, metadata, output_dir)
    logging.info("Saved %s synthetic crash windows to %s", len(synthetic), output_dir)


if __name__ == "__main__":
    main()
