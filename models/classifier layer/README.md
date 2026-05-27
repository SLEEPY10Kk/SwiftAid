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

For production-style gated confirmation, run the classifier only on windows already flagged by the LSTM autoencoder:

```powershell
C:\Users\HP\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/run_confirm_anomaly_candidates.py --windows path\to\candidate_or_live_windows.npy
```

Confirmed crashes are saved as offline review/retraining events. They are marked `use_for_autoencoder_training=False` because the autoencoder must remain normal-only, and `use_for_classifier_retraining=True` because confirmed/false-alarm labels improve the supervised layer.

## Outputs

- `outputs/stage2_synthetic_crashes`: synthetic crash windows, labels, metadata, and example plots.
- `outputs/stage3_anomaly_evaluation`: reconstruction scores, thresholds, temporal profiles, metrics, and PCA/error plots.
- `outputs/stage4_confirmation_classifier`: engineered features, trained classifier checkpoints, reports, predictions, feature importances, and comparison plots.
- `outputs/confirmed_crash_events`: confirmed crash windows and event log for later human review, threshold calibration, and offline classifier retraining.

If real aggressive/risky window files are available, set their paths in `configs/pipeline_config.json`. When absent, the evaluation creates deterministic risky driving surrogates from normal windows for a complete smoke-testable pipeline.
