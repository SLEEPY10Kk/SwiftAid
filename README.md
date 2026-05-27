# IMU Crash Detection System

Production-style crash detection pipeline using smartphone accelerometer and gyroscope time-series data.

## Key Features 
- **Hybrid anomaly + supervised pipeline**: an LSTM autoencoder first detects unusual IMU motion, then a supervised crash confirmation classifier decides whether the anomaly is truly crash-like.
- **Normal-only representation learning**: the autoencoder is trained only on normal driving windows, so crashes and risky maneuvers appear as reconstruction anomalies.
- **Realistic synthetic crash generation**: supports frontal collision, side collision, rollover-like events, and abrupt stop impacts with acceleration impulses, gyroscope spikes, deceleration, damping, and post-impact oscillations.
- **Phone-robust training augmentations**: random 3D rotation, Gaussian noise, magnitude scaling, time warping, sensor dropout, and window slicing help handle phone orientation changes, sensor noise, pocket/holder placement changes, and real mobile collection artifacts.
- **Crash confirmation classifier**: Random Forest, optional XGBoost, and lightweight MLP models compare anomaly candidates using engineered IMU features.
- **False-positive control**: classifier threshold tuning is included to reduce false alarms from aggressive braking, hard turns, or risky but non-crash behavior.
- **Offline-safe learning loop**: confirmed crashes are saved for later review and classifier retraining, but are explicitly excluded from autoencoder training so the normal-only model is not contaminated.
- **Explainability outputs**: reconstruction profiles, per-feature reconstruction errors, feature importance, PCA latent visualizations, metrics, confusion matrices, and prediction CSVs are saved.
- **Deployment-ready architecture**: designed for a Flutter mobile client sending IMU windows to a FastAPI backend that returns anomaly confidence and crash probability.

---

## Project Overview

This project implements a hybrid mobile + backend architecture for vehicle crash detection using smartphone IMU data.

The system learns normal driving behavior using an LSTM autoencoder trained on synchronized accelerometer and gyroscope streams. During live deployment, the Flutter mobile app continuously collects IMU data, sends fixed-length sensor windows to a FastAPI backend, and the backend runs a two-stage decision pipeline:

```text
LSTM autoencoder anomaly detection
        |
        v
Candidate anomaly
        |
        v
Supervised crash confirmation classifier
        |
        v
Crash probability + confidence output
```

The project also contains offline preprocessing pipelines, synthetic crash generation, anomaly evaluation tools, adaptive threshold experimentation, supervised crash classification modules, and event logging for confirmed crash candidates.

---

## System Architecture

```text
Phone IMU Sensors
        |
        v
Flutter Mobile Client
        |
        v
FastAPI Backend API
        |
        v
Window Validation + Normalization
        |
        v
LSTM Autoencoder
        |
        v
Reconstruction Error + Anomaly Score
        |
        v
Candidate Anomaly Gate
        |
        v
Feature Engineering
        |
        v
Crash Confirmation Classifier
        |
        v
Crash Probability Output
        |
        v
Confirmed Event Logging For Offline Review
```

---

## Machine Learning Pipeline

```text
Raw IMU Data
    |
    v
Sensor Synchronization
    |
    v
Sliding Window Generation
    |
    v
Normalization
    |
    v
Normal-Only LSTM Autoencoder Training
    |
    v
Synthetic Crash Generation
    |
    v
Anomaly Evaluation + Threshold Calibration
    |
    v
Feature Engineering
    |
    v
Supervised Crash Confirmation Training
    |
    v
Gated Deployment Inference
    |
    v
Offline Event Review + Version Updates
```

---

## Repository Structure

