from __future__ import annotations

import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import torch
from sklearn.decomposition import PCA
from sklearn.metrics import f1_score, precision_score, recall_score, roc_auc_score
from torch.utils.data import DataLoader, TensorDataset

from .config import FEATURE_COLUMNS, ThresholdConfig
from .utils import ensure_dir, validate_windows


@torch.no_grad()
def reconstruct_windows(
    model: torch.nn.Module,
    windows: np.ndarray,
    batch_size: int,
    device: torch.device,
) -> tuple[np.ndarray, np.ndarray]:
    windows = validate_windows(windows)
    loader = DataLoader(TensorDataset(torch.from_numpy(windows)), batch_size=batch_size, shuffle=False)
    reconstructions: list[np.ndarray] = []
    latents: list[np.ndarray] = []
    model.eval()
    for (batch,) in loader:
        batch = batch.to(device=device, dtype=torch.float32)
        reconstructed, latent = model(batch)
        reconstructions.append(reconstructed.cpu().numpy())
        latents.append(latent.cpu().numpy())
    return np.concatenate(reconstructions), np.concatenate(latents)


def reconstruction_statistics(windows: np.ndarray, reconstructions: np.ndarray) -> dict[str, np.ndarray]:
    residual = reconstructions - windows
    squared = residual**2
    temporal_profile = squared.mean(axis=2)
    topk = max(1, int(round(0.05 * temporal_profile.shape[1])))
    topk_temporal_error = np.sort(temporal_profile, axis=1)[:, -topk:].mean(axis=1)
    window_error = squared.mean(axis=(1, 2))
    return {
        "window_error": window_error,
        "per_feature_error": squared.mean(axis=1),
        "temporal_profile": temporal_profile,
        "peak_temporal_error": temporal_profile.max(axis=1),
        "topk_temporal_error": topk_temporal_error,
        "anomaly_score": 0.55 * window_error + 0.45 * topk_temporal_error,
        "residual": residual,
    }


