from __future__ import annotations

import json
from pathlib import Path

import joblib
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score, precision_recall_curve, precision_score, recall_score
from sklearn.model_selection import train_test_split
from sklearn.neural_network import MLPClassifier
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from .config import ClassifierConfig
from .utils import ensure_dir


def build_labeled_feature_table(group_features: dict[str, pd.DataFrame]) -> pd.DataFrame:
    frames: list[pd.DataFrame] = []
    for group, features in group_features.items():
        frame = features.copy()
        frame["group"] = group
        frame["crash_label"] = int(group == "synthetic_crash")
        frame["risk_label"] = int(group != "normal")
        frames.append(frame)
    return pd.concat(frames, ignore_index=True)


def _classifiers(cfg: ClassifierConfig, seed: int) -> dict[str, object]:
    models: dict[str, object] = {
        "random_forest": RandomForestClassifier(
            n_estimators=cfg.random_forest_estimators,
            class_weight="balanced",
            random_state=seed,
            n_jobs=-1,
        ),
        "mlp": Pipeline(
            [
                ("scaler", StandardScaler()),
                (
                    "mlp",
                    MLPClassifier(
                        hidden_layer_sizes=cfg.mlp_hidden_layers,
                        max_iter=cfg.mlp_max_iter,
                        random_state=seed,
                        early_stopping=True,
                    ),
                ),
            ]
        ),
    }
    try:
        from xgboost import XGBClassifier

        models["xgboost"] = XGBClassifier(
            n_estimators=250,
            max_depth=4,
            learning_rate=0.05,
            subsample=0.85,
            colsample_bytree=0.85,
            eval_metric="logloss",
            random_state=seed,
        )
    except Exception:
        models["xgboost"] = None
    return models


def tune_threshold(y_true: np.ndarray, probabilities: np.ndarray, max_false_positive_rate: float) -> float:
    precision, recall, thresholds = precision_recall_curve(y_true, probabilities)
    best_threshold = 0.5
    best_recall = -1.0
    candidate_thresholds = np.r_[thresholds, 1.0]
    for threshold in candidate_thresholds:
        preds = (probabilities >= threshold).astype(int)
        fp = np.sum((preds == 1) & (y_true == 0))
        tn = np.sum((preds == 0) & (y_true == 0))
        fpr = fp / max(1, fp + tn)
        rec = recall_score(y_true, preds, zero_division=0)
        if fpr <= max_false_positive_rate and rec > best_recall:
            best_recall = rec
            best_threshold = float(threshold)
    return best_threshold


def _probabilities(model: object, x: np.ndarray) -> np.ndarray:
    if hasattr(model, "predict_proba"):
        return model.predict_proba(x)[:, 1]
    decision = model.decision_function(x)
    return 1.0 / (1.0 + np.exp(-decision))


def load_classifier_bundle(path: Path) -> dict[str, object]:
    bundle = joblib.load(path)
    required = {"model", "feature_columns", "threshold"}
    missing = required - set(bundle)
    if missing:
        raise ValueError(f"Classifier bundle is missing required keys: {sorted(missing)}")
    return bundle


def predict_crash_confirmation(feature_table: pd.DataFrame, classifier_bundle: dict[str, object]) -> pd.DataFrame:
    feature_columns = list(classifier_bundle["feature_columns"])
    model = classifier_bundle["model"]
    threshold = float(classifier_bundle["threshold"])
    x = feature_table.reindex(columns=feature_columns, fill_value=0.0)
    x = x.replace([np.inf, -np.inf], np.nan).fillna(0.0).to_numpy(dtype=np.float32)
    probabilities = _probabilities(model, x)
    predictions = (probabilities >= threshold).astype(np.int64)
    output = feature_table.copy()
    output["crash_probability"] = probabilities
    output["crash_confidence"] = probabilities
    output["crash_threshold"] = threshold
    output["confirmed_crash"] = predictions
    return output


