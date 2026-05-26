"""
evaluation/anomaly_evaluator.py
================================
STAGE 3 — Anomaly Evaluation
==============================

This module:
  1. Runs inference with a trained LSTM autoencoder.
  2. Computes reconstruction errors (overall + per-feature + temporal).
  3. Estimates anomaly thresholds (3 strategies).
  4. Generates anomaly scores & binary predictions.
  5. Evaluates separation quality between window categories.
  6. Computes classification metrics (ROC-AUC, precision, recall, F1).
  7. Produces all evaluation visualizations.
  8. Saves CSVs, plots, and metric reports.
"""

import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import numpy as np
import pandas as pd
import torch
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from sklearn.metrics import (
    roc_auc_score, precision_score, recall_score, f1_score,
    roc_curve, confusion_matrix, ConfusionMatrixDisplay,
)
from sklearn.decomposition import PCA
from sklearn.manifold import TSNE

from config.config import (
    DEVICE, ANOMALY, FEATURES, PLOTS_DIR, OUTPUT_DIR, REPORTS_DIR,
    N_FEATURES,
)
from models.autoencoder import LSTMAutoencoder
from utils.utils import get_logger, windows_to_tensor, save_csv

log = get_logger("anomaly_evaluator")

# Category color map used throughout all plots
CATEGORY_COLORS = {
    "normal"    : "#4CAF50",
    "aggressive": "#FF9800",
    "risky"     : "#FF5722",
    "crash"     : "#F44336",
}


# ─────────────────────────────────────────────────────────────────────────────
# INFERENCE
# ─────────────────────────────────────────────────────────────────────────────

@torch.no_grad()
def run_inference(
    model     : LSTMAutoencoder,
    windows   : np.ndarray,
    batch_size: int = 256,
    device    : torch.device = DEVICE,
) -> dict:
    """
    Run the autoencoder on a set of windows and return all error metrics.

    Returns a dict with:
        recon_error      : [N]       mean MSE per window
        per_feature_error: [N, F]    per-feature MSE
        temporal_error   : [N, T]    per-timestep MSE (averaged over features)
        latent_embeddings: [N, L]    encoder output (for PCA/t-SNE)
        reconstructions  : [N, T, F] full reconstructed signal
    """
    model.eval()
    model.to(device)

    N = len(windows)
    all_recon_err  = []
    all_feat_err   = []
    all_temp_err   = []
    all_latent     = []
    all_recon      = []

    for start in range(0, N, batch_size):
        batch = windows_to_tensor(windows[start:start + batch_size], device)  # [B, T, F]

        x_hat  = model(batch)                             # [B, T, F]
        z      = model.encode(batch)                      # [B, L]

        # Per-window mean MSE
        recon_err  = ((batch - x_hat) ** 2).mean(dim=(1, 2))   # [B]
        # Per-feature MSE (averaged over T)
        feat_err   = ((batch - x_hat) ** 2).mean(dim=1)        # [B, F]
        # Per-timestep MSE (averaged over F)
        temp_err   = ((batch - x_hat) ** 2).mean(dim=2)        # [B, T]

        all_recon_err.append(recon_err.cpu().numpy())
        all_feat_err.append(feat_err.cpu().numpy())
        all_temp_err.append(temp_err.cpu().numpy())
        all_latent.append(z.cpu().numpy())
        all_recon.append(x_hat.cpu().numpy())

    return {
        "recon_error"       : np.concatenate(all_recon_err),
        "per_feature_error" : np.concatenate(all_feat_err),
        "temporal_error"    : np.concatenate(all_temp_err),
        "latent_embeddings" : np.concatenate(all_latent),
        "reconstructions"   : np.concatenate(all_recon),
    }


# ─────────────────────────────────────────────────────────────────────────────
# THRESHOLD ESTIMATION
# ─────────────────────────────────────────────────────────────────────────────

