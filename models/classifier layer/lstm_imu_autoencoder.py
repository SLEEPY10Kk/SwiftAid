from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np
from scipy.spatial.transform import Rotation
import torch
from torch import nn
from torch.utils.data import DataLoader, Dataset
import matplotlib.pyplot as plt


WINDOWS_PATH = Path(__file__).resolve().parent / "filtered_imu_windows.npy"
OUTPUT_DIR = Path(__file__).resolve().parent / "lstm_autoencoder_outputs"
BEST_CHECKPOINT_PATH = OUTPUT_DIR / "best_lstm_autoencoder.pt"
LAST_CHECKPOINT_PATH = OUTPUT_DIR / "last_lstm_autoencoder.pt"


# -----------------------------
# CONFIG
# -----------------------------
@dataclass(frozen=True)
class TrainConfig:
    input_size: int = 6
    hidden_size: int = 64
    latent_size: int = 16
    num_layers: int = 2
    dropout: float = 0.2

    batch_size: int = 64
    learning_rate: float = 1e-3
    weight_decay: float = 1e-5
    max_epochs: int = 100

    train_fraction: float = 0.7
    val_fraction: float = 0.15
    seed: int = 42

    enable_augmentations: bool = True
    noise_probability: float = 0.5
    rotation_probability: float = 0.5
    gaussian_noise_sigma: float = 0.01
    max_rotation_degrees: float = 15.0


# -----------------------------
# MODEL
# -----------------------------
class LSTMAutoencoder(nn.Module):
    def __init__(
        self,
        input_size: int,
        hidden_size: int,
        latent_size: int,
        num_layers: int,
        dropout: float,
    ) -> None:
        super().__init__()

        lstm_dropout = dropout if num_layers > 1 else 0.0

        self.encoder = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            dropout=lstm_dropout,
        )

        self.to_latent = nn.Linear(hidden_size, latent_size)
        self.from_latent = nn.Linear(latent_size, hidden_size)

        self.decoder = nn.LSTM(
            input_size=hidden_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            dropout=lstm_dropout,
        )

        self.output_layer = nn.Linear(hidden_size, input_size)

    def encode(self, x: torch.Tensor) -> torch.Tensor:
        _, (h, _) = self.encoder(x)
        return self.to_latent(h[-1])

    def decode(self, z: torch.Tensor, timesteps: int) -> torch.Tensor:
        base = self.from_latent(z).unsqueeze(1).expand(-1, timesteps, -1)

        # FIX: break deterministic collapse
        base = base + torch.randn_like(base) * 0.01

        out, _ = self.decoder(base)
        return self.output_layer(out)

    def forward(self, x: torch.Tensor):
        z = self.encode(x)
        recon = self.decode(z, x.size(1))
        return recon, z


# -----------------------------
# AUGMENTATION
# -----------------------------
def add_noise(x, sigma, rng):
    return x + rng.normal(0, sigma, x.shape).astype(np.float32)


def rotate(x, max_deg, rng):
    axis = rng.normal(size=3)
    axis /= np.linalg.norm(axis) + 1e-8
    angle = np.deg2rad(rng.uniform(-max_deg, max_deg))
    R = Rotation.from_rotvec(axis * angle).as_matrix().astype(np.float32)

    y = x.copy()
    y[:, :3] = y[:, :3] @ R.T
    y[:, 3:] = y[:, 3:] @ R.T
    return y


class IMUAugmentation:
    def __init__(self, cfg: TrainConfig):
        self.cfg = cfg
        self.rng = np.random.default_rng(cfg.seed)

    def __call__(self, x):
        x = x.astype(np.float32)

        if self.rng.random() < self.cfg.rotation_probability:
            x = rotate(x, self.cfg.max_rotation_degrees, self.rng)

        if self.rng.random() < self.cfg.noise_probability:
            x = add_noise(x, self.cfg.gaussian_noise_sigma, self.rng)

        return x