```text
imu-crash-detection/
|
|-- README.md
|-- requirements.txt
|-- render.yaml
|
|-- configs/
|   `-- pipeline_config.json
|
|-- imu_crash_pipeline/
|   |-- config.py
|   |-- utils.py
|   |-- model.py
|   |-- synthetic.py
|   |-- anomaly.py
|   |-- features.py
|   |-- classifier.py
|   `-- events.py
|
|-- scripts/
|   |-- run_full_pipeline.py
|   |-- run_stage2_generate_crashes.py
|   |-- run_stage3_anomaly_evaluation.py
|   |-- run_stage4_confirmation_classifier.py
|   `-- run_confirm_anomaly_candidates.py
|
|-- datasets/
|   |-- raw/
|   |-- synchronized/
|   |-- windowed/
|   |-- normalized/
|   `-- synthetic/
|
|-- preprocessing/
|   |-- sync_sensors.py
|   |-- create_imu_windows.py
|   `-- normalize_imu_windows.py
|
|-- augmentation/
|   |-- synthetic_imu_crash_anomalies.py
|   `-- crash_generator.py
|
|-- models/
|   |-- autoencoder/
|   |-- classifiers/
|   `-- checkpoints/
|
|-- outputs/
|   |-- stage2_synthetic_crashes/
|   |-- stage3_anomaly_evaluation/
|   |-- stage4_confirmation_classifier/
|   |-- confirmed_crash_events/
|   |-- plots/
|   |-- metrics/
|   |-- reports/
|   `-- predictions/
|
|-- fastapi_imu_backend/
|   |-- app/
|   |-- models/
|   |-- requirements.txt
|   `-- Dockerfile
|
|-- flutter_imu_client/
|   |-- lib/
|   |-- android/
|   |-- ios/
|   `-- pubspec.yaml
|
`-- docs/
```

---

## Input Data Format

Expected synchronized IMU columns:

```text
timestamp
ax
ay
az
gx
gy
gz
```

Model input shape:

```python
[num_windows, timesteps, features]
```

Feature order:

```text
ax, ay, az, gx, gy, gz
```

Typical window configuration:

```text
sample rate: 50 Hz
window length: 200 timesteps
overlap: 50%
```

---

## Core Components

### 1. Sensor Synchronization

`sync_sensors.py`

Synchronizes accelerometer and gyroscope streams using timestamp interpolation, uniform resampling, and 50 Hz alignment.

Output:

```text
Synchronized IMU dataframe
```

### 2. Sliding Window Generation

`create_imu_windows.py`

Creates fixed-length temporal windows for sequential deep learning.

Output:

```python
[num_windows, timesteps, 6]
```

### 3. Normalization

`normalize_imu_windows.py`

Performs z-score normalization, persists the scaler, and ensures train/test consistency.

Saved artifacts:

```text
scaler.pkl
imu_zscore_stats.npz
train_windows_zscore.npy
val_windows_zscore.npy
test_windows_zscore.npy
```

### 4. Robustness Augmentation

`lstm_imu_autoencoder.py`

The autoencoder training pipeline includes augmentation strategies designed for real phone IMU data:

- random 3D rotation for phone orientation invariance,
- Gaussian noise for sensor noise robustness,
- magnitude scaling for different mounting conditions,
- time warping for speed/timing variation,
- sensor dropout for temporary sensor glitches,
- window slicing for temporal robustness.

These augmentations help the model avoid overfitting to one phone position, one driver, or one clean sensor profile.

### 5. Baseline Statistical Anomaly Detection

`imu_crash_anomaly_baseline.py`

Implements simple baselines:

- z-score anomaly detection,
- magnitude thresholding,
- rolling statistics.

Used for debugging, threshold validation, and baseline comparisons.

### 6. Synthetic Crash Generation

`imu_crash_pipeline/synthetic.py`

Generates realistic crash-like IMU sequences from normal driving windows.

Supported crash types:

- frontal collision,
- side collision,
- rollover-like event,
- abrupt stop impact.

Crash generation includes:

- sudden acceleration spikes,
- abrupt deceleration,
- violent gyroscope spikes,
- short-duration impact impulse,
- post-impact oscillations,
- damping after impact,
- configurable severity levels,
- crash labels,
- anomaly metadata,
- severity metadata.

Outputs:

```text
outputs/stage2_synthetic_crashes/synthetic_crash_windows.npy
outputs/stage2_synthetic_crashes/synthetic_crash_labels.npy
outputs/stage2_synthetic_crashes/synthetic_crash_metadata.csv
outputs/stage2_synthetic_crashes/plots/
```

### 7. LSTM Autoencoder

`imu_crash_pipeline/model.py`

Learns normal driving behavior using sequence reconstruction.

Training objective:

```text
Minimize reconstruction error on normal driving windows only
```

Inference behavior:

```text
low reconstruction error  -> normal
high reconstruction error -> candidate anomaly
```

The autoencoder is intentionally not trained on crashes. Confirmed crash events are saved for classifier retraining and evaluation, not for normal-only autoencoder training.

### 8. Anomaly Evaluation

`imu_crash_pipeline/anomaly.py`

Runs the trained LSTM autoencoder on normal, aggressive/risky, and synthetic crash windows.

Computes:

- reconstruction error,
- per-feature reconstruction error,
- temporal reconstruction profiles,
- peak temporal error,
- impact-aware anomaly score,
- latent embeddings.

Threshold strategies:

- percentile thresholding,
- mean + standard deviation,
- adaptive robust threshold estimation.

Visualizations:

- reconstruction error distributions,
- input vs reconstruction plots,
- PCA latent embedding separation.

Outputs:

```text
outputs/stage3_anomaly_evaluation/anomaly_scores.csv
outputs/stage3_anomaly_evaluation/thresholds.json
outputs/stage3_anomaly_evaluation/anomaly_metrics.json
outputs/stage3_anomaly_evaluation/latent_pca_coordinates.csv
outputs/stage3_anomaly_evaluation/plots/
```

### 9. Feature Engineering

`imu_crash_pipeline/features.py`

Extracts crash-related features from IMU windows:

- reconstruction error,
- acceleration magnitude,
- gyroscope magnitude,
- jerk,
- impact duration,
- peak acceleration,
- rotational energy,
- per-axis statistical features,
- per-feature reconstruction errors.

These features are used by the supervised confirmation layer to distinguish actual crash-like impacts from hard braking, risky driving, or phone movement.

### 10. Crash Confirmation Classifier

`imu_crash_pipeline/classifier.py`

Second-stage supervised classifier that runs after anomaly detection.

Input:

```text
Only anomaly candidate windows flagged by the LSTM autoencoder
```

Output:

```text
crash_probability
crash_confidence
confirmed_crash
```

Supported models:

- Random Forest,
- XGBoost when installed,
- lightweight MLP.

The classifier includes probability threshold tuning to reduce false positives. It also saves feature importance for explainability.

Outputs:

```text
outputs/stage4_confirmation_classifier/random_forest.joblib
outputs/stage4_confirmation_classifier/mlp.joblib
outputs/stage4_confirmation_classifier/classifier_report.csv
outputs/stage4_confirmation_classifier/classifier_predictions.csv
outputs/stage4_confirmation_classifier/random_forest_feature_importance.csv
outputs/stage4_confirmation_classifier/deployment_manifest.json
```

### 11. Confirmed Crash Event Logging

`imu_crash_pipeline/events.py`

Confirmed crashes are saved for offline review and future model versioning.

Each event stores:

- event ID,
- timestamp,
- model version,
- source window index,
- saved IMU window path,
- anomaly score,
- crash probability,
- classifier threshold,
- human review status,
- offline training flags.

Important policy:

```text
use_for_autoencoder_training = False
use_for_classifier_retraining = True
```

This prevents crash data from contaminating the normal-only autoencoder while still allowing the supervised classifier to improve over time.

### 12. FastAPI Backend

`fastapi_imu_backend/`

Production inference server responsibilities:

- receive IMU windows,
- normalize and validate inputs,
- run LSTM autoencoder anomaly detection,
- pass candidate anomalies to the crash classifier,
- return anomaly confidence and crash probability,
- store inference logs,
- store updated threshold versions,
- provide newer threshold/model metadata to mobile clients.

### 13. Flutter Mobile Client

`flutter_imu_client/`

Mobile application responsibilities:

- collect accelerometer and gyroscope data,
- buffer synchronized IMU windows,
- send windows to backend,
- display anomaly/crash status,
- support future alert or emergency-contact workflows.

---

## Live Inference Logic

The supervised classifier should not run on every normal window in production. It runs only after the LSTM autoencoder flags a candidate anomaly.

```python
if anomaly_score >= anomaly_threshold:
    features = extract_window_features(window, reconstruction_error, ...)
    crash_probability = classifier.predict_proba(features)[0, 1]

    if crash_probability >= crash_threshold:
        decision = "confirmed_crash"
        save_confirmed_crash_event(...)
    else:
        decision = "non_crash_anomaly"