def estimate_thresholds(normal_errors: np.ndarray) -> dict:
    """
    Compute anomaly thresholds from normal driving reconstruction errors.

    Three strategies:
    -----------------
    1. PERCENTILE: threshold = p-th percentile of normal errors.
       Simple, robust to outliers, directly controls false-positive rate.

    2. SIGMA: threshold = mean + k * std.
       Assumes roughly Gaussian error distribution. Works well in practice
       because reconstruction errors tend to be log-normally distributed.

    3. ADAPTIVE: rolling mean + k * rolling std computed on ordered samples.
       Useful for streaming / online detection where distribution shifts over time.

    All thresholds returned in one dict for easy comparison.
    """
    mu  = float(np.mean(normal_errors))
    std = float(np.std(normal_errors))
    p   = ANOMALY["percentile_threshold"]
    k   = ANOMALY["sigma_multiplier"]

    thresholds = {
        "percentile" : float(np.percentile(normal_errors, p)),
        "sigma"      : mu + k * std,
        "adaptive"   : _adaptive_threshold(normal_errors, k),
    }

    log.info(
        f"Thresholds → "
        f"percentile({p}%): {thresholds['percentile']:.4f}  |  "
        f"sigma({k}σ): {thresholds['sigma']:.4f}  |  "
        f"adaptive: {thresholds['adaptive']:.4f}"
    )
    return thresholds


def _adaptive_threshold(errors: np.ndarray, k: float) -> float:
    """
    Adaptive threshold: rolling mean + k*std over the sorted error array.
    Returns the threshold at the tail (last adaptive value).
    """
    w = ANOMALY["adaptive_window"]
    # Sort errors to simulate a rolling window over increasing values
    sorted_e = np.sort(errors)
    roll_mu  = pd.Series(sorted_e).rolling(w, min_periods=1).mean().values
    roll_std = pd.Series(sorted_e).rolling(w, min_periods=1).std(ddof=0).fillna(0).values
    return float(np.max(roll_mu + k * roll_std))


# ─────────────────────────────────────────────────────────────────────────────
# ANOMALY SCORING
# ─────────────────────────────────────────────────────────────────────────────

def compute_anomaly_scores(errors: np.ndarray,
                           thresholds: dict,
                           strategy: str = "percentile") -> dict:
    """
    Convert reconstruction errors to anomaly scores and binary predictions.

    Anomaly score = reconstruction error (unnormalized).
    Binary prediction = error > threshold.

    Args:
        errors     : [N] reconstruction error per window
        thresholds : dict of threshold values
        strategy   : which threshold to use for binary label

    Returns:
        dict with 'scores', 'binary', 'threshold_used'
    """
    thresh = thresholds[strategy]
    return {
        "scores"          : errors,
        "binary"          : (errors > thresh).astype(int),
        "threshold_used"  : thresh,
        "strategy"        : strategy,
    }


# ─────────────────────────────────────────────────────────────────────────────
# CLASSIFICATION METRICS
# ─────────────────────────────────────────────────────────────────────────────

def evaluate_metrics(
    y_true    : np.ndarray,
    y_score   : np.ndarray,
    threshold : float,
) -> dict:
    """
    Compute full classification report.

    Args:
        y_true    : [N] ground-truth binary labels (0=normal, 1=anomaly)
        y_score   : [N] anomaly scores (reconstruction errors)
        threshold : decision threshold

    Returns:
        dict with AUC, precision, recall, F1, confusion matrix
    """
    y_pred = (y_score > threshold).astype(int)

    metrics = {
        "roc_auc"  : float(roc_auc_score(y_true, y_score)),
        "precision": float(precision_score(y_true, y_pred, zero_division=0)),
        "recall"   : float(recall_score(y_true, y_pred, zero_division=0)),
        "f1_score" : float(f1_score(y_true, y_pred, zero_division=0)),
        "threshold": threshold,
        "n_tp"     : int(np.sum((y_pred == 1) & (y_true == 1))),
        "n_fp"     : int(np.sum((y_pred == 1) & (y_true == 0))),
        "n_tn"     : int(np.sum((y_pred == 0) & (y_true == 0))),
        "n_fn"     : int(np.sum((y_pred == 0) & (y_true == 1))),
    }
    log.info(
        f"AUC={metrics['roc_auc']:.3f}  "
        f"P={metrics['precision']:.3f}  "
        f"R={metrics['recall']:.3f}  "
        f"F1={metrics['f1_score']:.3f}"
    )
    return metrics


# ─────────────────────────────────────────────────────────────────────────────
# VISUALIZATIONS
# ─────────────────────────────────────────────────────────────────────────────

