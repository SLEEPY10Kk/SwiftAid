# Flutter IMU Crash Client

Flutter client that collects accelerometer and gyroscope readings, builds 200-sample windows with 50% overlap, sends them to the FastAPI backend, and displays anomaly/crash alerts.

## Dependencies

```bash
flutter pub get
```

## Backend URL

The code defaults to:

```dart
CrashDetectionApi(baseUrl: 'http://10.0.2.2:8000')
```

Use this for an Android emulator when FastAPI runs on your computer.

For a physical phone, replace it with your computer's LAN IP:

```dart
CrashDetectionApi(baseUrl: 'http://192.168.1.10:8000')
```

Run FastAPI with:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## JSON Sent To Backend

```json
{
  "samples": [
    {"ax": 0.1, "ay": 9.7, "az": 0.2, "gx": 0.01, "gy": 0.02, "gz": 0.01}
  ]
}
```

## Notes

- Sensor streams are sampled onto a shared 50 Hz timer.
- Each request sends one `[200, 6]` IMU window.
- Overlap is 50%, so a new request is sent every 100 samples.
- Network timeouts and backend errors are shown in the UI.
- The app fetches `/config` when monitoring starts.
- The app listens to `/ws/config` for server-pushed threshold/model updates.
- After each prediction, the app uploads the scored window to `/windows` so the backend can retrain later.
