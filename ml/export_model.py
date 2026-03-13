"""
模型导出工具

功能：
1. PyTorch模型导出为ONNX格式
2. ONNX转换为TensorFlow Lite（用于Android端侧推理）
3. 模型量化（FP16/INT8）
"""

import torch
import torch.onnx
import numpy as np
import os
from typing import Tuple, Dict
import onnx
from onnx import optimizer

try:
    import tensorflow as tf
    from tensorflow import lite
    TF_AVAILABLE = True
except ImportError:
    TF_AVAILABLE = False
    print("警告: TensorFlow未安装，无法转换为TFLite格式")

from MultiModalTransformer import MultiModalTransformer, create_model


class ModelExporter:
    """模型导出器"""
    
    def __init__(self, model: torch.nn.Module, device: str = 'cpu'):
        """
        Args:
            model: PyTorch模型
            device: 设备
        """
        self.model = model.to(device).eval()
        self.device = device
    
    def export_to_onnx(
        self,
        save_path: str,
        input_shapes: Dict[str, Tuple] = None,
        opset_version: int = 12
    ):
        """导出为ONNX格式
        
        Args:
            save_path: 保存路径
            input_shapes: 输入形状字典
            opset_version: ONNX opset版本
        """
        if input_shapes is None:
            # 默认输入形状（batch_size=1）
            input_shapes = {
                'ppg': (1, 1, 1500),
                'hr_features': (1, 4),
                'hrv_features': (1, 3),
                'spo2_features': (1, 3),
                'temp_features': (1, 2),
                'motion': (1, 750, 6)
            }
        
        # 创建模拟输入
        dummy_inputs = tuple([
            torch.randn(shape).to(self.device)
            for shape in input_shapes.values()
        ])
        
        # 输入名称
        input_names = list(input_shapes.keys())
        
        # 输出名称
        output_names = ['sleep_stage_logits', 'recovery_score', 'anomaly_score']
        
        print(f"导出ONNX模型到: {save_path}")
        
        # 导出
        torch.onnx.export(
            self.model,
            dummy_inputs,
            save_path,
            export_params=True,
            opset_version=opset_version,
            do_constant_folding=True,
            input_names=input_names,
            output_names=output_names,
            dynamic_axes={
                # 支持动态batch size
                **{name: {0: 'batch_size'} for name in input_names},
                **{name: {0: 'batch_size'} for name in output_names}
            }
        )
        
        # 验证ONNX模型
        onnx_model = onnx.load(save_path)
        onnx.checker.check_model(onnx_model)
        
        # 优化ONNX模型
        optimized_model = optimizer.optimize(onnx_model)
        onnx.save(optimized_model, save_path)
        
        print(f"✓ ONNX模型导出成功！")
        
        # 打印模型信息
        file_size = os.path.getsize(save_path) / (1024 * 1024)
        print(f"  模型大小: {file_size:.2f} MB")
        print(f"  输入: {input_names}")
        print(f"  输出: {output_names}")
    
    def onnx_to_tflite(
        self,
        onnx_path: str,
        tflite_path: str,
        quantize: str = None
    ):
        """将ONNX模型转换为TFLite
        
        Args:
            onnx_path: ONNX模型路径
            tflite_path: TFLite保存路径
            quantize: 量化类型 ('fp16', 'int8', None)
        """
        if not TF_AVAILABLE:
            raise RuntimeError("需要安装TensorFlow才能转换为TFLite")
        
        try:
            import onnx_tf
        except ImportError:
            raise RuntimeError("需要安装onnx-tf: pip install onnx-tf")
        
        print(f"转换ONNX到TFLite...")
        
        # 1. ONNX -> TensorFlow
        onnx_model = onnx.load(onnx_path)
        tf_rep = onnx_tf.backend.prepare(onnx_model)
        
        tf_model_path = onnx_path.replace('.onnx', '_tf')
        tf_rep.export_graph(tf_model_path)
        
        # 2. TensorFlow -> TFLite
        converter = tf.lite.TFLiteConverter.from_saved_model(tf_model_path)
        
        # 设置优化选项
        if quantize == 'fp16':
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            converter.target_spec.supported_types = [tf.float16]
            print("  使用FP16量化")
        
        elif quantize == 'int8':
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            
            # INT8量化需要代表性数据集
            def representative_dataset_gen():
                # 生成代表性数据（这里使用随机数据，实际应用中应使用真实数据）
                for _ in range(100):
                    yield [
                        np.random.randn(1, 1, 1500).astype(np.float32),
                        np.random.randn(1, 4).astype(np.float32),
                        np.random.randn(1, 3).astype(np.float32),
                        np.random.randn(1, 3).astype(np.float32),
                        np.random.randn(1, 2).astype(np.float32),
                        np.random.randn(1, 750, 6).astype(np.float32)
                    ]
            
            converter.representative_dataset = representative_dataset_gen
            converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
            converter.inference_input_type = tf.uint8
            converter.inference_output_type = tf.uint8
            print("  使用INT8量化")
        
        else:
            print("  不使用量化")
        
        # 转换
        tflite_model = converter.convert()
        
        # 保存
        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)
        
        print(f"✓ TFLite模型保存到: {tflite_path}")
        
        # 打印模型信息
        file_size = os.path.getsize(tflite_path) / (1024 * 1024)
        print(f"  模型大小: {file_size:.2f} MB")


