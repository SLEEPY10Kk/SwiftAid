from __future__ import annotations

from datetime import datetime, timezone
import json
from pathlib import Path
from uuid import uuid4

import numpy as np

from .schemas import WindowUploadRequest


class SensorWindowStore:
    """File-backed storage for uploaded IMU windows and metadata."""

    def __init__(self, data_dir: Path, feature_order: tuple[str, ...]) -> None:
        self.data_dir = data_dir
        self.feature_order = feature_order
        self.windows_dir = data_dir / "windows"
        self.metadata_path = data_dir / "window_metadata.jsonl"
        self.windows_dir.mkdir(parents=True, exist_ok=True)

    def save_upload(self, request: WindowUploadRequest) -> dict[str, str]:
        window_id = uuid4().hex
        created_at = datetime.now(timezone.utc).isoformat()
        date_dir = self.windows_dir / created_at[:10]
        date_dir.mkdir(parents=True, exist_ok=True)
        storage_path = date_dir / f"{window_id}.npy"

        array = np.asarray(
            [[getattr(sample, feature) for feature in self.feature_order] for sample in request.samples],
            dtype=np.float32,
        )
        np.save(storage_path, array)

        metadata = {
            "window_id": window_id,
            "device_id": request.device_id,
            "created_at": created_at,
            "storage_path": str(storage_path),
            "num_samples": len(request.samples),
            "model_version": request.model_version,
            "anomaly_score": request.anomaly_score,
            "predicted_anomaly": request.predicted_anomaly,
            "user_confirmed_crash": request.user_confirmed_crash,
            "metadata": request.metadata,
        }
        with self.metadata_path.open("a", encoding="utf-8") as file:
            file.write(json.dumps(metadata) + "\n")

        return {"window_id": window_id, "storage_path": str(storage_path)}

    def load_metadata(self) -> list[dict]:
        if not self.metadata_path.exists():
            return []
        records = []
        with self.metadata_path.open("r", encoding="utf-8") as file:
            for line in file:
                if line.strip():
                    records.append(json.loads(line))
        return records

    def load_training_windows(self, min_timesteps: int) -> np.ndarray:
        """
        Load trusted normal windows for retraining.

        Confirmed crashes and predicted anomalies are excluded so the
        autoencoder continues to learn normal driving/phone motion.
        """
        arrays = []
        for record in self.load_metadata():
            if record.get("user_confirmed_crash") is True:
                continue
            if record.get("predicted_anomaly") is True:
                continue

            path = Path(record["storage_path"])
            if not path.exists():
                continue
            array = np.load(path).astype(np.float32)
            if array.ndim != 2 or array.shape[1] != len(self.feature_order):
                continue
            if len(array) < min_timesteps:
                continue
            arrays.append(array[-min_timesteps:])

        if not arrays:
            return np.empty((0, min_timesteps, len(self.feature_order)), dtype=np.float32)
        return np.stack(arrays).astype(np.float32)
