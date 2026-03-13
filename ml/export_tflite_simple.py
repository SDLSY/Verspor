"""
简化的TFLite模型导出脚本 - 适配TensorFlow 2.20
"""

import numpy as np
import tensorflow as tf
import os

print("TensorFlow版本:", tf.__version__)

def create_simple_anomaly_model():
    """创建一个简单的异常检测模型"""
    
    # 使用tf.keras而不是keras
    from tensorflow import keras
    
    # 输入层
    hr_input = keras.Input(shape=(4,), name='hr_features')
    spo2_input = keras.Input(shape=(3,), name='spo2_features')
    hrv_input = keras.Input(shape=(3,), name='hrv_features')
    
    # 特征融合
    concat = keras.layers.Concatenate()([hr_input, spo2_input, hrv_input])
    
    # 隐藏层
    x = keras.layers.Dense(32, activation='relu')(concat)
    x = keras.layers.Dropout(0.2)(x)
    x = keras.layers.Dense(16, activation='relu')(x)
    
    # 输出层
    anomaly_output = keras.layers.Dense(1, activation='sigmoid', name='anomaly_score')(x)
    
    # 创建模型
    model = keras.Model(
        inputs=[hr_input, spo2_input, hrv_input],
        outputs=anomaly_output,
        name='simple_anomaly_detector'
    )
    
    return model

# 创建输出目录
output_dir = './ml/models'
os.makedirs(output_dir, exist_ok=True)

print("\n" + "=" * 60)
print("简化TFLite模型导出")
print("=" * 60)

# 1. 创建模型
print("\n1. 创建模型...")
model = create_simple_anomaly_model()
model.summary()

# 2. 编译模型
print("\n2. 编译模型...")
model.compile(
    optimizer='adam',
    loss='binary_crossentropy',
    metrics=['accuracy']
)

# 3. 生成并训练模型
print("\n3. 快速训练模型...")
num_samples = 1000
hr_data = np.random.uniform(60, 100, size=(num_samples, 4)).astype(np.float32)
spo2_data = np.random.uniform(95, 100, size=(num_samples, 3)).astype(np.float32)
hrv_data = np.random.uniform(30, 70, size=(num_samples, 3)).astype(np.float32)

# 生成标签
labels = np.zeros((num_samples, 1), dtype=np.float32)
for i in range(num_samples):
    if spo2_data[i, 0] < 90 or hr_data[i, 0] < 50 or hr_data[i, 0] > 110:
        labels[i] = 1.0

# 训练
history = model.fit(
    [hr_data, spo2_data, hrv_data],
    labels,
    epochs=10,
    batch_size=32,
    validation_split=0.2,
    verbose=1
)

print(f"\n训练完成！最终验证准确率: {history.history['val_accuracy'][-1]:.4f}")

# 4. 导出TFLite INT8模型
print("\n4. 导出TFLite INT8模型...")

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]

# 代表性数据集
def representative_data_gen():
    for _ in range(100):
        hr = np.random.uniform(60, 100, size=(1, 4)).astype(np.float32)
        spo2 = np.random.uniform(95, 100, size=(1, 3)).astype(np.float32)
        hrv = np.random.uniform(30, 70, size=(1, 3)).astype(np.float32)
        yield [hr, spo2, hrv]

converter.representative_dataset = representative_data_gen
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
converter.inference_input_type = tf.float32
converter.inference_output_type = tf.float32

tflite_int8_model = converter.convert()

# 保存
tflite_int8_path = os.path.join(output_dir, 'sleep_model.tflite')
with open(tflite_int8_path, 'wb') as f:
    f.write(tflite_int8_model)

int8_size = os.path.getsize(tflite_int8_path) / 1024
print(f"✓ INT8模型已保存: {tflite_int8_path}")
print(f"  模型大小: {int8_size:.2f} KB")

# 5. 验证模型
print("\n5. 验证TFLite模型...")
interpreter = tf.lite.Interpreter(model_path=tflite_int8_path)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print(f"  输入数量: {len(input_details)}")
for i, detail in enumerate(input_details):
    print(f"    输入{i}: {detail['name']}, 形状: {detail['shape']}")

# 测试推理
hr_test = np.array([[70.0, 65.0, 78.0, 4.5]], dtype=np.float32)
spo2_test = np.array([[98.0, 97.0, 99.0]], dtype=np.float32)
hrv_test = np.array([[50.0, 40.0, 20.0]], dtype=np.float32)

interpreter.set_tensor(input_details[0]['index'], hr_test)
interpreter.set_tensor(input_details[1]['index'], hrv_test)
interpreter.set_tensor(input_details[2]['index'], spo2_test)

import time
start_time = time.perf_counter()
interpreter.invoke()
inference_time = (time.perf_counter() - start_time) * 1000

output = interpreter.get_tensor(output_details[0]['index'])

print(f"  ✓ 推理成功！")
print(f"  输入: HR={hr_test[0,0]}, SpO2={spo2_test[0,0]}")
print(f"  输出 (异常分数): {output[0,0]:.4f}")
print(f"  推理时间: {inference_time:.2f} ms")

print("\n" + "=" * 60)
print("✓ 导出完成！")
print("=" * 60)
print(f"模型文件: {tflite_int8_path}")
print(f"模型大小: {int8_size:.2f} KB")
print("\n下一步：将模型复制到Android项目的assets目录")
print("命令：copy ml\\models\\sleep_model.tflite app\\src\\main\\assets\\")
