"""
models/autoencoder.py
=====================
LSTM Autoencoder for normal driving reconstruction.

Architecture
------------
Encoder: stacked LSTM → compresses T timesteps to a fixed latent vector z.
Decoder: repeat z across T timesteps → stacked LSTM → reconstruct signal.

Anomaly detection principle
----------------------------
The autoencoder is trained ONLY on normal driving data.
It learns to faithfully reconstruct normal IMU patterns.
When a crash (out-of-distribution signal) is fed in, reconstruction
quality degrades sharply → high reconstruction error = anomaly.

The reconstruction error (MSE per window) serves as the anomaly score.
"""

import torch
import torch.nn as nn


class LSTMEncoder(nn.Module):
    """
    Stacked LSTM encoder.
    Compresses [batch, T, n_features] → [batch, latent_size].
    Uses last hidden state of the final LSTM layer as the latent vector.
    """

    def __init__(self, input_size: int, hidden_size: int,
                 latent_size: int, num_layers: int, dropout: float):
        super().__init__()
        self.lstm = nn.LSTM(
            input_size  = input_size,
            hidden_size = hidden_size,
            num_layers  = num_layers,
            batch_first = True,
            dropout     = dropout if num_layers > 1 else 0.0,
        )
        self.fc = nn.Linear(hidden_size, latent_size)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: [B, T, input_size]
        Returns:
            z: [B, latent_size]
        """
        _, (h_n, _) = self.lstm(x)   # h_n: [num_layers, B, hidden_size]
        h_last = h_n[-1]              # last layer hidden state: [B, hidden_size]
        z = self.fc(h_last)           # [B, latent_size]
        return z


class LSTMDecoder(nn.Module):
    """
    Stacked LSTM decoder.
    Expands [batch, latent_size] → [batch, T, n_features].
    The latent vector is repeated T times to seed the decoder LSTM.
    """

    def __init__(self, latent_size: int, hidden_size: int,
                 output_size: int, num_layers: int, dropout: float,
                 seq_len: int):
        super().__init__()
        self.seq_len = seq_len
        self.fc_in   = nn.Linear(latent_size, hidden_size)
        self.lstm    = nn.LSTM(
            input_size  = hidden_size,
            hidden_size = hidden_size,
            num_layers  = num_layers,
            batch_first = True,
            dropout     = dropout if num_layers > 1 else 0.0,
        )
        self.fc_out  = nn.Linear(hidden_size, output_size)

    def forward(self, z: torch.Tensor) -> torch.Tensor:
        """
        Args:
            z: [B, latent_size]
        Returns:
            x_hat: [B, T, output_size]
        """
        # Project latent vector to hidden size and repeat for all T steps
        h0 = self.fc_in(z)                       # [B, hidden_size]
        h0 = h0.unsqueeze(1).repeat(1, self.seq_len, 1)  # [B, T, hidden_size]
        out, _ = self.lstm(h0)                    # [B, T, hidden_size]
        x_hat  = self.fc_out(out)                 # [B, T, output_size]
        return x_hat


class LSTMAutoencoder(nn.Module):
    """
    Full LSTM autoencoder: Encoder + Decoder.
    Used for unsupervised anomaly detection via reconstruction error.
    """

    def __init__(self, n_features: int, hidden_size: int,
                 latent_size: int, num_layers: int,
                 dropout: float, seq_len: int):
        super().__init__()
        self.encoder = LSTMEncoder(
            input_size  = n_features,
            hidden_size = hidden_size,
            latent_size = latent_size,
            num_layers  = num_layers,
            dropout     = dropout,
        )
        self.decoder = LSTMDecoder(
            latent_size = latent_size,
            hidden_size = hidden_size,
            output_size = n_features,
            num_layers  = num_layers,
            dropout     = dropout,
            seq_len     = seq_len,
        )

    def forward(self, x: torch.Tensor):
        """
        Args:
            x: [B, T, n_features]
        Returns:
            x_hat: [B, T, n_features]  reconstructed signal
        """
        z     = self.encoder(x)
        x_hat = self.decoder(z)
        return x_hat

    def encode(self, x: torch.Tensor) -> torch.Tensor:
        """Return the latent embedding only (useful for PCA/t-SNE)."""
        return self.encoder(x)

    @staticmethod
    def reconstruction_error(x: torch.Tensor, x_hat: torch.Tensor,
                             reduction: str = "mean") -> torch.Tensor:
        """
        Mean squared error per window.

        Args:
            x, x_hat : [B, T, F]
            reduction : 'mean' → scalar per window [B]
                        'none' → per-feature error [B, F]
        Returns:
            error: [B] or [B, F]
        """
        se = (x - x_hat) ** 2          # [B, T, F]
        if reduction == "none":
            return se.mean(dim=1)       # average over T → [B, F]
        return se.mean(dim=(1, 2))      # average over T and F → [B]


def load_autoencoder(checkpoint_path: str, config: dict,
                     device: torch.device) -> LSTMAutoencoder:
    """
    Load a trained autoencoder from a .pt checkpoint.

    Args:
        checkpoint_path : path to saved state_dict
        config          : dict with hidden_size, latent_size, num_layers, dropout
        device          : torch device

    Returns:
        model in eval mode on device
    """
    from config.config import N_FEATURES, WINDOW_SIZE

    model = LSTMAutoencoder(
        n_features  = N_FEATURES,
        hidden_size = config["hidden_size"],
        latent_size = config["latent_size"],
        num_layers  = config["num_layers"],
        dropout     = config["dropout"],
        seq_len     = WINDOW_SIZE,
    ).to(device)

    state = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(state)
    model.eval()
    return model