else:
    decision = "normal"
```

This design reduces false positives and makes the supervised layer focus on the hardest decision: crash vs. non-crash anomaly.

---

## Offline Update Strategy

Training and evaluation remain offline. Live deployment should not retrain automatically.

Recommended update loop:

```text
Collect new normal driving data
Collect flagged anomaly events
Collect confirmed crash / false alarm labels
        |
        v
Train or update autoencoder on normal-only data
        |
        v
Recompute anomaly thresholds
        |
        v
Evaluate against aggressive/risky/crash examples
        |
        v
Retrain confirmation classifier using labeled examples
        |
        v
Save new versioned model + thresholds + classifier
```

Recommended versioned artifact layout:

```text
model_version/
  autoencoder.pt
  anomaly_thresholds.json
  confirmation_classifier.joblib
  classifier_threshold.json
  scaler.pkl
  feature_columns.json
  evaluation_metrics.json
  metadata.json
```

---

## Installation

### Clone Repository

```bash
git clone <repository-url>
cd imu-crash-detection
```

### Create Virtual Environment

Windows:

```bash
python -m venv venv
venv\Scripts\activate
```

Linux/macOS:

```bash
python3 -m venv venv
source venv/bin/activate
```

### Install Dependencies

```bash
pip install -r requirements.txt
```

---

## Run Offline ML Pipeline

Run the stage 2-4 pipeline:

```bash
python scripts/run_full_pipeline.py --config configs/pipeline_config.json
```

Run individual stages:

```bash
python scripts/run_stage2_generate_crashes.py --config configs/pipeline_config.json
python scripts/run_stage3_anomaly_evaluation.py --config configs/pipeline_config.json
python scripts/run_stage4_confirmation_classifier.py --config configs/pipeline_config.json
```

Run gated candidate confirmation:

```bash
python scripts/run_confirm_anomaly_candidates.py --config configs/pipeline_config.json --windows path/to/live_or_candidate_windows.npy
```

---

## Example API Workflow

Request:

```json
{
  "imu_window": [[...]]
}
```

Response:

```json
{
  "anomaly_score": 0.82,
  "anomaly_confidence": 0.87,
  "crash_probability": 0.91,
  "prediction": "crash"
}
```

---

## FastAPI Backend Deployment

Local development:

```bash
cd fastapi_imu_backend
uvicorn app.main:app --reload
```

Backend URL:

```text
http://127.0.0.1:8000
```

Recommended cloud deployment target:

```text
FastAPI inference backend only
```

Training, threshold calibration, synthetic crash generation, and evaluation should remain offline.

---

## Flutter App

Run mobile app:

```bash
cd flutter_imu_client
flutter pub get
flutter run
```

---

## Datasets

Recommended dataset categories:

- normal driving,
- aggressive driving,
- risky driving,
- synthetic crash events,
- confirmed crash candidates after human review.

Large `.npy`, `.csv`, `.pt`, `.joblib`, and raw sensor files should usually be stored outside Git or with Git LFS.

---

## Research And Technical Concepts

This project combines:

- time-series anomaly detection,
- accelerometer + gyroscope sensor fusion,
- IMU signal processing,
- sequence reconstruction with LSTM autoencoders,
- synthetic data generation,
- supervised crash classification,
- adaptive thresholding,
- feature explainability,
- offline model versioning,
- mobile/backend deployment.

---

## Future Improvements

Potential future extensions:

- real crash dataset validation,
- GPS speed and location fusion,
- audio crash confirmation,
- phone orientation calibration,
- SimSiam or contrastive pretraining,
- online drift monitoring,
- federated learning,
- edge inference optimization,
- emergency-contact alert workflow,
- user cancellation countdown after crash confirmation.

---

## License

This repository is intended for educational use, research, hackathon development, and experimentation.
