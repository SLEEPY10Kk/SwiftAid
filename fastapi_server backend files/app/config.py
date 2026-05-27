from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import os


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CHECKPOINT_PATH = PROJECT_ROOT / "lstm_autoencoder_outputs" / "best_lstm_autoencoder.pt"
BACKEND_ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Settings:
    model_path: Path = Path(os.getenv("IMU_MODEL_PATH", DEFAULT_CHECKPOINT_PATH))
    threshold: float = float(os.getenv("IMU_ANOMALY_THRESHOLD", "0.05"))
    timesteps: int = int(os.getenv("IMU_TIMESTEPS", "200"))
    features: tuple[str, ...] = ("ax", "ay", "az", "gx", "gy", "gz")
    hidden_size: int = int(os.getenv("IMU_HIDDEN_SIZE", "128"))
    latent_size: int = int(os.getenv("IMU_LATENT_SIZE", "32"))
    num_layers: int = int(os.getenv("IMU_NUM_LAYERS", "1"))
    dropout: float = float(os.getenv("IMU_DROPOUT", "0.0"))
    data_dir: Path = Path(os.getenv("IMU_DATA_DIR", BACKEND_ROOT / "data"))
    registry_dir: Path = Path(os.getenv("IMU_REGISTRY_DIR", BACKEND_ROOT / "model_registry"))
    retrain_interval_minutes: int = int(os.getenv("IMU_RETRAIN_INTERVAL_MINUTES", "1440"))
    min_retrain_windows: int = int(os.getenv("IMU_MIN_RETRAIN_WINDOWS", "500"))
    threshold_percentile: float = float(os.getenv("IMU_THRESHOLD_PERCENTILE", "99.0"))
    min_threshold: float = float(os.getenv("IMU_MIN_THRESHOLD", "0.000001"))
    max_threshold: float = float(os.getenv("IMU_MAX_THRESHOLD", "10.0"))
    max_threshold_change_ratio: float = float(os.getenv("IMU_MAX_THRESHOLD_CHANGE_RATIO", "0.50"))
    max_validation_loss_increase_ratio: float = float(
        os.getenv("IMU_MAX_VALIDATION_LOSS_INCREASE_RATIO", "0.10")
    )
    max_epochs: int = int(os.getenv("IMU_RETRAIN_MAX_EPOCHS", "30"))
    batch_size: int = int(os.getenv("IMU_BATCH_SIZE", "64"))
    learning_rate: float = float(os.getenv("IMU_LEARNING_RATE", "0.001"))
    patience: int = int(os.getenv("IMU_EARLY_STOP_PATIENCE", "6"))


settings = Settings()
