# Phone IMU Crash Detection Pipeline

Production-style stages 2-4 for a phone IMU crash detection system using accelerometer and gyroscope windows shaped `[num_windows, timesteps, features]` with features `ax, ay, az, gx, gy, gz`.

The default config points at the latest LSTM autoencoder/checkpoint and normalized windows from the prior IMU workspace:

`C:/Users/HP/Documents/Codex/2026-05-19/files-mentioned-by-the-user-accelerometer/lstm_autoencoder_outputs/best_lstm_autoencoder.pt`

## Run

```powershell
C:\Users\HP\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/run_full_pipeline.py --config configs/pipeline_config.json
```

Or run individual stages:

```powershell
C:\Users\HP\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/run_stage2_generate_crashes.py
C:\Users\HP\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/run_stage3_anomaly_evaluation.py
C:\Users\HP\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/run_stage4_confirmation_classifier.py
```

## Outputs

- `outputs/stage2_synthetic_crashes`: synthetic crash windows, labels, metadata, and example plots.
- `outputs/stage3_anomaly_evaluation`: reconstruction scores, thresholds, temporal profiles, metrics, and PCA/error plots.
- `outputs/stage4_confirmation_classifier`: engineered features, trained classifier checkpoints, reports, predictions, feature importances, and comparison plots.

If real aggressive/risky window files are available, set their paths in `configs/pipeline_config.json`. When absent, the evaluation creates deterministic risky driving surrogates from normal windows for a complete smoke-testable pipeline.
