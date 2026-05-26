"""
classifiers/crash_classifier.py
=================================
STAGE 4 — Crash Confirmation Classifier

Two-stage pipeline rationale
-----------------------------
The LSTM autoencoder is good at flagging OUT-OF-DISTRIBUTION windows,
but it cannot distinguish crash from aggressive driving (both score high).
A supervised classifier trained on labelled examples learns this boundary.

Three complementary classifiers are implemented:
  1. Random Forest  — ensemble, interpretable feature importance, fast
  2. XGBoost        — gradient boosting, typically best tabular performance
  3. MLP (PyTorch)  — lightweight neural net, continuous probability output

Threshold Tuning
-----------------
Each classifier outputs a crash probability in [0, 1].
The decision threshold (default 0.5) is tuned to minimize false positives
at a target false-positive rate (FPR ≤ config.CLASSIFIER["fp_rate_target"]).
This is critical for SOS systems: we prefer missed detections over false alarms.
"""

import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pickle
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset
import matplotlib.pyplot as plt
from sklearn.ensemble   import RandomForestClassifier
from sklearn.metrics    import (
    accuracy_score, precision_score, recall_score, f1_score,
    confusion_matrix, ConfusionMatrixDisplay, roc_auc_score, roc_curve,
)
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split

try:
    from xgboost import XGBClassifier
    XGB_AVAILABLE = True
except ImportError:
    XGB_AVAILABLE = False

from config.config import (
    CLASSIFIER as CLSF_CFG, RANDOM_SEED, DEVICE, MODELS_DIR,
    PLOTS_DIR, REPORTS_DIR, OUTPUT_DIR,
)
from utils.utils import get_logger, set_seed, save_csv

log = get_logger("crash_classifier")


# ─────────────────────────────────────────────────────────────────────────────
# LIGHTWEIGHT MLP (PyTorch)
# ─────────────────────────────────────────────────────────────────────────────

class CrashMLP(nn.Module):
    """
    Small fully-connected network for crash probability estimation.

    Architecture: Input → [128 → 64 → 32] → 1 (sigmoid)
    Dropout after each hidden layer for regularisation.
    Binary cross-entropy loss during training.
    """

    def __init__(self, input_dim: int, hidden_dims: list, dropout: float = 0.3):
        super().__init__()
        layers = []
        in_dim = input_dim
        for h in hidden_dims:
            layers += [nn.Linear(in_dim, h), nn.BatchNorm1d(h), nn.ReLU(), nn.Dropout(dropout)]
            in_dim = h
        layers += [nn.Linear(in_dim, 1), nn.Sigmoid()]
        self.net = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x).squeeze(-1)   # [B]


