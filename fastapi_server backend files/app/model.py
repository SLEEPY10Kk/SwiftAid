from __future__ import annotations

import torch
from torch import nn


class LSTMAutoencoder(nn.Module):
    """
    LSTM autoencoder for multivariate IMU reconstruction.

    Expected input shape:
        [batch, timesteps, features]
    """

    def __init__(
        self,
        input_size: int,
        hidden_size: int = 128,
        latent_size: int = 32,
        num_layers: int = 1,
        dropout: float = 0.0,
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
        decoder_input = decoder_seed.unsqueeze(1).repeat(1, timesteps, 1)
        decoded_sequence, _ = self.decoder(decoder_input)
        return self.output_layer(decoded_sequence)

    def forward(self, x: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        latent = self.encode(x)
        reconstruction = self.decode(latent, timesteps=x.size(1))
        return reconstruction, latent
