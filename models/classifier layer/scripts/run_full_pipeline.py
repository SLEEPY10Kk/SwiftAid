from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scripts.run_stage2_generate_crashes import main as run_stage2
from scripts.run_stage3_anomaly_evaluation import main as run_stage3
from scripts.run_stage4_confirmation_classifier import main as run_stage4


def main() -> None:
    parser = argparse.ArgumentParser(description="Run stages 2-4 for the IMU crash detection pipeline.")
    parser.add_argument("--config", default="configs/pipeline_config.json")
    args = parser.parse_args()

    import sys

    old_argv = sys.argv[:]
    try:
        sys.argv = [old_argv[0], "--config", args.config]
        run_stage2()
        run_stage3()
        run_stage4()
    finally:
        sys.argv = old_argv


if __name__ == "__main__":
    main()
