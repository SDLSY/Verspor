"""Export AggregatedSleepTransformer to ONNX with baked-in normalization.

Usage:
    python export_aggregated_onnx.py \
        --checkpoint ./artifacts/week2/aggregated_transformer/best_model.pth \
        --norm_stats ./artifacts/week2/aggregated_transformer/normalization_stats.json \
        --output ./artifacts/week2/aggregated_transformer/sleep-transformer-v2.onnx
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import torch

from AggregatedTransformer import (
    MODALITY_DIMS,
    MODALITY_NAMES,
    TOTAL_DIMS,
    AggregatedSleepTransformer,
    OnnxExportWrapper,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export model to ONNX")
    _ = parser.add_argument(
        "--checkpoint",
        type=str,
        default="./artifacts/week2/aggregated_transformer/best_model.pth",
    )
    _ = parser.add_argument(
        "--norm_stats",
        type=str,
        default="./artifacts/week2/aggregated_transformer/normalization_stats.json",
    )
    _ = parser.add_argument(
        "--output",
        type=str,
        default="./artifacts/week2/aggregated_transformer/sleep-transformer-v2.onnx",
    )
    _ = parser.add_argument("--d_model", type=int, default=64)
    _ = parser.add_argument("--num_heads", type=int, default=4)
    _ = parser.add_argument("--num_layers", type=int, default=2)
    _ = parser.add_argument("--dropout", type=float, default=0.15)
    _ = parser.add_argument("--opset", type=int, default=17)
    return parser.parse_args()


def load_norm_stats(path: Path) -> tuple[torch.Tensor, torch.Tensor]:
    """Load normalization stats and concatenate into flat [17] tensors."""
    with path.open("r", encoding="utf-8") as f:
        stats = json.load(f)

    means_list: list[float] = []
    stds_list: list[float] = []
    for name in MODALITY_NAMES:
        modality = stats[name]
        means_list.extend(modality["mean"])
        stds_list.extend(modality["std"])

    means = torch.tensor(means_list, dtype=torch.float32)
    stds = torch.tensor(stds_list, dtype=torch.float32)
    assert means.shape[0] == TOTAL_DIMS, f"Expected {TOTAL_DIMS} dims, got {means.shape[0]}"
    return means, stds


def main() -> None:
    args = parse_args()
    checkpoint_path = Path(str(args.checkpoint))
    norm_stats_path = Path(str(args.norm_stats))
    output_path = Path(str(args.output))
    output_path.parent.mkdir(parents=True, exist_ok=True)

    model = AggregatedSleepTransformer(
        d_model=int(args.d_model),
        num_heads=int(args.num_heads),
        num_layers=int(args.num_layers),
        dropout=float(args.dropout),
        num_classes=5,
    )
    state_dict = torch.load(checkpoint_path, map_location="cpu", weights_only=True)
    model.load_state_dict(state_dict)
    model.eval()

    means, stds = load_norm_stats(norm_stats_path)
    wrapper = OnnxExportWrapper(model, means, stds)
    wrapper.eval()

    dummy_input = torch.randn(1, TOTAL_DIMS)

    torch.onnx.export(
        wrapper,
        (dummy_input,),
        str(output_path),
        opset_version=int(args.opset),
        input_names=["features"],
        output_names=["stage_logits", "anomaly_score"],
        dynamic_axes={
            "features": {0: "batch"},
            "stage_logits": {0: "batch"},
            "anomaly_score": {0: "batch"},
        },
    )

    file_size_kb = output_path.stat().st_size / 1024
    print(f"ONNX model exported to {output_path} ({file_size_kb:.1f} KB)")

    # Verify output consistency with PyTorch
    try:
        import onnxruntime as ort

        session = ort.InferenceSession(str(output_path))
        test_input = torch.randn(4, TOTAL_DIMS)

        with torch.no_grad():
            pt_logits, pt_anomaly = wrapper(test_input)

        ort_result = session.run(
            None, {"features": test_input.numpy()}
        )
        ort_logits = ort_result[0]
        ort_anomaly = ort_result[1]

        logit_diff = np.abs(pt_logits.numpy() - ort_logits).max()
        anomaly_diff = np.abs(pt_anomaly.numpy() - ort_anomaly).max()
        print(f"PyTorch vs ONNX max diff: logits={logit_diff:.6f}, anomaly={anomaly_diff:.6f}")

        if logit_diff < 1e-4 and anomaly_diff < 1e-4:
            print("Verification PASSED")
        else:
            print("WARNING: Output difference exceeds threshold")
    except ImportError:
        print("onnxruntime not installed, skipping verification")


if __name__ == "__main__":
    main()