class MLPTrainer:
    """Trains and evaluates the CrashMLP classifier."""

    def __init__(self, input_dim: int, device: torch.device = DEVICE):
        set_seed(RANDOM_SEED)
        self.device = device
        self.model  = CrashMLP(
            input_dim   = input_dim,
            hidden_dims = CLSF_CFG["mlp_hidden"],
        ).to(device)
        self.optimizer = torch.optim.Adam(self.model.parameters(), lr=CLSF_CFG["mlp_lr"])
        self.criterion = nn.BCELoss()
        self.scaler_   = StandardScaler()   # fitted during training

    def fit(self, X_train: np.ndarray, y_train: np.ndarray) -> None:
        X_scaled = self.scaler_.fit_transform(X_train).astype(np.float32)
        dataset  = TensorDataset(
            torch.tensor(X_scaled),
            torch.tensor(y_train, dtype=torch.float32),
        )
        loader = DataLoader(dataset, batch_size=CLSF_CFG["mlp_batch_size"], shuffle=True)

        self.model.train()
        for epoch in range(CLSF_CFG["mlp_epochs"]):
            epoch_loss = 0.0
            for X_b, y_b in loader:
                X_b, y_b = X_b.to(self.device), y_b.to(self.device)
                self.optimizer.zero_grad()
                pred = self.model(X_b)
                loss = self.criterion(pred, y_b)
                loss.backward()
                self.optimizer.step()
                epoch_loss += loss.item()
            if (epoch + 1) % 10 == 0:
                log.info(f"    MLP epoch {epoch+1:3d}/{CLSF_CFG['mlp_epochs']}  "
                         f"loss={epoch_loss/len(loader):.4f}")

    def predict_proba(self, X: np.ndarray) -> np.ndarray:
        X_scaled = self.scaler_.transform(X).astype(np.float32)
        self.model.eval()
        with torch.no_grad():
            probs = self.model(torch.tensor(X_scaled).to(self.device)).cpu().numpy()
        return probs   # [N]  probabilities of crash=1

    def predict(self, X: np.ndarray, threshold: float = 0.5) -> np.ndarray:
        return (self.predict_proba(X) >= threshold).astype(int)

    def save(self, path: str) -> None:
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        torch.save({
            "model_state": self.model.state_dict(),
            "scaler"     : self.scaler_,
        }, path)
        log.info(f"MLP saved → {path}")

    @classmethod
    def load(cls, path: str, input_dim: int, device: torch.device = DEVICE) -> "MLPTrainer":
        trainer = cls(input_dim, device)
        ckpt    = torch.load(path, map_location=device)
        trainer.model.load_state_dict(ckpt["model_state"])
        trainer.scaler_ = ckpt["scaler"]
        trainer.model.eval()
        return trainer


# ─────────────────────────────────────────────────────────────────────────────
# THRESHOLD TUNING
# ─────────────────────────────────────────────────────────────────────────────

def tune_threshold(y_true: np.ndarray, y_proba: np.ndarray,
                   fp_rate_target: float = 0.05) -> float:
    """
    Find the lowest threshold that keeps FPR ≤ fp_rate_target.

    In SOS systems, false positives (false crash alarms) are costly:
    they erode user trust and may lead to unnecessary emergency responses.
    We therefore set the decision threshold conservatively.

    Strategy: scan ROC curve and pick the threshold with FPR ≤ target
    that maximises recall (TPR).
    """
    fpr, tpr, thresholds = roc_curve(y_true, y_proba)
    # Find all thresholds where FPR ≤ target
    valid = fpr <= fp_rate_target
    if not np.any(valid):
        log.warning("No threshold meets FP rate target; using 0.5")
        return 0.5
    # Among valid thresholds, pick the one with highest TPR
    best_idx = np.argmax(tpr[valid])
    # Map back to original threshold index
    valid_indices = np.where(valid)[0]
    best_thresh   = float(thresholds[valid_indices[best_idx]])
    log.info(f"Tuned threshold = {best_thresh:.4f}  "
             f"(FPR={fpr[valid_indices[best_idx]]:.3f}, "
             f"TPR={tpr[valid_indices[best_idx]]:.3f})")
    return best_thresh


# ─────────────────────────────────────────────────────────────────────────────
# METRICS
# ─────────────────────────────────────────────────────────────────────────────

def compute_metrics(y_true: np.ndarray, y_pred: np.ndarray,
                    y_proba: np.ndarray, name: str) -> dict:
    m = {
        "classifier" : name,
        "accuracy"   : float(accuracy_score(y_true, y_pred)),
        "precision"  : float(precision_score(y_true, y_pred, zero_division=0)),
        "recall"     : float(recall_score(y_true, y_pred, zero_division=0)),
        "f1_score"   : float(f1_score(y_true, y_pred, zero_division=0)),
        "roc_auc"    : float(roc_auc_score(y_true, y_proba)),
    }
    log.info(
        f"  [{name:12s}] Acc={m['accuracy']:.3f}  P={m['precision']:.3f}  "
        f"R={m['recall']:.3f}  F1={m['f1_score']:.3f}  AUC={m['roc_auc']:.3f}"
    )
    return m


# ─────────────────────────────────────────────────────────────────────────────
# VISUALIZATION
# ─────────────────────────────────────────────────────────────────────────────

