# P1 Edge AI Model Asset

Put the TensorFlow Lite model file here:

- File name: `sleep_model.tflite`
- Relative path: `app/src/main/assets/ml/sleep_model.tflite`

The Android app loads this file from `SleepAnomalyDetector.DEFAULT_MODEL_ASSET_PATH`.
If missing, app will fallback to rule-based scoring.
