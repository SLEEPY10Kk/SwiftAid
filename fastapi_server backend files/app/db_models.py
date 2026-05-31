from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from .database import Base


class IMUWindowRecord(Base):
    __tablename__ = "imu_windows"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    window_id: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    device_id: Mapped[str] = mapped_column(String(128), index=True)
    storage_path: Mapped[str] = mapped_column(Text)
    num_samples: Mapped[int] = mapped_column(Integer)

    model_version: Mapped[str | None] = mapped_column(String(128), nullable=True)
    anomaly_score: Mapped[float | None] = mapped_column(Float, nullable=True)
    predicted_anomaly: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    user_confirmed_crash: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    metadata_json: Mapped[str | None] = mapped_column(Text, nullable=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
    )


class ModelVersionRecord(Base):
    __tablename__ = "model_versions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    version: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    model_path: Mapped[str] = mapped_column(Text)

    threshold: Mapped[float] = mapped_column(Float)
    threshold_method: Mapped[str] = mapped_column(Text)
    validation_loss: Mapped[float] = mapped_column(Float)
    train_window_count: Mapped[int] = mapped_column(Integer)
    config_json: Mapped[str | None] = mapped_column(Text, nullable=True)

    is_active: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
    )