def plot_confusion_matrices(cms: dict, class_names: list,
                            save_path: str = None) -> None:
    n    = len(cms)
    fig, axes = plt.subplots(1, n, figsize=(5 * n, 4))
    if n == 1:
        axes = [axes]
    fig.suptitle("Confusion Matrices — Crash Confirmation Classifiers", fontweight="bold")

    for ax, (name, cm) in zip(axes, cms.items()):
        disp = ConfusionMatrixDisplay(confusion_matrix=cm, display_labels=class_names)
        disp.plot(ax=ax, colorbar=False, cmap="Blues")
        ax.set_title(name)

    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


def plot_metrics_comparison(metrics_list: list, save_path: str = None) -> None:
    df     = pd.DataFrame(metrics_list).set_index("classifier")
    metric_cols = ["accuracy", "precision", "recall", "f1_score", "roc_auc"]
    df     = df[metric_cols]

    x      = np.arange(len(metric_cols))
    width  = 0.8 / len(df)
    fig, ax = plt.subplots(figsize=(11, 5))
    fig.suptitle("Classifier Comparison", fontweight="bold")

    colors = ["#2196F3", "#FF9800", "#4CAF50"]
    for i, (clf_name, row) in enumerate(df.iterrows()):
        ax.bar(x + i * width, row.values, width, label=clf_name,
               color=colors[i % len(colors)], alpha=0.8)

    ax.set_xticks(x + width * (len(df) - 1) / 2)
    ax.set_xticklabels([m.replace("_", " ").title() for m in metric_cols])
    ax.set_ylim(0, 1.05)
    ax.set_ylabel("Score")
    ax.legend()
    ax.grid(True, alpha=0.3, axis="y")
    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


def plot_feature_importance(importances: dict, feature_names: list,
                            top_n: int = 20, save_path: str = None) -> None:
    """Plot top-N feature importances from tree-based models."""
    n_models = len(importances)
    fig, axes = plt.subplots(1, n_models, figsize=(7 * n_models, 6))
    if n_models == 1:
        axes = [axes]
    fig.suptitle(f"Top-{top_n} Feature Importances", fontweight="bold")

    for ax, (name, imp) in zip(axes, importances.items()):
        idx   = np.argsort(imp)[::-1][:top_n]
        vals  = imp[idx]
        names = [feature_names[i] for i in idx]
        ax.barh(range(len(vals)), vals[::-1], color="#42A5F5", alpha=0.85)
        ax.set_yticks(range(len(vals)))
        ax.set_yticklabels(names[::-1], fontsize=7)
        ax.set_xlabel("Importance")
        ax.set_title(name)
        ax.grid(True, alpha=0.3, axis="x")

    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


def plot_probability_distribution(proba_dict: dict, threshold: float,
                                  save_path: str = None) -> None:
    """Distribution of crash probability by category."""
    CATEGORY_COLORS = {
        "normal": "#4CAF50", "aggressive": "#FF9800",
        "risky": "#FF5722",  "crash": "#F44336",
    }
    fig, ax = plt.subplots(figsize=(10, 4))
    for cat, proba in proba_dict.items():
        ax.hist(proba, bins=50, alpha=0.6, label=cat, density=True,
                color=CATEGORY_COLORS.get(cat, "gray"))
    ax.axvline(threshold, color="black", linestyle="--", linewidth=2,
               label=f"Threshold = {threshold:.3f}")
    ax.set(title="Crash Probability Distribution by Category",
           xlabel="P(crash)", ylabel="Density")
    ax.legend(); ax.grid(True, alpha=0.3)
    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


# ─────────────────────────────────────────────────────────────────────────────
# MAIN CLASSIFIER PIPELINE
# ─────────────────────────────────────────────────────────────────────────────

