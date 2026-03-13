"""Lightweight Transformer for sleep stage classification using aggregated features.

Extends MultiModalEpochTransformer with an anomaly detection head.
Input: 7 modality groups totaling 17 dimensions (aggregated stats only).
Output: (stage_logits [B, 5], anomaly_score [B, 1]).
"""

from __future__ import annotations

import torch
from torch import nn

MODALITY_NAMES = ["hr", "spo2", "hrv", "temp", "motion", "ppg", "edge"]
MODALITY_DIMS = {
    "hr": 4,
    "spo2": 3,
    "hrv": 4,
    "temp": 3,
    "motion": 1,
    "ppg": 1,
    "edge": 1,
}
TOTAL_DIMS = sum(MODALITY_DIMS.values())  # 17
STAGE_ORDER = ["WAKE", "N1", "N2", "N3", "REM"]


class AggregatedSleepTransformer(nn.Module):
    """Multi-modal Transformer using only aggregated physiological features."""

    def __init__(
        self,
        d_model: int = 64,
        num_heads: int = 4,
        num_layers: int = 2,
        dropout: float = 0.15,
        num_classes: int = 5,
    ):
        super().__init__()
        self.d_model = d_model

        self.projectors = nn.ModuleDict(
            {
                name: nn.Sequential(
                    nn.Linear(MODALITY_DIMS[name], d_model),
                    nn.LayerNorm(d_model),
                    nn.GELU(),
                )
                for name in MODALITY_NAMES
            }
        )

        self.cls_token = nn.Parameter(torch.zeros(1, 1, d_model))
        self.pos_embedding = nn.Parameter(
            torch.zeros(1, len(MODALITY_NAMES) + 1, d_model)
        )

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=num_heads,
            dim_feedforward=d_model * 4,
            dropout=dropout,
            activation="gelu",
            batch_first=True,
        )
        self.encoder = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)

        self.classifier = nn.Sequential(
            nn.LayerNorm(d_model),
            nn.Dropout(dropout),
            nn.Linear(d_model, num_classes),
        )

        self.anomaly_head = nn.Sequential(
            nn.LayerNorm(d_model),
            nn.Dropout(dropout),
            nn.Linear(d_model, 1),
            nn.Sigmoid(),
        )

    def forward(
        self, batch: dict[str, torch.Tensor]
    ) -> tuple[torch.Tensor, torch.Tensor]:
        tokens = [self.projectors[name](batch[name]) for name in MODALITY_NAMES]
        x = torch.stack(tokens, dim=1)
        bsz = x.shape[0]
        cls = self.cls_token.expand(bsz, -1, -1)
        x = torch.cat([cls, x], dim=1)
        x = x + self.pos_embedding[:, : x.shape[1], :]
        encoded = self.encoder(x)
        cls_out = encoded[:, 0, :]
        stage_logits = self.classifier(cls_out)
        anomaly_score = self.anomaly_head(cls_out)
        return stage_logits, anomaly_score


class OnnxExportWrapper(nn.Module):
    """Wrapper that accepts flat [B, 17] input with baked-in normalization."""

    def __init__(
        self,
        model: AggregatedSleepTransformer,
        means: torch.Tensor,
        stds: torch.Tensor,
    ):
        super().__init__()
        self.model = model
        self.register_buffer("means", means)
        self.register_buffer("stds", stds)
        self._splits = [MODALITY_DIMS[name] for name in MODALITY_NAMES]

    def forward(self, features: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        normalized = (features - self.means) / self.stds
        parts = torch.split(normalized, self._splits, dim=1)
        batch = {name: parts[i] for i, name in enumerate(MODALITY_NAMES)}
        return self.model(batch)