def plot_error_distributions(
    error_dict : dict,    # {category_name: error_array}
    thresholds : dict,
    save_path  : str = None,
) -> None:
    """Overlapping error histograms per category + threshold lines."""
    fig, axes = plt.subplots(1, 2, figsize=(14, 5))
    fig.suptitle("Reconstruction Error Distributions by Category", fontweight="bold")

    threshold_colors = {"percentile": "blue", "sigma": "orange", "adaptive": "purple"}

    for name, errors in error_dict.items():
        c = CATEGORY_COLORS.get(name, "gray")
        axes[0].hist(errors, bins=60, alpha=0.55, label=name, color=c, density=True)
        axes[1].hist(np.log1p(errors), bins=60, alpha=0.55, label=name, color=c, density=True)

    for t_name, t_val in thresholds.items():
        c = threshold_colors.get(t_name, "black")
        axes[0].axvline(t_val, color=c, linestyle="--", linewidth=1.5, label=f"θ_{t_name}")
        axes[1].axvline(np.log1p(t_val), color=c, linestyle="--", linewidth=1.5)

    axes[0].set(title="Raw Reconstruction Error", xlabel="MSE", ylabel="Density")
    axes[1].set(title="Log Reconstruction Error", xlabel="log(1+MSE)", ylabel="Density")
    for ax in axes:
        ax.legend(fontsize=8)
        ax.grid(True, alpha=0.3)

    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


def plot_anomaly_score_histogram(
    scores_dict: dict,
    threshold  : float,
    save_path  : str = None,
) -> None:
    """Stacked anomaly score distribution with decision boundary."""
    fig, ax = plt.subplots(figsize=(10, 5))
    for name, scores in scores_dict.items():
        c = CATEGORY_COLORS.get(name, "gray")
        ax.hist(scores, bins=80, alpha=0.6, label=name, color=c, density=True)

    ax.axvline(threshold, color="black", linestyle="--", linewidth=2,
               label=f"Threshold = {threshold:.4f}")
    ax.fill_betweenx(
        [0, ax.get_ylim()[1] if ax.get_ylim()[1] > 0 else 1],
        threshold, ax.get_xlim()[1] if ax.get_xlim()[1] > threshold else threshold * 2,
        alpha=0.1, color="red", label="Anomaly Zone"
    )
    ax.set(title="Anomaly Score Distribution", xlabel="Reconstruction Error", ylabel="Density")
    ax.legend()
    ax.grid(True, alpha=0.3)
    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


def plot_anomaly_timeline(
    errors    : np.ndarray,
    labels    : np.ndarray,     # ground truth
    threshold : float,
    category_names: list,       # name per window
    save_path : str = None,
) -> None:
    """
    Timeline plot: anomaly score over window index.
    Highlights ground-truth anomaly windows in red.
    """
    fig, ax = plt.subplots(figsize=(14, 5))
    ax.plot(errors, color="steelblue", linewidth=0.6, alpha=0.8, label="Reconstruction Error")
    ax.axhline(threshold, color="red", linestyle="--", linewidth=1.5, label=f"Threshold")

    # Shade regions where ground truth = 1
    for i, (lbl, e) in enumerate(zip(labels, errors)):
        if lbl == 1:
            ax.axvspan(i - 0.5, i + 0.5, color="salmon", alpha=0.3)

    ax.set(title="Anomaly Score Timeline", xlabel="Window Index", ylabel="Reconstruction MSE")
    ax.legend()
    ax.grid(True, alpha=0.3)
    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


def plot_input_vs_reconstruction(
    original   : np.ndarray,    # [T, F]
    reconstructed: np.ndarray,  # [T, F]
    title      : str = "Input vs Reconstruction",
    save_path  : str = None,
) -> None:
    """
    Side-by-side input and reconstruction for a single window.
    Reconstruction error is shaded.
    """
    T, F = original.shape
    t    = np.arange(T)
    fig, axes = plt.subplots(F, 1, figsize=(12, F * 2), sharex=True)
    fig.suptitle(title, fontweight="bold")

    for i, (ax, feat) in enumerate(zip(axes, FEATURES)):
        ax.plot(t, original[:, i],       label="Input",          color="steelblue",  linewidth=1)
        ax.plot(t, reconstructed[:, i],  label="Reconstruction", color="crimson",    linewidth=1,
                linestyle="--")
        ax.fill_between(t, original[:, i], reconstructed[:, i], alpha=0.2, color="orange",
                        label="Error")
        ax.set_ylabel(feat, fontsize=8)
        ax.legend(fontsize=7, loc="upper right")
        ax.grid(True, alpha=0.3)

    axes[-1].set_xlabel("Timestep")
    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