class CrashConfirmationPipeline:
    """
    Orchestrates Stage 4: trains, evaluates, and saves all classifiers.

    Usage
    -----
    pipeline = CrashConfirmationPipeline()
    results  = pipeline.run(feature_df)
    pipeline.save_outputs(results)
    """

    def __init__(self, device: torch.device = DEVICE):
        set_seed(RANDOM_SEED)
        self.device = device

    def run(self, feature_df: pd.DataFrame) -> dict:
        """
        Args:
            feature_df: DataFrame with feature columns + 'is_crash' label column

        Returns:
            dict with trained models, metrics, predictions
        """
        log.info("=== STAGE 4: Crash Confirmation Classifier ===")

        # --- Prepare data ---
        label_col = "is_crash"
        drop_cols = [c for c in ["label", "category", "is_crash"] if c in feature_df.columns]

        X = feature_df.drop(columns=drop_cols).values.astype(np.float32)
        y = feature_df[label_col].values.astype(int)
        feature_names = [c for c in feature_df.columns if c not in drop_cols]

        # Handle NaN/inf from feature engineering edge cases
        X = np.nan_to_num(X, nan=0.0, posinf=0.0, neginf=0.0)

        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=0.2, random_state=RANDOM_SEED, stratify=y
        )
        log.info(f"Train: {len(X_train)}  Test: {len(X_test)}  "
                 f"Crash rate: {y.mean():.2%}")

        # --- Scaler for tree models (optional but consistent) ---
        scaler = StandardScaler()
        X_train_sc = scaler.fit_transform(X_train)
        X_test_sc  = scaler.transform(X_test)

        metrics_list = []
        models       = {}
        thresholds   = {}
        cms          = {}
        importances  = {}
        all_probas   = {}

        # ── Random Forest ───────────────────────────────────────────────────
        log.info("Training Random Forest…")
        rf = RandomForestClassifier(
            n_estimators = CLSF_CFG["rf_n_estimators"],
            max_depth    = CLSF_CFG["rf_max_depth"],
            random_state = RANDOM_SEED,
            n_jobs       = -1,
            class_weight = "balanced",
        )
        rf.fit(X_train_sc, y_train)
        rf_proba = rf.predict_proba(X_test_sc)[:, 1]
        rf_thresh = tune_threshold(y_test, rf_proba, CLSF_CFG["fp_rate_target"])
        rf_pred   = (rf_proba >= rf_thresh).astype(int)

        metrics_list.append(compute_metrics(y_test, rf_pred, rf_proba, "RandomForest"))
        models["RandomForest"]      = rf
        thresholds["RandomForest"]  = rf_thresh
        cms["RandomForest"]         = confusion_matrix(y_test, rf_pred)
        importances["RandomForest"] = rf.feature_importances_
        all_probas["RandomForest"]  = rf_proba

        # ── XGBoost ─────────────────────────────────────────────────────────
        if XGB_AVAILABLE:
            log.info("Training XGBoost…")
            xgb = XGBClassifier(
                n_estimators  = CLSF_CFG["xgb_n_estimators"],
                max_depth     = CLSF_CFG["xgb_max_depth"],
                learning_rate = CLSF_CFG["xgb_lr"],
                random_state  = RANDOM_SEED,
                use_label_encoder = False,
                eval_metric   = "logloss",
                scale_pos_weight = (y == 0).sum() / (y == 1).sum() if (y == 1).sum() > 0 else 1,
            )
            xgb.fit(X_train_sc, y_train)
            xgb_proba = xgb.predict_proba(X_test_sc)[:, 1]
            xgb_thresh = tune_threshold(y_test, xgb_proba, CLSF_CFG["fp_rate_target"])
            xgb_pred   = (xgb_proba >= xgb_thresh).astype(int)

            metrics_list.append(compute_metrics(y_test, xgb_pred, xgb_proba, "XGBoost"))
            models["XGBoost"]      = xgb
            thresholds["XGBoost"]  = xgb_thresh
            cms["XGBoost"]         = confusion_matrix(y_test, xgb_pred)
            importances["XGBoost"] = xgb.feature_importances_
            all_probas["XGBoost"]  = xgb_proba
        else:
            log.warning("XGBoost not installed; skipping.")

        # ── MLP ─────────────────────────────────────────────────────────────
        log.info("Training MLP…")
        mlp_trainer = MLPTrainer(input_dim=X_train_sc.shape[1], device=self.device)
        mlp_trainer.fit(X_train_sc, y_train)
        mlp_proba  = mlp_trainer.predict_proba(X_test_sc)
        mlp_thresh = tune_threshold(y_test, mlp_proba, CLSF_CFG["fp_rate_target"])
        mlp_pred   = (mlp_proba >= mlp_thresh).astype(int)

        metrics_list.append(compute_metrics(y_test, mlp_pred, mlp_proba, "MLP"))
        models["MLP"]      = mlp_trainer
        thresholds["MLP"]  = mlp_thresh
        cms["MLP"]         = confusion_matrix(y_test, mlp_pred)
        # MLP has no built-in feature importance; skip
        all_probas["MLP"]  = mlp_proba

        # ── Per-category probabilities (best classifier) ─────────────────────
        # Use RF as default "best" for probability breakdown
        rf_proba_all = rf.predict_proba(scaler.transform(X))[:, 1]
        categories   = feature_df["category"].values if "category" in feature_df else None
        cat_probas   = {}
        if categories is not None:
            for cat in np.unique(categories):
                mask = categories == cat
                cat_probas[cat] = rf_proba_all[mask]

        return {
            "models"        : models,
            "thresholds"    : thresholds,
            "metrics_list"  : metrics_list,
            "cms"           : cms,
            "importances"   : importances,
            "feature_names" : feature_names,
            "X_test"        : X_test_sc,
            "y_test"        : y_test,
            "all_probas"    : all_probas,
            "cat_probas"    : cat_probas,
            "scaler"        : scaler,
        }

    def generate_all_plots(self, results: dict) -> None:
        """Save all Stage 4 visualizations."""
        plot_confusion_matrices(
            results["cms"], class_names=["non-crash", "crash"],
            save_path=os.path.join(PLOTS_DIR, "confusion_matrices.png"),
        )
        plot_metrics_comparison(
            results["metrics_list"],
            save_path=os.path.join(PLOTS_DIR, "classifier_comparison.png"),
        )
        if results["importances"]:
            plot_feature_importance(
                results["importances"], results["feature_names"],
                save_path=os.path.join(PLOTS_DIR, "feature_importance.png"),
            )
        if results["cat_probas"]:
            best_thresh = results["thresholds"].get("RandomForest", 0.5)
            plot_probability_distribution(
                results["cat_probas"], best_thresh,
                save_path=os.path.join(PLOTS_DIR, "crash_probability_dist.png"),
            )
        log.info("All Stage 4 plots saved.")

    def save_outputs(self, results: dict) -> None:
        """Save models, metric reports, and prediction CSVs."""
        # --- Save sklearn models ---
        for name, model in results["models"].items():
            if name == "MLP":
                path = os.path.join(MODELS_DIR, "mlp_classifier.pt")
                model.save(path)
            else:
                path = os.path.join(MODELS_DIR, f"{name.lower()}_classifier.pkl")
                with open(path, "wb") as f:
                    pickle.dump(model, f)
                log.info(f"{name} saved → {path}")

        # Save scaler
        scaler_path = os.path.join(MODELS_DIR, "feature_scaler.pkl")
        with open(scaler_path, "wb") as f:
            pickle.dump(results["scaler"], f)

        # --- Metrics report ---
        report_df = pd.DataFrame(results["metrics_list"])
        save_csv(report_df, os.path.join(REPORTS_DIR, "stage4_metrics.csv"))

        # --- Prediction CSV ---
        pred_rows = []
        for clf_name, probas in results["all_probas"].items():
            thresh = results["thresholds"][clf_name]
            for proba in probas:
                pred_rows.append({
                    "classifier"      : clf_name,
                    "crash_probability": proba,
                    "predicted_crash" : int(proba >= thresh),
                    "threshold"       : thresh,
                })
        save_csv(pd.DataFrame(pred_rows),
                 os.path.join(OUTPUT_DIR, "classifier_predictions.csv"))
        log.info("Stage 4 outputs saved.")
