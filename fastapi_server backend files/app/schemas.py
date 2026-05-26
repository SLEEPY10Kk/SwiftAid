from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field, field_validator


class IMUSample(BaseModel):
    ax: float
    ay: float
    az: float
    gx: float
    gy: float
    gz: float


class IMUInferenceRequest(BaseModel):
    samples: list[IMUSample] = Field(
        ...,
        description="Continuous IMU samples ordered by time.",
    )

    @field_validator("samples")
    @classmethod
    def samples_must_not_be_empty(cls, value: list[IMUSample]) -> list[IMUSample]:
        if not value:
            raise ValueError("samples must contain at least one IMU sample.")
        return value


class WindowUploadRequest(IMUInferenceRequest):
    device_id: str = Field(..., min_length=1)
    model_version: str | None = None
    anomaly_score: float | None = None
    predicted_anomaly: bool | None = None
    user_confirmed_crash: bool | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class IMUInferenceResponse(BaseModel):
    anomaly_score: float
    is_anomaly: bool
    threshold: float
    num_samples: int
    device: str
    model_version: str | None = None


class WindowUploadResponse(BaseModel):
    window_id: str
    storage_path: str


class ClientConfigResponse(BaseModel):
    model_version: str | None
    threshold: float
    threshold_method: str
    timesteps: int
    sample_rate_hz: float
    feature_order: list[str]
    updated_at: str | None


class RetrainResponse(BaseModel):
    status: str
    message: str


class ModelVersionResponse(BaseModel):
    version: str
    model_path: str
    threshold: float
    threshold_method: str
    validation_loss: float
    train_window_count: int
    created_at: str
    is_active: bool
