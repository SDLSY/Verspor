"""
简化版TensorFlow Lite模型量化导出脚本
用于快速创建INT8量化的轻量级模型用于端侧推理
"""

import numpy as np
import tensorflow as tf
import keras  # Keras 3.x 需要直接导入
import os


def create_simple_anomaly_model():
    """创建一个简单的异常检测模型（用于演示）"""
    
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


def generate_representative_dataset():
    """生成代表性数据集用于INT8量化"""
    
    def representative_data_gen():
        # 生成100个代表性样本
        for _ in range(100):
            # 心率特征: [当前, 最小, 最大, 标准差]
            hr = np.random.uniform(60, 100, size=(1, 4)).astype(np.float32)
            
            # 血氧特征: [当前, 最小, 最大]
            spo2 = np.random.uniform(95, 100, size=(1, 3)).astype(np.float32)
            
            # HRV特征: [SDNN, RMSSD, PNN50]
            hrv = np.random.uniform(30, 70, size=(1, 3)).astype(np.float32)
            
            yield [hr, spo2, hrv]
    
    return representative_data_gen


def export_tflite_int8_model(output_dir='./models'):
    """导出INT8量化的TFLite模型"""
    
    os.makedirs(output_dir, exist_ok=True)
    
    print("=" * 70)
    print("TensorFlow Lite INT8量化模型导出")
    print("=" * 70)
    
    # 1. 创建模型
    print("\n1. 创建模型...")
    model = create_simple_anomaly_model()
    model.summary()
    
    # 2. 编译模型（可选，但建议）
    print("\n2. 编译模型...")
    model.compile(
        optimizer='adam',
        loss='binary_crossentropy',
        metrics=['accuracy']
    )
    
    # 3. 生成一些训练数据并快速训练（让模型有一些合理的权重）
    print("\n3. 快速训练模型...")
    
    # 生成训练数据
    num_samples = 1000
    hr_data = np.random.uniform(60, 100, size=(num_samples, 4)).astype(np.float32)
    spo2_data = np.random.uniform(95, 100, size=(num_samples, 3)).astype(np.float32)
    hrv_data = np.random.uniform(30, 70, size=(num_samples, 3)).astype(np.float32)
    
    # 生成标签（简单规则：低血氧或心率异常为异常）
    labels = np.zeros((num_samples, 1), dtype=np.float32)
    for i in range(num_samples):
        # 如果血氧<90或心率<50或心率>110，则为异常
        if spo2_data[i, 0] < 90 or hr_data[i, 0] < 50 or hr_data[i, 0] > 110:
            labels[i] = 1.0
    
    # 训练模型
    history = model.fit(
        [hr_data, spo2_data, hrv_data],
        labels,
        epochs=10,
        batch_size=32,
        validation_split=0.2,
        verbose=1
    )
    
    print(f"\n训练完成！最终验证准确率: {history.history['val_accuracy'][-1]:.4f}")
    
    # 4. 保存Keras模型
    print("\n4. 保存Keras模型...")
    keras_model_path = os.path.join(output_dir, 'anomaly_detector.keras')
    model.save(keras_model_path)
    keras_size = os.path.getsize(keras_model_path) / (1024 * 1024)
    print(f"  ✓ Keras模型已保存: {keras_model_path}")
    print(f"  模型大小: {keras_size:.2f} MB")
    
    # 5. 转换为TFLite (FP32 - 无量化)
    print("\n5. 转换为TFLite (FP32)...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_fp32_model = converter.convert()
    
    tflite_fp32_path = os.path.join(output_dir, 'anomaly_detector_fp32.tflite')
    with open(tflite_fp32_path, 'wb') as f:
        f.write(tflite_fp32_model)
    
    fp32_size = os.path.getsize(tflite_fp32_path) / 1024
    print(f"  ✓ FP32模型已保存: {tflite_fp32_path}")
    print(f"  模型大小: {fp32_size:.2f} KB")
    
    # 6. 转换为TFLite (FP16量化)
    print("\n6. 转换为TFLite (FP16量化)...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    tflite_fp16_model = converter.convert()
    
    tflite_fp16_path = os.path.join(output_dir, 'anomaly_detector_fp16.tflite')
    with open(tflite_fp16_path, 'wb') as f:
        f.write(tflite_fp16_model)
    
    fp16_size = os.path.getsize(tflite_fp16_path) / 1024
    print(f"  ✓ FP16模型已保存: {tflite_fp16_path}")
    print(f"  模型大小: {fp16_size:.2f} KB")
    print(f"  压缩比: {fp32_size/fp16_size:.2f}x")
    
    # 7. 转换为TFLite (INT8量化 - 最轻量)
    print("\n7. 转换为TFLite (INT8量化)...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    # 设置代表性数据集
    converter.representative_dataset = generate_representative_dataset()
    
    # 设置INT8量化
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.float32  # 输入仍为float32
    converter.inference_output_type = tf.float32  # 输出仍为float32
    
    tflite_int8_model = converter.convert()
    
    tflite_int8_path = os.path.join(output_dir, 'anomaly_detector_int8.tflite')
    with open(tflite_int8_path, 'wb') as f:
        f.write(tflite_int8_model)
    
    int8_size = os.path.getsize(tflite_int8_path) / 1024
    print(f"  ✓ INT8模型已保存: {tflite_int8_path}")
    print(f"  模型大小: {int8_size:.2f} KB")
    print(f"  压缩比: {fp32_size/int8_size:.2f}x")
    
    # 8. 验证模型
    print("\n8. 验证TFLite模型...")
    verify_tflite_model(tflite_int8_path)
    
    # 总结
    print("\n" + "=" * 70)
    print("导出完成！文件列表:")
    print("=" * 70)
    print(f"1. Keras完整模型:    {keras_model_path} ({keras_size:.2f} MB)")
    print(f"2. TFLite FP32模型:  {tflite_fp32_path} ({fp32_size:.2f} KB)")
    print(f"3. TFLite FP16模型:  {tflite_fp16_path} ({fp16_size:.2f} KB)")
    print(f"4. TFLite INT8模型:  {tflite_int8_path} ({int8_size:.2f} KB) ⭐ 推荐端侧使用")
    print("=" * 70)
    
    return tflite_int8_path


def verify_tflite_model(model_path):
    """验证TFLite模型"""
    
    # 加载模型
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    
    # 获取输入输出详情
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"  模型输入数量: {len(input_details)}")
    for i, detail in enumerate(input_details):
        print(f"    输入{i}: {detail['name']}, 形状: {detail['shape']}, 类型: {detail['dtype']}")
    
    print(f"  模型输出数量: {len(output_details)}")
    for i, detail in enumerate(output_details):
        print(f"    输出{i}: {detail['name']}, 形状: {detail['shape']}, 类型: {detail['dtype']}")
    
    # 测试推理
    print("\n  测试推理...")
    
    # 准备测试数据
    hr_test = np.array([[70.0, 65.0, 78.0, 4.5]], dtype=np.float32)
    spo2_test = np.array([[98.0, 97.0, 99.0]], dtype=np.float32)
    hrv_test = np.array([[50.0, 40.0, 20.0]], dtype=np.float32)
    
    # 设置输入
    interpreter.set_tensor(input_details[0]['index'], hr_test)
    interpreter.set_tensor(input_details[1]['index'], spo2_test)
    interpreter.set_tensor(input_details[2]['index'], hrv_test)
    
    # 执行推理
    import time
    start_time = time.perf_counter()
    interpreter.invoke()
    inference_time = (time.perf_counter() - start_time) * 1000
    
    # 获取输出
    output = interpreter.get_tensor(output_details[0]['index'])
    
    print(f"  ✓ 推理成功！")
    print(f"  输入: HR={hr_test[0,0]}, SpO2={spo2_test[0,0]}")
    print(f"  输出 (异常分数): {output[0,0]:.4f}")
    print(f"  推理时间: {inference_time:.2f} ms")


def benchmark_tflite_model(model_path, num_runs=100):
    """性能基准测试"""
    
    print(f"\n性能基准测试 (运行{num_runs}次)...")
    
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    # 准备随机测试数据
    hr_test = np.random.uniform(60, 100, size=(1, 4)).astype(np.float32)
    spo2_test = np.random.uniform(95, 100, size=(1, 3)).astype(np.float32)
    hrv_test = np.random.uniform(30, 70, size=(1, 3)).astype(np.float32)
    
    inference_times = []
    
    for _ in range(num_runs):
        interpreter.set_tensor(input_details[0]['index'], hr_test)
        interpreter.set_tensor(input_details[1]['index'], spo2_test)
        interpreter.set_tensor(input_details[2]['index'], hrv_test)
        
        start_time = time.perf_counter()
        interpreter.invoke()
        inference_time = (time.perf_counter() - start_time) * 1000
        
        inference_times.append(inference_time)
    
    # 统计
    avg_time = np.mean(inference_times)
    min_time = np.min(inference_times)
    max_time = np.max(inference_times)
    p95_time = np.percentile(inference_times, 95)
    
    print(f"  平均推理时间: {avg_time:.2f} ms")
    print(f"  最小推理时间: {min_time:.2f} ms")
    print(f"  最大推理时间: {max_time:.2f} ms")
    print(f"  P95推理时间:  {p95_time:.2f} ms")


if __name__ == '__main__':
    import argparse
    import time
    
    parser = argparse.ArgumentParser(description='导出TFLite INT8量化模型')
    parser.add_argument(
        '--output_dir',
        type=str,
        default='./models',
        help='输出目录'
    )
    parser.add_argument(
        '--benchmark',
        action='store_true',
        help='运行性能基准测试'
    )
    
    args = parser.parse_args()
    
    # 导出模型
    model_path = export_tflite_int8_model(output_dir=args.output_dir)
    
    # 性能测试
    if args.benchmark:
        benchmark_tflite_model(model_path)