def export_full_model(
    checkpoint_path: str,
    output_dir: str,
    device: str = 'cpu'
):
    """完整导出流程
    
    Args:
        checkpoint_path: PyTorch检查点路径
        output_dir: 输出目录
        device: 设备
    """
    os.makedirs(output_dir, exist_ok=True)
    
    print("=" * 60)
    print("模型导出流程")
    print("=" * 60)
    
    # 1. 加载PyTorch模型
    print("\n1. 加载PyTorch模型...")
    model = create_model(device)
    
    if os.path.exists(checkpoint_path):
        checkpoint = torch.load(checkpoint_path, map_location=device)
        model.load_state_dict(checkpoint['model_state_dict'])
        print(f"  ✓ 加载检查点: {checkpoint_path}")
        print(f"  Epoch: {checkpoint.get('epoch', 'N/A')}")
    else:
        print(f"  警告: 检查点不存在，使用随机初始化模型")
    
    # 2. 导出ONNX
    print("\n2. 导出ONNX格式...")
    onnx_path = os.path.join(output_dir, 'mmtsa_model.onnx')
    exporter = ModelExporter(model, device)
    exporter.export_to_onnx(onnx_path)
    
    # 3. 转换为TFLite（不同量化版本）
    if TF_AVAILABLE:
        print("\n3. 转换为TFLite格式...")
        
        # 无量化版本（用于对比）
        tflite_path = os.path.join(output_dir, 'mmtsa_model_fp32.tflite')
        try:
            exporter.onnx_to_tflite(onnx_path, tflite_path, quantize=None)
        except Exception as e:
            print(f"  × FP32转换失败: {e}")
        
        # FP16量化版本
        tflite_fp16_path = os.path.join(output_dir, 'mmtsa_model_fp16.tflite')
        try:
            exporter.onnx_to_tflite(onnx_path, tflite_fp16_path, quantize='fp16')
        except Exception as e:
            print(f"  × FP16转换失败: {e}")
        
        # INT8量化版本（最小体积）
        tflite_int8_path = os.path.join(output_dir, 'mmtsa_model_int8.tflite')
        try:
            exporter.onnx_to_tflite(onnx_path, tflite_int8_path, quantize='int8')
        except Exception as e:
            print(f"  × INT8转换失败: {e}")
    
    print("\n" + "=" * 60)
    print("导出完成！")
    print("=" * 60)
    print(f"\n输出目录: {output_dir}")
    print("\n生成的文件:")
    for filename in os.listdir(output_dir):
        filepath = os.path.join(output_dir, filename)
        if os.path.isfile(filepath):
            size = os.path.getsize(filepath) / (1024 * 1024)
            print(f"  - {filename}: {size:.2f} MB")


if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='导出多模态Transformer模型')
    parser.add_argument(
        '--checkpoint',
        type=str,
        default='./checkpoints/best_model.pth',
        help='PyTorch检查点路径'
    )
    parser.add_argument(
        '--output_dir',
        type=str,
        default='./exported_models',
        help='输出目录'
    )
    parser.add_argument(
        '--device',
        type=str,
        default='cpu',
        help='设备 (cuda/cpu)'
    )
    
    args = parser.parse_args()
    
    export_full_model(
        checkpoint_path=args.checkpoint,
        output_dir=args.output_dir,
        device=args.device
    )
