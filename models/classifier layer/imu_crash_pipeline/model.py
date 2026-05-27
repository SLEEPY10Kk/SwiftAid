from __future__ import annotations

from pathlib import Path

import torch
from torch import nn

from .config import ModelConfig


class LSTMAutoencoder(nn.Module):
    """LSTM autoencoder for multivariate IMU windows shaped [batch, time, features]."""

    def __init__(
        self,
        input_size: int,
        hidden_size: int = 64,
        latent_size: int = 16,
        num_layers: int = 2,
        dropout: float = 0.2,
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
        _, (hidden, _) = self.encoder(x)
        return self.to_latent(hidden[-1])

    def decode(self, latent: torch.Tensor, timesteps: int) -> torch.Tensor:
        decoder_seed = self.from_latent(latent)
        decoder_input = decoder_seed.unsqueeze(1).expand(-1, timesteps, -1)
        decoded_sequence, _ = self.decoder(decoder_input)
        return self.output_layer(decoded_sequence)

    def forward(self, x: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        latent = self.encode(x)
        reconstruction = self.decode(latent, timesteps=x.size(1))
        return reconstruction, latent


def _extract_state_dict(checkpoint: object) -> dict[str, torch.Tensor]:
    if isinstance(checkpoint, dict):
        for key in ("model", "model_state_dict", "state_dict"):
            state = checkpoint.get(key)
            if isinstance(state, dict):
                return state
        if all(torch.is_tensor(v) for v in checkpoint.values()):
            return checkpoint
    raise ValueError("Unsupported checkpoint format. Expected model/model_state_dict/state_dict.")


def load_autoencoder(
    checkpoint_path: Path,
    config: ModelConfig,
    device: torch.device,
) -> LSTMAutoencoder:
    model = LSTMAutoencoder(
        input_size=config.input_size,
        hidden_size=config.hidden_size,
        latent_size=config.latent_size,
        num_layers=config.num_layers,
        dropout=config.dropout,
    ).to(device)
    checkpoint = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(_extract_state_dict(checkpoint))
    model.eval()
    return model
