"""
Sleep Transformer V2 — ONNX inference service for Hugging Face Spaces.

Accepts the same request format as the cloud's callHttpInference() and returns
stages / anomalyScore / factors / insights.
"""

import math
import os
from contextlib import asynccontextmanager
from typing import Any

import numpy as np
import onnxruntime as ort
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

# ---------------------------------------------------------------------------
# Globals
# ---------------------------------------------------------------------------

MODEL_PATH = os.path.join(os.path.dirname(__file__), "sleep-transformer-v2.onnx")
API_TOKEN = os.environ.get("API_TOKEN", "")
STAGE_ORDER = ["WAKE", "N1", "N2", "N3", "REM"]

session: ort.InferenceSession | None = None


# ---------------------------------------------------------------------------
# Lifespan — load model once at startup
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(_app: FastAPI):
    global session
    session = ort.InferenceSession(
        MODEL_PATH,
        providers=["CPUExecutionProvider"],
    )
    yield


app = FastAPI(title="Sleep Transformer V2", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _num(obj: dict | None, key: str, default: float = 0.0) -> float:
    """Safely extract a finite float from a dict."""
    if obj is None:
        return default
    v = obj.get(key)
    if v is None:
        return default
    try:
        f = float(v)
        return f if math.isfinite(f) else default
    except (TypeError, ValueError):
        return default


def _as_dict(value: Any) -> dict:
    return value if isinstance(value, dict) else {}


def extract_features(window: dict) -> list[float]:
    """
    Extract 17-dim feature vector from a single window.

    Order matches cloud-next/src/lib/local-baseline-inference.ts vectorize():
      hr(4) + spo2(3) + hrv(4) + temp(3) + motion(1) + ppg(1) + edge(1)
    """
    hr = _as_dict(window.get("hr_features"))
    spo2 = _as_dict(window.get("spo2_features"))
    hrv = _as_dict(window.get("hrv_features"))
    temp = _as_dict(window.get("temp_features"))
    motion = _as_dict(window.get("motion_features"))
    ppg = _as_dict(window.get("ppg_features"))

    return [
        _num(hr, "heartRate"),
        _num(hr, "heartRateAvg"),
        _num(hr, "heartRateMin"),
        _num(hr, "heartRateMax"),
        _num(spo2, "bloodOxygen"),
        _num(spo2, "bloodOxygenAvg"),
        _num(spo2, "bloodOxygenMin"),
        _num(hrv, "hrv"),
        _num(hrv, "rmssd"),
        _num(hrv, "sdnn"),
        _num(hrv, "lfHfRatio"),
        _num(temp, "temperature"),
        _num(temp, "temperatureAvg"),
        _num(temp, "temperatureTrend"),
        _num(motion, "motionIntensity"),
        _num(ppg, "ppgValue"),
        _num(window, "edge_anomaly_signal"),
    ]


def softmax(logits: np.ndarray) -> np.ndarray:
    """Row-wise softmax."""
    shifted = logits - logits.max(axis=1, keepdims=True)
    exp = np.exp(shifted)
    return exp / exp.sum(axis=1, keepdims=True)


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok", "model_loaded": session is not None}


@app.post("/")
async def predict(request: Request):
    # --- Auth ---
    if API_TOKEN:
        auth = request.headers.get("authorization", "")
        token = auth.removeprefix("Bearer ").strip()
        if token != API_TOKEN:
            raise HTTPException(status_code=401, detail="unauthorized")

    # --- Parse body ---
    body = await request.json()
    windows: list[dict] = body.get("windows", [])
    if not windows:
        raise HTTPException(status_code=400, detail="no windows provided")

    # --- Build [B, 17] input ---
    features = np.array(
        [extract_features(w) for w in windows],
        dtype=np.float32,
    )

    # --- ONNX inference (normalization baked into model) ---
    assert session is not None
    stage_logits, anomaly_scores = session.run(None, {"features": features})

    # --- Decode ---
    probs = softmax(stage_logits)
    stages = []
    for i in range(len(windows)):
        idx = int(np.argmax(probs[i]))
        stages.append({
            "stage": STAGE_ORDER[idx],
            "confidence": round(float(np.clip(probs[i][idx], 0.0, 1.0)), 4),
            "windowIndex": windows[i].get("windowIndex", i),
        })

    mean_anomaly = float(np.clip(np.mean(anomaly_scores), 0.0, 1.0))

    # --- Generate factors & insights ---
    wake_n1_ratio = sum(1 for s in stages if s["stage"] in ("WAKE", "N1")) / max(len(stages), 1)
    factors: list[str] = []
    insights: list[str] = []

    if mean_anomaly >= 0.5:
        factors.append("elevated_anomaly_signal")
        insights.append("Model detected elevated nocturnal physiological variability.")
    if wake_n1_ratio >= 0.4:
        factors.append("high_wake_ratio")
        insights.append("Frequent awakenings or light sleep detected.")
    if not factors:
        factors.append("stable_stage_pattern")
        insights.append("Sleep staging appears stable; continue multi-night monitoring.")

    return JSONResponse({
        "stages": stages,
        "anomalyScore": round(mean_anomaly, 4),
        "factors": factors,
        "insights": insights,
    })
