---
title: Sleep Transformer V2
emoji: "\U0001F4A4"
colorFrom: indigo
colorTo: purple
sdk: docker
pinned: false
---

# Sleep Transformer V2

ONNX-based sleep stage classification service.

- **Input**: 17-dimensional aggregated sleep features per window
- **Output**: Sleep stage (WAKE/N1/N2/N3/REM) with confidence + anomaly score
- **Model**: ~490KB ONNX, ~200K parameters, CPU-only

## Runtime contract
- `POST /`
- Request body: `{ "modelVersion": "...", "featureSchemaVersion": "v1", "windows": [...] }`
- Response body: `{ "stages": [...], "anomalyScore": 0.12, "factors": [...], "insights": [...] }`
- `GET /health` returns model load status

## Auth
- Set `API_TOKEN` in the Space or local environment
- `cloud-next` should send `Authorization: Bearer <MODEL_INFERENCE_TOKEN>`

## Local run
```bash
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 7860
```

## Docker / Hugging Face
- The included `Dockerfile` is ready for Docker Space deployment
- Keep `sleep-transformer-v2.onnx` in the repository or image
- Expose port `7860`
