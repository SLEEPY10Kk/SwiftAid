from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
import uuid

import numpy as np
import pandas as pd

from .utils import ensure_dir


def save_confirmed_crash_events(
    windows: np.ndarray,
    confirmations: pd.DataFrame,
    event_dir: Path,
    model_version: str,
    source: str = "offline_batch",
) -> pd.DataFrame:
    """Persist confirmed crash windows and metadata for later offline review/retraining."""
    event_dir = ensure_dir(event_dir)
    confirmed = confirmations[confirmations["confirmed_crash"] == 1].copy()
    rows: list[dict[str, object]] = []

    for _, row in confirmed.iterrows():
        event_id = str(uuid.uuid4())
        window_index = int(row.get("window_index", 0))
        window_path = event_dir / f"{event_id}.npy"
        np.save(window_path, windows[window_index].astype(np.float32))
        rows.append(
            {
                "event_id": event_id,
                "created_at_utc": datetime.now(timezone.utc).isoformat(),
                "source": source,
                "model_version": model_version,
                "window_index": window_index,
                "window_path": str(window_path),
                "anomaly_score": float(row.get("reconstruction_error", np.nan)),
                "crash_probability": float(row.get("crash_probability", np.nan)),
                "crash_threshold": float(row.get("crash_threshold", np.nan)),
                "human_review_status": "pending",
                "use_for_autoencoder_training": False,
                "use_for_classifier_retraining": True,
            }
        )

    events = pd.DataFrame(rows)
    log_path = event_dir / "confirmed_crash_event_log.csv"
    if log_path.exists() and not events.empty:
        previous = pd.read_csv(log_path)
        events = pd.concat([previous, events], ignore_index=True)
    events.to_csv(log_path, index=False)
    return events
