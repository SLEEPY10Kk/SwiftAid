from __future__ import annotations

from datetime import datetime, timezone
import json
from pathlib import Path
from uuid import uuid4

import torch
from sqlalchemy import select, update

from .database import SessionLocal, database_enabled
from .db_models import ModelVersionRecord


class ModelRegistry:
    """Simple file-backed model registry with immutable versions."""

    def __init__(self, registry_dir: Path) -> None:
        self.registry_dir = registry_dir
        self.versions_dir = registry_dir / "versions"
        self.active_path = registry_dir / "active_model.json"
        self.versions_dir.mkdir(parents=True, exist_ok=True)

    def get_active(self) -> dict | None:
        if database_enabled():
            assert SessionLocal is not None
            with SessionLocal() as session:
                record = session.scalars(
                    select(ModelVersionRecord).where(ModelVersionRecord.is_active.is_(True))
                ).first()
                return model_record_to_metadata(record) if record else None

        if not self.active_path.exists():
            return None
        return json.loads(self.active_path.read_text(encoding="utf-8"))

    def list_versions(self) -> list[dict]:
        if database_enabled():
            assert SessionLocal is not None
            with SessionLocal() as session:
                records = session.scalars(select(ModelVersionRecord).order_by(ModelVersionRecord.created_at)).all()
                return [model_record_to_metadata(record) for record in records]

        versions = []
        for metadata_path in sorted(self.versions_dir.glob("*/metadata.json")):
            versions.append(json.loads(metadata_path.read_text(encoding="utf-8")))
        active = self.get_active()
        active_version = active["version"] if active else None
        for version in versions:
            version["is_active"] = version["version"] == active_version
        return versions

    def save_version(
        self,
        model: torch.nn.Module,
        threshold: float,
        threshold_method: str,
        validation_loss: float,
        train_window_count: int,
        config: dict,
        activate: bool = True,
    ) -> dict:
        version = datetime.now(timezone.utc).strftime("v%Y%m%d%H%M%S") + f"_{uuid4().hex[:8]}"
        version_dir = self.versions_dir / version
        version_dir.mkdir(parents=True, exist_ok=False)

        model_path = version_dir / "model.pt"
        metadata_path = version_dir / "metadata.json"

        torch.save(
            {
                "model_state_dict": model.state_dict(),
                "config": config,
                "threshold": threshold,
                "threshold_method": threshold_method,
            },
            model_path,
        )

        metadata = {
            "version": version,
            "model_path": str(model_path),
            "threshold": float(threshold),
            "threshold_method": threshold_method,
            "validation_loss": float(validation_loss),
            "train_window_count": int(train_window_count),
            "created_at": datetime.now(timezone.utc).isoformat(),
            "config": config,
            "is_active": activate,
        }
        metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")

        if database_enabled():
            assert SessionLocal is not None
            with SessionLocal() as session:
                if activate:
                    session.execute(update(ModelVersionRecord).values(is_active=False))
                session.add(
                    ModelVersionRecord(
                        version=version,
                        model_path=str(model_path),
                        threshold=float(threshold),
                        threshold_method=threshold_method,
                        validation_loss=float(validation_loss),
                        train_window_count=int(train_window_count),
                        config_json=json.dumps(config),
                        is_active=activate,
                    )
                )
                session.commit()

        if activate:
            self.promote(version)
        return metadata

    def promote(self, version: str) -> dict:
        if database_enabled():
            assert SessionLocal is not None
            with SessionLocal() as session:
                record = session.scalars(
                    select(ModelVersionRecord).where(ModelVersionRecord.version == version)
                ).first()
                if record is None:
                    raise FileNotFoundError(f"Model version not found: {version}")
                session.execute(update(ModelVersionRecord).values(is_active=False))
                record.is_active = True
                session.commit()
                session.refresh(record)
                metadata = model_record_to_metadata(record)
                self.active_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
                return metadata

        metadata_path = self.versions_dir / version / "metadata.json"
        if not metadata_path.exists():
            raise FileNotFoundError(f"Model version not found: {version}")

        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        metadata["is_active"] = True
        self.active_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
        return metadata


def model_record_to_metadata(record: ModelVersionRecord) -> dict:
    return {
        "version": record.version,
        "model_path": record.model_path,
        "threshold": float(record.threshold),
        "threshold_method": record.threshold_method,
        "validation_loss": float(record.validation_loss),
        "train_window_count": int(record.train_window_count),
        "created_at": record.created_at.isoformat(),
        "config": json.loads(record.config_json or "{}"),
        "is_active": bool(record.is_active),
    }
