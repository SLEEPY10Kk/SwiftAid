from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any


FEATURE_COLUMNS = ("ax", "ay", "az", "gx", "gy", "gz")


@dataclass(frozen=True)
class ModelConfig:
    input_size: int = 6
    hidden_size: int = 64
    latent_size: int = 16
    num_layers: int = 2
    dropout: float = 0.2
    batch_size: int = 64


@dataclass(frozen=True)
class PathConfig:
    normal_windows: Path
    aggressive_windows: Path | None
    risky_windows: Path | None
    checkpoint: Path
    output_dir: Path
    event_log_dir: Path
    model_version: str


@dataclass(frozen=True)
class SyntheticCrashConfig:
    crash_fraction: float = 0.4
    severity_levels: tuple[str, ...] = ("minor", "moderate", "severe")
    min_impact_duration_s: float = 0.08
    max_impact_duration_s: float = 0.28
    post_impact_duration_s: float = 1.2
    max_delta_per_step: float = 2.75
    sensor_noise_scale: float = 0.012


@dataclass(frozen=True)
class ThresholdConfig:
    percentile: float = 99.0
    std_multiplier: float = 3.0
    adaptive_window: int = 25
    adaptive_z: float = 3.5


@dataclass(frozen=True)
class ClassifierConfig:
    test_size: float = 0.25
    crash_probability_threshold: float = 0.5
    max_false_positive_rate: float = 0.02
    random_forest_estimators: int = 300
    mlp_hidden_layers: tuple[int, ...] = (64, 32)
    mlp_max_iter: int = 500


@dataclass(frozen=True)
class PipelineConfig:
    seed: int
    sample_rate_hz: float
    feature_columns: tuple[str, ...]
    paths: PathConfig
    model: ModelConfig
    synthetic_crash: SyntheticCrashConfig
    thresholds: ThresholdConfig
    classifier: ClassifierConfig


def _optional_path(value: str | None) -> Path | None:
    if not value:
        return None
    return Path(value)


def load_config(path: str | Path = "configs/pipeline_config.json") -> PipelineConfig:
    config_path = Path(path)
    with config_path.open("r", encoding="utf-8") as handle:
        raw: dict[str, Any] = json.load(handle)

    paths = raw["paths"]
    model = raw.get("model", {})
    synthetic = raw.get("synthetic_crash", {})
    thresholds = raw.get("thresholds", {})
    classifier = raw.get("classifier", {})

    return PipelineConfig(
        seed=int(raw.get("seed", 42)),
        sample_rate_hz=float(raw.get("sample_rate_hz", 50.0)),
        feature_columns=tuple(raw.get("feature_columns", FEATURE_COLUMNS)),
        paths=PathConfig(
            normal_windows=Path(paths["normal_windows"]),
            aggressive_windows=_optional_path(paths.get("aggressive_windows")),
            risky_windows=_optional_path(paths.get("risky_windows")),
            checkpoint=Path(paths["checkpoint"]),
            output_dir=Path(paths.get("output_dir", "outputs")),
            event_log_dir=Path(paths.get("event_log_dir", "outputs/confirmed_crash_events")),
            model_version=str(paths.get("model_version", "imu-crash-v1")),
        ),
        model=ModelConfig(**model),
        synthetic_crash=SyntheticCrashConfig(
            crash_fraction=float(synthetic.get("crash_fraction", 0.4)),
            severity_levels=tuple(synthetic.get("severity_levels", ("minor", "moderate", "severe"))),
            min_impact_duration_s=float(synthetic.get("min_impact_duration_s", 0.08)),
            max_impact_duration_s=float(synthetic.get("max_impact_duration_s", 0.28)),
            post_impact_duration_s=float(synthetic.get("post_impact_duration_s", 1.2)),
            max_delta_per_step=float(synthetic.get("max_delta_per_step", 2.75)),
            sensor_noise_scale=float(synthetic.get("sensor_noise_scale", 0.012)),
        ),
        thresholds=ThresholdConfig(**thresholds),
        classifier=ClassifierConfig(
            test_size=float(classifier.get("test_size", 0.25)),
            crash_probability_threshold=float(classifier.get("crash_probability_threshold", 0.5)),
            max_false_positive_rate=float(classifier.get("max_false_positive_rate", 0.02)),
            random_forest_estimators=int(classifier.get("random_forest_estimators", 300)),
            mlp_hidden_layers=tuple(classifier.get("mlp_hidden_layers", (64, 32))),
            mlp_max_iter=int(classifier.get("mlp_max_iter", 500)),
        ),
    )