def plot_roc_curve(y_true: np.ndarray, y_score: np.ndarray,
                   auc: float, save_path: str = None) -> None:
    fpr, tpr, _ = roc_curve(y_true, y_score)
    fig, ax = plt.subplots(figsize=(6, 6))
    ax.plot(fpr, tpr, color="crimson", linewidth=2, label=f"AUC = {auc:.3f}")
    ax.plot([0, 1], [0, 1], linestyle="--", color="gray")
    ax.set(title="ROC Curve", xlabel="False Positive Rate", ylabel="True Positive Rate")
    ax.legend()
    ax.grid(True, alpha=0.3)
    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


def plot_per_feature_error(
    feat_error_dict: dict,    # {category: [N, F] array}
    save_path: str = None,
) -> None:
    """Bar chart of mean per-feature reconstruction error per category."""
    fig, ax = plt.subplots(figsize=(10, 5))
    x    = np.arange(len(FEATURES))
    width= 0.2
    for i, (name, feat_err) in enumerate(feat_error_dict.items()):
        means = feat_err.mean(axis=0)
        ax.bar(x + i * width, means, width, label=name,
               color=CATEGORY_COLORS.get(name, "gray"), alpha=0.8)

    ax.set_xticks(x + width * (len(feat_error_dict) - 1) / 2)
    ax.set_xticklabels(FEATURES)
    ax.set(title="Mean Per-Feature Reconstruction Error by Category",
           ylabel="Mean MSE")
    ax.legend()
    ax.grid(True, alpha=0.3, axis="y")
    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


def plot_latent_embeddings(
    embeddings_dict: dict,    # {category: [N, L] array}
    method: str = "pca",
    save_path: str = None,
) -> None:
    """
    2-D projection of latent embeddings using PCA or t-SNE.

    Purpose: verify the autoencoder has learned a compact normal manifold
    that crash windows fall outside of. Good separation here indicates the
    encoder captures crash-relevant structure, not just noise.
    """
    names   = list(embeddings_dict.keys())
    arrays  = [embeddings_dict[n] for n in names]
    labels  = np.concatenate([[i] * len(a) for i, a in enumerate(arrays)])
    all_emb = np.concatenate(arrays, axis=0)

    # Dimensionality reduction
    if method == "tsne":
        log.info("Running t-SNE on latent embeddings (may be slow for large N)…")
        reducer = TSNE(n_components=2, random_state=42, perplexity=30, n_iter=1000)
    else:
        reducer = PCA(n_components=2)

    emb_2d = reducer.fit_transform(all_emb)

    fig, ax = plt.subplots(figsize=(8, 6))
    for i, name in enumerate(names):
        mask = labels == i
        ax.scatter(emb_2d[mask, 0], emb_2d[mask, 1],
                   label=name, color=CATEGORY_COLORS.get(name, "gray"),
                   alpha=0.5, s=10, edgecolors="none")

    ax.set(title=f"Latent Space ({method.upper()})", xlabel="Component 1", ylabel="Component 2")
    ax.legend(markerscale=3)
    ax.grid(True, alpha=0.3)
    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.show(); plt.close()


# ─────────────────────────────────────────────────────────────────────────────
# HIGH-LEVEL EVALUATOR CLASS
# ─────────────────────────────────────────────────────────────────────────────

