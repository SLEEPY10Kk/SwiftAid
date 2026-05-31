from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect, status

from .config import settings
from .database import init_db
from .inference import CrashDetectionService, ModelNotLoadedError
from .notifier import ConfigBroadcaster
from .registry import ModelRegistry
from .schemas import (
    ClientConfigResponse,
    IMUInferenceRequest,
    IMUInferenceResponse,
    ModelVersionResponse,
    RetrainResponse,
    WindowUploadRequest,
    WindowUploadResponse,
)
from .storage import SensorWindowStore
from .training import retrain_autoencoder


service = CrashDetectionService(settings)
store = SensorWindowStore(settings.data_dir, settings.features)
registry = ModelRegistry(settings.registry_dir)
broadcaster = ConfigBroadcaster()
retrain_lock = asyncio.Lock()
periodic_retrain_task: asyncio.Task | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global periodic_retrain_task
    init_db()
    try:
        load_active_or_default_model()
    except FileNotFoundError as exc:
        # Keep the API alive so /health can report the missing model cleanly.
        app.state.startup_error = str(exc)
    else:
        app.state.startup_error = None

    periodic_retrain_task = asyncio.create_task(periodic_retraining_loop())
    yield
    if periodic_retrain_task:
        periodic_retrain_task.cancel()


app = FastAPI(
    title="Phone IMU Crash Detection API",
    version="1.0.0",
    description="Detect crash-like anomalies from phone accelerometer and gyroscope windows.",
    lifespan=lifespan,
)


@app.get("/health")
def health() -> dict[str, str | bool | None]:
    return {
        "ok": app.state.startup_error is None,
        "model_loaded": service.model is not None,
        "model_version": service.model_version,
        "threshold": service.threshold,
        "startup_error": app.state.startup_error,
    }


@app.post("/predict", response_model=IMUInferenceResponse)
def predict(request: IMUInferenceRequest) -> dict[str, float | bool | int | str]:
    """
    Receive phone IMU JSON data and return an autoencoder anomaly decision.

    Request body:
        {
          "samples": [
            {"ax": 0.1, "ay": 9.7, "az": 0.2, "gx": 0.01, "gy": 0.02, "gz": 0.01}
          ]
        }
    """
    try:
        return service.predict(request.samples)
    except ModelNotLoadedError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc


@app.post("/windows", response_model=WindowUploadResponse)
def upload_window(request: WindowUploadRequest) -> dict[str, str]:
    """Store uploaded IMU windows for later adaptive retraining."""
    return store.save_upload(request)


@app.get("/config", response_model=ClientConfigResponse)
def get_client_config() -> dict:
    """
    Return active model and threshold metadata for mobile clients.

    Flutter can call this on startup, periodically, or after a WebSocket
    threshold_update event.
    """
    return build_client_config()


@app.websocket("/ws/config")
async def config_updates(websocket: WebSocket) -> None:
    """Push threshold/model-version updates to connected mobile clients."""
    await broadcaster.connect(websocket)
    await websocket.send_json(build_client_config())
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        broadcaster.disconnect(websocket)


@app.post("/admin/retrain", response_model=RetrainResponse)
async def retrain_now() -> dict[str, str]:
    """Manually trigger retraining and activate the new model version if successful."""
    result = await run_retraining_once()
    return {"status": result["status"], "message": result["message"]}


@app.get("/admin/models", response_model=list[ModelVersionResponse])
def list_models() -> list[dict]:
    """List all registered model versions."""
    return registry.list_versions()


@app.post("/admin/models/{version}/promote", response_model=ClientConfigResponse)
async def promote_model(version: str) -> dict:
    """Activate a previous model version and push its threshold to clients."""
    try:
        metadata = registry.promote(version)
        load_model_from_metadata(metadata)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc

    config = build_client_config()
    await broadcaster.broadcast({"type": "threshold_update", "config": config})
    return config


def load_active_or_default_model() -> None:
    active = registry.get_active()
    if active:
        load_model_from_metadata(active)
        return
    service.load_model(settings.model_path, threshold=settings.threshold, model_version=None)


def load_model_from_metadata(metadata: dict) -> None:
    service.load_model(
        model_path=metadata_path(metadata["model_path"]),
        threshold=float(metadata["threshold"]),
        model_version=metadata["version"],
    )


def metadata_path(path: str) -> "Path":
    from pathlib import Path

    return Path(path)


def build_client_config() -> dict:
    active = registry.get_active()
    return {
        "model_version": service.model_version,
        "threshold": service.threshold,
        "threshold_method": active.get("threshold_method", "static") if active else "static",
        "timesteps": settings.timesteps,
        "sample_rate_hz": 50.0,
        "feature_order": list(settings.features),
        "updated_at": active.get("created_at") if active else None,
    }


async def periodic_retraining_loop() -> None:
    while True:
        await asyncio.sleep(settings.retrain_interval_minutes * 60)
        await run_retraining_once()


async def run_retraining_once() -> dict[str, str]:
    async with retrain_lock:
        result = await asyncio.to_thread(
            retrain_autoencoder,
            store,
            registry,
            settings,
            True,
        )
        if result.status == "completed" and result.metadata:
            load_model_from_metadata(result.metadata)
            await broadcaster.broadcast({"type": "threshold_update", "config": build_client_config()})
        return {"status": result.status, "message": result.message}
