# IMU Crash Detection System

crash detection pipeline using:

* Accelerometer + gyroscope sensor fusion
* LSTM autoencoder anomaly detection
* Synthetic crash simulation
* Supervised crash confirmation classifier
* FastAPI backend deployment
* Flutter mobile client

---

# Project Overview

This project implements a hybrid mobile + cloud architecture for vehicle crash detection using smartphone IMU (Inertial Measurement Unit) data.

The system learns normal driving behavior using an LSTM autoencoder trained on synchronized accelerometer and gyroscope streams.

During live deployment:

1. The Flutter mobile app continuously collects phone IMU data.
2. Sensor windows are sent to a FastAPI backend.
3. The backend runs anomaly detection using the trained LSTM autoencoder.
4. Candidate anomalies are passed to a crash confirmation classifier.
5. The system outputs crash probability and anomaly confidence.

The project also contains:

* offline preprocessing pipelines,
* synthetic crash generation,
* anomaly evaluation tools,
* adaptive threshold experimentation,
* supervised crash classification modules.

---

# System Architecture

```text
Phone IMU Sensors
        ↓
Flutter Mobile Client
        ↓
FastAPI Backend API
        ↓
Feature Engineering
        ↓
LSTM Autoencoder
        ↓
Reconstruction Error
        ↓
Anomaly Detection
        ↓
Crash Confirmation Classifier
        ↓
Crash Probability Output
```

---

# Repository Structure

```text
imu-crash-detection/
│
├── README.md
├── requirements.txt
├── render.yaml
│
├── datasets/
│   ├── raw/
│   ├── synchronized/
│   ├── windowed/
│   ├── normalized/
│   └── synthetic/
│
├── preprocessing/
│   ├── sync_sensors.py
│   ├── create_imu_windows.py
│   └── normalize_imu_windows.py
│
├── baseline/
│   └── imu_crash_anomaly_baseline.py
│
├── augmentation/
│   ├── synthetic_imu_crash_anomalies.py
│   └── crash_generator.py
│
├── models/
│   │
│   ├── autoencoder/
│   │   ├── lstm_imu_autoencoder.py
│   │   ├── autoencoder.py
│   │   ├── autoencoder_anomaly_scoring.py
│   │   └── autoencoder.pt
│   │
│   ├── classifiers/
│   │   ├── crash_classifier.py
│   │   ├── feature_engineering.py
│   │   ├── anomaly_evaluator.py
│   │   ├── utils.py
│   │   └── crash_classifier.pkl
│   │
│   └── checkpoints/
│       ├── scaler.pkl
│       ├── threshold.json
│       └── metadata.json
│
├── outputs/
│   ├── plots/
│   ├── metrics/
│   ├── reports/
│   ├── predictions/
│   └── logs/
│
├── scripts/
│   └── run_pipeline.py
│
├── fastapi_imu_backend/
│   ├── app/
│   ├── models/
│   ├── requirements.txt
│   └── Dockerfile
│
├── flutter_imu_client/
│   ├── lib/
│   ├── android/
│   ├── ios/
│   └── pubspec.yaml
│
└── docs/
```

---

# Core Components

## 1. Sensor Synchronization

`sync_sensors.py`

Synchronizes:

* accelerometer streams
* gyroscope streams

using:

* timestamp interpolation
* uniform resampling
* 50 Hz alignment

Output:

```text
Synchronized IMU dataframe
```

---

## 2. Sliding Window Generation

`create_imu_windows.py`

Creates fixed-length temporal windows for sequential deep learning.

Typical configuration:

* 2-second windows
* 50 Hz sampling
* 200 timesteps
* 50% overlap

Output shape:

```python
[num_windows, timesteps, features]
```

Features:

```text
ax, ay, az, gx, gy, gz
```

---

## 3. Normalization

`normalize_imu_windows.py`

Performs:

* z-score normalization
* scaling persistence
* train-test consistency

Saved artifacts:

* scaler.pkl

---

## 4. Baseline Statistical Anomaly Detection

`imu_crash_anomaly_baseline.py`

Implements simple statistical baselines:

* z-score anomaly detection
* magnitude thresholding
* rolling statistics

Used for:

* debugging
* threshold validation
* baseline comparisons

---

## 5. Synthetic Crash Generation

`synthetic_imu_crash_anomalies.py`
`crash_generator.py`

Generates realistic crash-like IMU sequences.

Supported crash types:

* frontal collision
* side collision
* rollover-like event
* abrupt stop impact