def train_confirmation_classifiers(
    feature_table: pd.DataFrame,
    cfg: ClassifierConfig,
    seed: int,
    output_dir: Path,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    output_dir = ensure_dir(output_dir)
    drop_columns = {"group", "crash_label", "risk_label"}
    feature_columns = [column for column in feature_table.columns if column not in drop_columns]
    x = feature_table[feature_columns].replace([np.inf, -np.inf], np.nan).fillna(0.0).to_numpy(dtype=np.float32)
    y = feature_table["crash_label"].to_numpy(dtype=np.int64)
    groups = feature_table["group"].to_numpy()

    stratify = y if len(np.unique(y)) > 1 and min(np.bincount(y)) >= 2 else None
    x_train, x_test, y_train, y_test, group_train, group_test = train_test_split(
        x,
        y,
        groups,
        test_size=cfg.test_size,
        random_state=seed,
        stratify=stratify,
    )

    rows: list[dict[str, object]] = []
    prediction_frames: list[pd.DataFrame] = []
    for name, model in _classifiers(cfg, seed).items():
        if model is None:
            rows.append({"classifier": name, "status": "skipped_missing_dependency"})
            continue
        model.fit(x_train, y_train)
        probabilities = _probabilities(model, x_test)
        threshold = tune_threshold(y_test, probabilities, cfg.max_false_positive_rate)
        preds = (probabilities >= threshold).astype(int)
        cm = confusion_matrix(y_test, preds, labels=[0, 1])
        rows.append(
            {
                "classifier": name,
                "status": "trained",
                "accuracy": float(accuracy_score(y_test, preds)),
                "precision": float(precision_score(y_test, preds, zero_division=0)),
                "recall": float(recall_score(y_test, preds, zero_division=0)),
                "f1": float(f1_score(y_test, preds, zero_division=0)),
                "threshold": threshold,
                "tn": int(cm[0, 0]),
                "fp": int(cm[0, 1]),
                "fn": int(cm[1, 0]),
                "tp": int(cm[1, 1]),
            }
        )
        joblib.dump({"model": model, "feature_columns": feature_columns, "threshold": threshold}, output_dir / f"{name}.joblib")
        prediction_frames.append(
            pd.DataFrame(
                {
                    "classifier": name,
                    "group": group_test,
                    "true_crash": y_test,
                    "crash_probability": probabilities,
                    "crash_confidence": probabilities,
                    "predicted_crash": preds,
                }
            )
        )
        if hasattr(model, "feature_importances_"):
            importance = pd.DataFrame({"feature": feature_columns, "importance": model.feature_importances_})
            importance.sort_values("importance", ascending=False).to_csv(output_dir / f"{name}_feature_importance.csv", index=False)

    report = pd.DataFrame(rows)
    predictions = pd.concat(prediction_frames, ignore_index=True) if prediction_frames else pd.DataFrame()
    report.to_csv(output_dir / "classifier_report.csv", index=False)
    predictions.to_csv(output_dir / "classifier_predictions.csv", index=False)
    with (output_dir / "feature_columns.json").open("w", encoding="utf-8") as handle:
        json.dump(feature_columns, handle, indent=2)
    best = report[report["status"] == "trained"].sort_values(["f1", "precision", "recall"], ascending=False)
    if not best.empty:
        best_name = str(best.iloc[0]["classifier"])
        with (output_dir / "deployment_manifest.json").open("w", encoding="utf-8") as handle:
            json.dump(
                {
                    "selected_classifier": best_name,
                    "model_file": f"{best_name}.joblib",
                    "threshold": float(best.iloc[0]["threshold"]),
                    "purpose": "Use only after the LSTM autoencoder has flagged a candidate anomaly.",
                    "offline_update_policy": "Save confirmed events for later validation/retraining; do not retrain live.",
                },
                handle,
                indent=2,
            )
    plot_classifier_comparison(report, output_dir)
    return report, predictions


def plot_classifier_comparison(report: pd.DataFrame, output_dir: Path) -> None:
    trained = report[report["status"] == "trained"]
    if trained.empty:
        return
    plot_dir = ensure_dir(output_dir / "plots")
    fig, ax = plt.subplots(figsize=(9, 5))
    x = np.arange(len(trained))
    width = 0.18
    for offset, metric in enumerate(["accuracy", "precision", "recall", "f1"]):
        ax.bar(x + offset * width, trained[metric], width, label=metric)
    ax.set_xticks(x + width * 1.5)
    ax.set_xticklabels(trained["classifier"])
    ax.set_ylim(0, 1.02)
    ax.legend()
    ax.grid(axis="y", alpha=0.25)
    fig.tight_layout()
    fig.savefig(plot_dir / "classifier_comparison.png", dpi=160)
    plt.close(fig)
