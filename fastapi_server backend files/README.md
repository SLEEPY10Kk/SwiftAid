# Phone IMU Crash Detection API

FastAPI backend for LSTM-autoencoder based anomaly detection on phone IMU windows.

## Run

```bash
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

By default, the API loads:

```text
../lstm_autoencoder_outputs/best_lstm_autoencoder.pt
```

Override paths or model settings with environment variables:

```bash
set IMU_MODEL_PATH=C:\path\to\best_lstm_autoencoder.pt
set IMU_ANOMALY_THRESHOLD=0.05
set IMU_TIMESTEPS=200
```

## Endpoints

### `GET /health`

Returns model loading status.

### `GET /config`

Returns the active model version, threshold, feature order, and window size.
The Flutter app should call this on startup and update its local threshold.

```json
{
  "model_version": "v20260520170000_ab12cd34",
  "threshold": 0.0482,
  "threshold_method": "max(mean+3std=0.041, p99=0.0482)",
  "timesteps": 200,
  "sample_rate_hz": 50.0,
  "feature_order": ["ax", "ay", "az", "gx", "gy", "gz"],
  "updated_at": "2026-05-20T11:30:00+00:00"
}
```

### `POST /windows`

Stores uploaded IMU windows for adaptive retraining.

```json
{
  "device_id": "phone_001",
  "samples": [
    {"ax": 0.1, "ay": 9.7, "az": 0.2, "gx": 0.01, "gy": 0.02, "gz": 0.01}
  ],
  "model_version": "v20260520170000_ab12cd34",
  "anomaly_score": 0.012,
  "predicted_anomaly": false,
  "user_confirmed_crash": false
}
```

### `POST /predict`

Request:

```json
{
  "samples": [
    {"ax": 0.1, "ay": 9.7, "az": 0.2, "gx": 0.01, "gy": 0.02, "gz": 0.01}
  ]
}
```

Response:

```json
{
  "anomaly_score": 0.0123,
  "is_anomaly": false,
  "threshold": 0.05,
  "num_samples": 200,
  "device": "cuda"
}
```

### `POST /admin/retrain`

Manually triggers retraining. The same retraining function also runs periodically according to:

```text
IMU_RETRAIN_INTERVAL_MINUTES
```

### `GET /admin/models`

Lists model versions in the local model registry.

### `POST /admin/models/{version}/promote`

Rolls forward or rolls back to a specific model version.

### `WebSocket /ws/config`

Mobile clients can subscribe here to receive:

```json
{
  "type": "threshold_update",
  "config": {
    "model_version": "v20260520170000_ab12cd34",
    "threshold": 0.0482
  }
}
```

The API trims inputs longer than `IMU_TIMESTEPS` to the most recent samples and pads shorter inputs by repeating the last sample.

## Where To Integrate

The adaptive threshold pipeline is integrated into these files:

```text
app/main.py        API endpoints, scheduler, model activation, config push
app/storage.py     Uploaded IMU window storage
app/training.py    Periodic autoencoder retraining and threshold recomputation
app/registry.py    Versioned model registry and rollback/promotion
app/inference.py   Loads active model and uses active threshold
app/schemas.py     Request/response schemas
```

In the Flutter app, integrate with:

```text
flutter_imu_client/lib/main.dart
```

Use `/config` on startup to fetch the latest threshold. Keep sending windows to `/predict`. Also send accepted windows to `/windows` so the server has data for future retraining.