def compute_thresholds(normal_errors: np.ndarray, cfg: ThresholdConfig) -> dict[str, float]:
    normal_errors = np.asarray(normal_errors, dtype=np.float64)
    rolling = pd.Series(normal_errors).rolling(cfg.adaptive_window, min_periods=max(3, cfg.adaptive_window // 4))
    adaptive_baseline = rolling.median().fillna(float(np.median(normal_errors))).to_numpy()
    adaptive_mad = np.median(np.abs(normal_errors - np.median(normal_errors))) + 1e-9
    return {
        "percentile": float(np.percentile(normal_errors, cfg.percentile)),
        "percentile_value": float(cfg.percentile),
        "mean_plus_std": float(normal_errors.mean() + cfg.std_multiplier * normal_errors.std()),
        "mean": float(normal_errors.mean()),
        "std": float(normal_errors.std()),
        "adaptive_global": float(np.median(adaptive_baseline) + cfg.adaptive_z * 1.4826 * adaptive_mad),
        "adaptive_z": float(cfg.adaptive_z),
    }


def predict_anomalies(errors: np.ndarray, threshold: float) -> np.ndarray:
    return (np.asarray(errors) >= threshold).astype(np.int64)


def score_groups(
    model: torch.nn.Module,
    groups: dict[str, np.ndarray],
    batch_size: int,
    device: torch.device,
) -> dict[str, dict[str, np.ndarray]]:
    output: dict[str, dict[str, np.ndarray]] = {}
    for name, windows in groups.items():
        recon, latent = reconstruct_windows(model, windows, batch_size, device)
        stats = reconstruction_statistics(windows, recon)
        output[name] = {
            "windows": windows,
            "reconstructions": recon,
            "latents": latent,
            **stats,
        }
    return output


def build_score_table(results: dict[str, dict[str, np.ndarray]], threshold: float) -> pd.DataFrame:
    rows: list[pd.DataFrame] = []
    for group, values in results.items():
        errors = values["window_error"]
        frame = pd.DataFrame(
            {
                "group": group,
                "window_index": np.arange(len(errors)),
                "reconstruction_error": errors,
                "peak_temporal_error": values["peak_temporal_error"],
                "topk_temporal_error": values["topk_temporal_error"],
                "anomaly_score": values["anomaly_score"],
                "is_crash": (group == "synthetic_crash").astype(int) if isinstance(group, np.ndarray) else int(group == "synthetic_crash"),
                "is_anomaly_class": int(group != "normal"),
                "predicted_anomaly": predict_anomalies(values["anomaly_score"], threshold),
            }
        )
        for idx, feature in enumerate(FEATURE_COLUMNS):
            frame[f"{feature}_reconstruction_error"] = values["per_feature_error"][:, idx]
        rows.append(frame)
    return pd.concat(rows, ignore_index=True)


def evaluate_anomaly_separation(score_table: pd.DataFrame, threshold: float) -> dict[str, float]:
    y_true = score_table["is_anomaly_class"].to_numpy(dtype=int)
    y_crash = score_table["is_crash"].to_numpy(dtype=int)
    scores = score_table["anomaly_score"].to_numpy()
    preds = (scores >= threshold).astype(int)
    metrics = {
        "threshold": float(threshold),
        "precision_anomaly": float(precision_score(y_true, preds, zero_division=0)),
        "recall_anomaly": float(recall_score(y_true, preds, zero_division=0)),
        "f1_anomaly": float(f1_score(y_true, preds, zero_division=0)),
        "precision_crash": float(precision_score(y_crash, preds, zero_division=0)),
        "recall_crash": float(recall_score(y_crash, preds, zero_division=0)),
        "f1_crash": float(f1_score(y_crash, preds, zero_division=0)),
    }
    if len(np.unique(y_true)) > 1:
        metrics["roc_auc_anomaly"] = float(roc_auc_score(y_true, scores))
    if len(np.unique(y_crash)) > 1:
        metrics["roc_auc_crash"] = float(roc_auc_score(y_crash, scores))
    return metrics


def save_anomaly_outputs(
    results: dict[str, dict[str, np.ndarray]],
    score_table: pd.DataFrame,
    thresholds: dict[str, float],
    metrics: dict[str, float],
    output_dir: Path,
) -> None:
    output_dir = ensure_dir(output_dir)
    score_table.to_csv(output_dir / "anomaly_scores.csv", index=False)
    with (output_dir / "thresholds.json").open("w", encoding="utf-8") as handle:
        json.dump(thresholds, handle, indent=2)
    with (output_dir / "anomaly_metrics.json").open("w", encoding="utf-8") as handle:
        json.dump(metrics, handle, indent=2)
    for group, values in results.items():
        np.save(output_dir / f"{group}_temporal_profiles.npy", values["temporal_profile"])
        np.save(output_dir / f"{group}_latents.npy", values["latents"])


def plot_error_distributions(score_table: pd.DataFrame, output_dir: Path) -> None:
    plot_dir = ensure_dir(output_dir / "plots")
    fig, ax = plt.subplots(figsize=(10, 5))
    for group, subset in score_table.groupby("group"):
        ax.hist(subset["anomaly_score"], bins=40, alpha=0.45, density=True, label=group)
    ax.set_xlabel("impact-aware anomaly score")
    ax.set_ylabel("density")
    ax.legend()
    ax.grid(alpha=0.25)
    fig.tight_layout()
    fig.savefig(plot_dir / "reconstruction_error_distributions.png", dpi=160)
    plt.close(fig)


def plot_input_vs_reconstruction(results: dict[str, dict[str, np.ndarray]], output_dir: Path, max_groups: int = 4) -> None:
    plot_dir = ensure_dir(output_dir / "plots")
    for group, values in list(results.items())[:max_groups]:
        idx = int(np.argmax(values["window_error"]))
        fig, axes = plt.subplots(2, 1, figsize=(12, 6), sharex=True)
        t = np.arange(values["windows"].shape[1])
        axes[0].plot(t, np.linalg.norm(values["windows"][idx, :, :3], axis=1), label="input")
        axes[0].plot(t, np.linalg.norm(values["reconstructions"][idx, :, :3], axis=1), label="reconstruction")
        axes[0].set_ylabel("accel magnitude")
        axes[0].legend()
        axes[0].grid(alpha=0.25)
        axes[1].plot(t, np.linalg.norm(values["windows"][idx, :, 3:], axis=1), label="input")
        axes[1].plot(t, np.linalg.norm(values["reconstructions"][idx, :, 3:], axis=1), label="reconstruction")
        axes[1].set_ylabel("gyro magnitude")
        axes[1].set_xlabel("timestep")
        axes[1].grid(alpha=0.25)
        fig.suptitle(f"Input vs reconstruction: {group}")
        fig.tight_layout()
        fig.savefig(plot_dir / f"input_vs_reconstruction_{group}.png", dpi=160)
        plt.close(fig)


def plot_latent_pca(results: dict[str, dict[str, np.ndarray]], output_dir: Path) -> None:
    plot_dir = ensure_dir(output_dir / "plots")
    latents = []
    labels = []
    for group, values in results.items():
        latents.append(values["latents"])
        labels.extend([group] * len(values["latents"]))
    latent_matrix = np.concatenate(latents)
    coords = PCA(n_components=2, random_state=42).fit_transform(latent_matrix)
    frame = pd.DataFrame({"pc1": coords[:, 0], "pc2": coords[:, 1], "group": labels})
    fig, ax = plt.subplots(figsize=(8, 6))
    for group, subset in frame.groupby("group"):
        ax.scatter(subset["pc1"], subset["pc2"], s=14, alpha=0.65, label=group)
    ax.set_xlabel("PC1")
    ax.set_ylabel("PC2")
    ax.legend()
    ax.grid(alpha=0.2)
    fig.tight_layout()
    fig.savefig(plot_dir / "latent_pca_separation.png", dpi=160)
    plt.close(fig)
    frame.to_csv(output_dir / "latent_pca_coordinates.csv", index=False)
