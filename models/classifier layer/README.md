# IMU Crash Detection Pipeline — Stages 2–4

Production-quality Python pipeline for phone IMU-based vehicle crash detection.

## Architecture

```
crash_detection/
├── config/
│   └── config.py              ← All hyperparameters, physics constants, paths
├── utils/
│   └── utils.py               ← Seeds, logging, magnitude/jerk helpers
├── augmentation/
│   └── crash_generator.py     ← STAGE 2: Synthetic crash generation
├── models/
│   └── autoencoder.py         ← LSTM Autoencoder definition + loader
├── evaluation/
│   └── anomaly_evaluator.py   ← STAGE 3: Reconstruction error, thresholds, metrics
├── classifiers/
│   ├── feature_engineering.py ← STAGE 4: IMU feature extraction
│   └── crash_classifier.py    ← STAGE 4: RF / XGBoost / MLP classifiers
├── outputs/
│   ├── plots/                 ← All generated figures
│   └── reports/               ← CSV metric reports
├── models/                    ← Saved model checkpoints
└── run_pipeline.py            ← End-to-end runner
```

## Quick Start

```bash
# Install dependencies
pip install torch scikit-learn numpy pandas matplotlib scipy xgboost

# Run with a trained autoencoder checkpoint
python run_pipeline.py --autoencoder models/autoencoder.pt

# Smoke-test without a checkpoint (random model, structure test only)
python run_pipeline.py
```

## Stage 2 — Synthetic Crash Generation

`SyntheticCrashGenerator` produces four crash types at four severity levels:

| Type          | Primary Signal           | Key Physics Feature          |
|---------------|--------------------------|------------------------------|
| Frontal       | −acc_y spike             | Half-sine impulse + crumple  |
| Side          | +acc_x spike             | Yaw gyro spike (gyro_z)      |
| Rollover      | Sustained gyro_x/y       | Continuous roll rotation     |
| Abrupt Stop   | −acc_y only              | Minimal rotation             |

All crashes use: half-sine impact pulse → exponentially decaying oscillation.

Severity levels: `low (0.4×)`, `medium (0.7×)`, `high (1.0×)`, `extreme (1.4×)`.

## Stage 3 — Anomaly Evaluation

Autoencoder trained on normal driving; anomaly score = reconstruction MSE.

Three threshold strategies:
- **Percentile**: `p`-th percentile of normal errors (controls FPR directly)
- **Sigma**: `mean + k·std` of normal errors  
- **Adaptive**: rolling mean + k·std (for online/streaming use)

Outputs: error CSVs, ROC curve, PCA/t-SNE latent space, per-feature error bars.

## Stage 4 — Crash Confirmation Classifier

Second-stage classifier using engineered features:
- Autoencoder reconstruction error (overall + per-feature)
- Peak/mean acceleration and gyroscope magnitudes
- Jerk (d(acc)/dt) — peak, energy
- Impact duration above threshold
- Rotational energy per axis
- Statistical moments (mean, std, skew, kurtosis) per IMU axis
- FFT dominant frequency, spectral entropy, high-frequency energy

Three classifiers: **Random Forest**, **XGBoost**, **MLP (PyTorch)**.

Threshold tuning minimises false positives at configurable FPR target (default 5%).

## Configuration

All parameters in `config/config.py`:
- `CRASH_PHYSICS` — peak g values per crash type
- `CRASH_SEVERITY_LEVELS` — severity scale factors
- `ANOMALY` — threshold percentile, sigma multiplier
- `CLASSIFIER` — model hyperparameters, FP rate target
- `AUTOENCODER` — hidden size, latent size, checkpoint path