class AnomalyEvaluator:
    """
    Orchestrates the full Stage 3 anomaly evaluation pipeline.

    Usage
    -----
    evaluator = AnomalyEvaluator(model, device)
    results   = evaluator.run(
        windows_dict  = {"normal": w_n, "aggressive": w_a, "crash": w_c},
        labels_dict   = {"normal": l_n, "aggressive": l_a, "crash": l_c},
    )
    evaluator.save_outputs(results)
    """

    def __init__(self, model: LSTMAutoencoder, device: torch.device = DEVICE):
        self.model  = model
        self.device = device

    def run(
        self,
        windows_dict : dict,   # {category: [N, T, F]}
        labels_dict  : dict,   # {category: [N]} ground truth (0/1)
    ) -> dict:
        """Run full evaluation and return a results dict."""
        log.info("=== STAGE 3: Anomaly Evaluation ===")

        # --- 1. Inference per category ---
        inference = {}
        for cat, windows in windows_dict.items():
            log.info(f"  Running inference on category: {cat} ({len(windows)} windows)")
            inference[cat] = run_inference(self.model, windows, device=self.device)

        # --- 2. Threshold estimation (from normal errors only) ---
        normal_errors = inference["normal"]["recon_error"]
        thresholds    = estimate_thresholds(normal_errors)

        # --- 3. Anomaly scores per category ---
        scores = {cat: inf["recon_error"] for cat, inf in inference.items()}

        # --- 4. Build flat ground-truth arrays for metric computation ---
        y_true  = np.concatenate(list(labels_dict.values()))
        y_score = np.concatenate(list(scores.values()))

        # Use percentile threshold as primary
        primary_threshold = thresholds["percentile"]
        metrics = evaluate_metrics(y_true, y_score, primary_threshold)

        return {
            "inference"  : inference,
            "thresholds" : thresholds,
            "scores"     : scores,
            "y_true"     : y_true,
            "y_score"    : y_score,
            "metrics"    : metrics,
        }

    def generate_all_plots(self, results: dict, windows_dict: dict) -> None:
        """Generate and save all evaluation visualizations."""
        inf        = results["inference"]
        thresholds = results["thresholds"]
        scores     = results["scores"]
        metrics    = results["metrics"]

        # Error distributions
        plot_error_distributions(
            {cat: inf[cat]["recon_error"] for cat in inf},
            thresholds,
            save_path=os.path.join(PLOTS_DIR, "error_distributions.png"),
        )

        # Anomaly score histogram (primary threshold)
        plot_anomaly_score_histogram(
            scores,
            threshold=thresholds["percentile"],
            save_path=os.path.join(PLOTS_DIR, "anomaly_score_histogram.png"),
        )

        # Anomaly timeline (all categories concatenated)
        all_errors = np.concatenate(list(scores.values()))
        all_labels = results["y_true"]
        plot_anomaly_timeline(
            all_errors, all_labels, thresholds["percentile"],
            category_names=[],
            save_path=os.path.join(PLOTS_DIR, "anomaly_timeline.png"),
        )

        # ROC curve
        plot_roc_curve(
            results["y_true"], results["y_score"],
            auc=metrics["roc_auc"],
            save_path=os.path.join(PLOTS_DIR, "roc_curve.png"),
        )

        # Per-feature reconstruction error
        plot_per_feature_error(
            {cat: inf[cat]["per_feature_error"] for cat in inf},
            save_path=os.path.join(PLOTS_DIR, "per_feature_error.png"),
        )

        # Latent space PCA
        plot_latent_embeddings(
            {cat: inf[cat]["latent_embeddings"] for cat in inf},
            method="pca",
            save_path=os.path.join(PLOTS_DIR, "latent_pca.png"),
        )

        # Input vs reconstruction — pick one crash window
        crash_cat = "crash" if "crash" in inf else list(inf.keys())[-1]
        idx       = 0
        plot_input_vs_reconstruction(
            original     = windows_dict[crash_cat][idx],
            reconstructed= inf[crash_cat]["reconstructions"][idx],
            title        = f"Input vs Reconstruction — {crash_cat} window #{idx}",
            save_path    = os.path.join(PLOTS_DIR, "input_vs_reconstruction.png"),
        )
        log.info("All Stage 3 plots saved.")

    def save_outputs(self, results: dict) -> None:
        """Save anomaly score CSVs and metric reports."""
        # --- Anomaly scores CSV ---
        rows = []
        for cat, sc in results["scores"].items():
            for score in sc:
                rows.append({"category": cat, "anomaly_score": score})
        save_csv(pd.DataFrame(rows),
                 os.path.join(OUTPUT_DIR, "anomaly_scores.csv"))

        # --- Metrics report ---
        report_df = pd.DataFrame([results["metrics"]])
        save_csv(report_df, os.path.join(REPORTS_DIR, "stage3_metrics.csv"))
        log.info("Stage 3 outputs saved.")