Crash generation includes:

* acceleration spikes
* gyroscope spikes
* abrupt deceleration
* oscillatory post-impact motion

Used for:

* evaluation
* classifier training
* anomaly stress testing

---

## 6. LSTM Autoencoder

`lstm_imu_autoencoder.py`

Learns normal driving behavior using sequence reconstruction.

Training objective:

```text
Minimize reconstruction error on normal driving windows
```

Inference:

* low reconstruction error → normal
* high reconstruction error → anomaly

---

## 7. Anomaly Scoring

`autoencoder_anomaly_scoring.py`

Computes:

* reconstruction MSE
* anomaly thresholds
* anomaly scores
* reconstruction visualizations

Threshold strategies:

* percentile thresholding
* sigma thresholding
* adaptive thresholds

---

## 8. Feature Engineering

`feature_engineering.py`

Extracts engineered crash-related features:

* acceleration magnitude
* gyroscope magnitude
* jerk
* rotational energy
* FFT energy
* spectral entropy
* impact duration
* statistical moments

---

## 9. Crash Confirmation Classifier

`crash_classifier.py`

Second-stage supervised classifier.

Input:

* anomaly scores
* engineered IMU features

Output:

* crash probability
* confidence score

Supported models:

* Random Forest
* XGBoost
* MLP

---

## 10. FastAPI Backend

`fastapi_imu_backend/`

Production inference server.

Responsibilities:

* receive IMU windows
* run anomaly detection
* run crash confirmation
* return predictions
* store inference logs
* stores updated retrained threshold versions
* pushes newer threshold updates to mobile phones

---

## 11. Flutter Mobile Client

`flutter_imu_client/`

Mobile application responsible for:

* collecting IMU data
* buffering windows
* sending sensor streams to backend
* displaying anomaly/crash status

---

# Machine Learning Pipeline

```text
Raw IMU Data
    ↓
Sensor Synchronization
    ↓
Sliding Window Generation
    ↓
Normalization
    ↓
LSTM Autoencoder Training
    ↓
Anomaly Scoring
    ↓
Feature Engineering
    ↓
Crash Confirmation Classifier
    ↓
Deployment
```

---

# Datasets

Expected IMU columns:

```text
timestamp
ax
ay
az
gx
gy
gz
```

Supported sources:

* smartphone accelerometer
* smartphone gyroscope
* synchronized IMU logs

Recommended datasets:

* normal driving
* aggressive driving
* risky driving
* synthetic crash events

---

# Installation

## Clone Repository

```bash
git clone <repository-url>
cd imu-crash-detection
```

---

## Create Virtual Environment

### Windows

```bash
python -m venv venv
venv\Scripts\activate
```

### Linux / macOS

```bash
python3 -m venv venv
source venv/bin/activate
```

---

## Install Dependencies

```bash
pip install -r requirements.txt
```

---

# Offline ML Pipeline

Run the complete offline pipeline:

```bash
python scripts/run_pipeline.py
```

Pipeline stages:

1. Synchronize sensors
2. Create IMU windows
3. Normalize windows
4. Generate synthetic crashes
5. Train autoencoder
6. Compute anomaly scores
7. Train crash classifier
8. Save checkpoints

---

# FastAPI Backend Deployment

## Local Development

```bash
cd fastapi_imu_backend
uvicorn app.main:app --reload
```

Backend available at:

```text
http://127.0.0.1:8000
```

---

# Flutter App

## Run Mobile App

```bash
cd flutter_imu_client
flutter pub get
flutter run
```

---

# Example API Workflow

## Request

```json
{
  "imu_window": [[...]]
}
```

## Response

```json
{
  "anomaly_score": 0.82,
  "crash_probability": 0.91,
  "prediction": "crash"
}
```

---

# Deployment

## Recommended Cloud Platform

* Render
* Railway
Recommended deployment target:

```text
FastAPI inference backend only
```

Training and evaluation remain offline.

---

# Research and Technical Concepts

This project combines:

* time-series anomaly detection
* sensor fusion
* IMU signal processing
* sequential deep learning
* supervised crash classification
* synthetic data generation
* adaptive thresholding

---

# Future Improvements

Potential future extensions:

* SimSiam / contrastive pretraining
* online drift monitoring
* self-supervised learning
* federated learning
* phone orientation invariance
* real crash datasets
* GPS fusion
* audio crash confirmation
* edge inference optimization

---

# License

This repository is intended for:

* educational use
* research
* hackathon development
* experimentation

---

