"""
Build a tiny on-device anomaly model and export TFLite for Android.
Output: app/src/main/assets/ml/sleep_model.tflite
"""

from __future__ import annotations

import argparse
import pathlib

import numpy as np
import tensorflow as tf
from tensorflow import keras


def create_model() -> keras.Model:
    hr_input = keras.Input(shape=(4,), name="hr_features")
    spo2_input = keras.Input(shape=(3,), name="spo2_features")
    hrv_input = keras.Input(shape=(3,), name="hrv_features")

    x = keras.layers.Concatenate()([hr_input, spo2_input, hrv_input])
    x = keras.layers.Dense(32, activation="relu")(x)
    x = keras.layers.Dense(16, activation="relu")(x)
    output = keras.layers.Dense(1, activation="sigmoid", name="anomaly_score")(x)

    model = keras.Model(inputs=[hr_input, spo2_input, hrv_input], outputs=output)
    model.compile(optimizer="adam", loss="binary_crossentropy", metrics=["accuracy"])
    return model


def generate_dataset(n: int = 4000, seed: int = 42):
    rng = np.random.default_rng(seed)

    hr = np.column_stack(
        [
            rng.uniform(45, 120, size=n),
            rng.uniform(40, 100, size=n),
            rng.uniform(55, 130, size=n),
            rng.uniform(1, 25, size=n),
        ]
    ).astype(np.float32)

    spo2 = np.column_stack(
        [
            rng.uniform(82, 100, size=n),
            rng.uniform(80, 98, size=n),
            rng.uniform(84, 100, size=n),
        ]
    ).astype(np.float32)

    hrv = np.column_stack(
        [
            rng.uniform(10, 80, size=n),
            rng.uniform(8, 70, size=n),
            rng.uniform(5, 60, size=n),
        ]
    ).astype(np.float32)

    risk = np.zeros(n, dtype=np.float32)
    risk += np.clip((50 - hr[:, 0]) / 20, 0, 1)
    risk += np.clip((hr[:, 0] - 100) / 25, 0, 1)
    risk += np.clip((90 - spo2[:, 0]) / 8, 0, 1) * 1.5
    risk += np.clip((20 - hrv[:, 0]) / 20, 0, 1)
    risk += np.clip((hr[:, 3] - 12) / 12, 0, 1) * 0.3
    risk = np.clip(risk / 2.5, 0, 1)

    labels = (risk > 0.35).astype(np.float32).reshape(-1, 1)
    return hr, spo2, hrv, labels


def export_tflite(model: keras.Model, output_path: pathlib.Path):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_bytes = converter.convert()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(tflite_bytes)


def verify_tflite(output_path: pathlib.Path):
    interpreter = tf.lite.Interpreter(model_path=str(output_path))
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print("[export] input tensors:")
    for i, detail in enumerate(input_details):
        print(f"  - idx={i}, name={detail['name']}, shape={detail['shape']}")

    hr = np.array([[72.0, 64.0, 82.0, 4.0]], dtype=np.float32)
    spo2 = np.array([[98.0, 96.0, 99.0]], dtype=np.float32)
    hrv = np.array([[45.0, 35.0, 18.0]], dtype=np.float32)

    for detail in input_details:
        name = detail["name"].lower()
        index = detail["index"]
        if "hr_features" in name:
            interpreter.set_tensor(index, hr)
        elif "spo2_features" in name:
            interpreter.set_tensor(index, spo2)
        elif "hrv_features" in name:
            interpreter.set_tensor(index, hrv)

    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]["index"])
    score = float(output[0][0])

    print(f"[export] model={output_path}")
    print(f"[export] size_kb={output_path.stat().st_size / 1024:.2f}")
    print(f"[export] sample_score={score:.4f}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        default="app/src/main/assets/ml/sleep_model.tflite",
        help="output model path",
    )
    args = parser.parse_args()

    output_path = pathlib.Path(args.output)

    hr, spo2, hrv, labels = generate_dataset()
    model = create_model()
    model.fit([hr, spo2, hrv], labels, epochs=8, batch_size=64, verbose=0, validation_split=0.2)

    export_tflite(model, output_path)
    verify_tflite(output_path)


if __name__ == "__main__":
    main()