# -----------------------------
# DATASET
# -----------------------------
class IMUDataset(Dataset):
    def __init__(self, windows, augment=False, aug=None):
        self.windows = windows.astype(np.float32)
        self.augment = augment
        self.aug = aug

    def __len__(self):
        return len(self.windows)

    def __getitem__(self, i):
        x = self.windows[i]
        if self.augment and self.aug:
            x = self.aug(x)
        return torch.from_numpy(x)


# -----------------------------
# SPLIT
# -----------------------------
def split(w, tr, va):
    n = len(w)
    t = int(n * tr)
    v = int(n * va)
    return w[:t], w[t:t+v], w[t+v:]


# -----------------------------
# TRAIN / EVAL
# -----------------------------
def train_epoch(model, loader, opt, loss_fn, device):
    model.train()
    total = 0

    for x in loader:
        x = x.to(device)
        opt.zero_grad()

        y, _ = model(x)
        loss = loss_fn(y, x)

        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        opt.step()

        total += loss.item() * x.size(0)

    return total / len(loader.dataset)


@torch.no_grad()
def eval_epoch(model, loader, loss_fn, device):
    model.eval()
    total = 0

    for x in loader:
        x = x.to(device)
        y, _ = model(x)
        loss = loss_fn(y, x)
        total += loss.item() * x.size(0)

    return total / len(loader.dataset)


@torch.no_grad()
def errors(model, windows, device, bs=64):
    loader = DataLoader(IMUDataset(windows), batch_size=bs)
    model.eval()

    out = []
    for x in loader:
        x = x.to(device)
        y, _ = model(x)
        e = ((y - x) ** 2).mean(dim=(1, 2))
        out.append(e.cpu().numpy())

    return np.concatenate(out)


# -----------------------------
# CHECKPOINT
# -----------------------------
def save(path, model, opt, epoch, val):
    torch.save({
        "model": model.state_dict(),
        "opt": opt.state_dict(),
        "epoch": epoch,
        "val": val
    }, path)


def load(model, path, device):
    ckpt = torch.load(path, map_location=device)
    model.load_state_dict(ckpt["model"])
    model.to(device).eval()
    return ckpt


# -----------------------------
# DIAGNOSTICS
# -----------------------------
def print_stats(e):
    print("Reconstruction stats:")
    print(" mean:", e.mean())
    print(" std :", e.std())
    print(" p95 :", np.percentile(e, 95))
    print(" p99 :", np.percentile(e, 99))


# -----------------------------
# MAIN
# -----------------------------
def main():
    cfg = TrainConfig()
    torch.manual_seed(cfg.seed)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    windows = np.load(WINDOWS_PATH).astype(np.float32)
    tr, va, te = split(windows, cfg.train_fraction, cfg.val_fraction)

    aug = IMUAugmentation(cfg) if cfg.enable_augmentations else None

    train_loader = DataLoader(IMUDataset(tr, True, aug), batch_size=cfg.batch_size, shuffle=True)
    val_loader = DataLoader(IMUDataset(va), batch_size=cfg.batch_size)

    model = LSTMAutoencoder(
        cfg.input_size,
        cfg.hidden_size,
        cfg.latent_size,
        cfg.num_layers,
        cfg.dropout
    ).to(device)

    opt = torch.optim.AdamW(model.parameters(), lr=cfg.learning_rate, weight_decay=cfg.weight_decay)
    loss_fn = nn.MSELoss()

    best = float("inf")

    for epoch in range(cfg.max_epochs):
        tr_loss = train_epoch(model, train_loader, opt, loss_fn, device)
        va_loss = eval_epoch(model, val_loader, loss_fn, device)

        if va_loss < best:
            best = va_loss
            save(BEST_CHECKPOINT_PATH, model, opt, epoch, va_loss)

        print(f"Epoch {epoch} | train {tr_loss:.4f} | val {va_loss:.4f}")

    load(model, BEST_CHECKPOINT_PATH, device)

    test_errors = errors(model, te, device)
    print_stats(test_errors)

    OUTPUT_DIR.mkdir(exist_ok=True)
    np.save(OUTPUT_DIR / "test_errors.npy", test_errors)

    plt.hist(test_errors, bins=80)
    plt.savefig(OUTPUT_DIR / "hist.png")


if __name__ == "__main__":
    main()