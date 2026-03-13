"""
端侧AI推理引擎 - 实时生理指标异常检测Demo

功能：
1. 加载INT8量化TensorFlow Lite模型
2. 实时模拟生理指标数据流（血氧、心率等）
3. 毫秒级推理检测异常
4. 本地声光预警系统
"""

import numpy as np
import time
import threading
from datetime import datetime
from collections import deque
import json
import warnings
warnings.filterwarnings('ignore')

try:
    import tensorflow as tf
    TF_AVAILABLE = True
except ImportError:
    TF_AVAILABLE = False
    print("警告: TensorFlow未安装")


class EdgeInferenceEngine:
    """端侧推理引擎"""
    
    def __init__(self, model_path: str = None, use_mock: bool = True):
        """
        Args:
            model_path: TFLite模型路径
            use_mock: 是否使用模拟推理（如果模型不存在）
        """
        self.model_path = model_path
        self.use_mock = use_mock
        self.interpreter = None
        self.input_details = None
        self.output_details = None
        
        # 推理性能统计
        self.inference_times = deque(maxlen=100)
        
        # 初始化模型
        if model_path and TF_AVAILABLE:
            self._load_model()
        else:
            print("⚠ 使用模拟推理模式")
            self.use_mock = True
    
    def _load_model(self):
        """加载TFLite模型"""
        try:
            print(f"📦 加载TFLite模型: {self.model_path}")
            
            # 创建解释器
            self.interpreter = tf.lite.Interpreter(model_path=self.model_path)
            self.interpreter.allocate_tensors()
            
            # 获取输入输出详情
            self.input_details = self.interpreter.get_input_details()
            self.output_details = self.interpreter.get_output_details()
            
            print("✓ 模型加载成功")
            print(f"  输入: {len(self.input_details)} 个")
            print(f"  输出: {len(self.output_details)} 个")
            
            self.use_mock = False
            
        except Exception as e:
            print(f"× 模型加载失败: {e}")
            print("  切换到模拟推理模式")
            self.use_mock = True
    
    def preprocess_data(self, vital_signs: dict) -> dict:
        """预处理输入数据
        
        Args:
            vital_signs: 生理指标字典
                - ppg: 光电容积脉搏波信号 (1500维)
                - heart_rate: 心率
                - hrv_sdnn: 心率变异性SDNN
                - hrv_rmssd: 心率变异性RMSSD
                - spo2: 血氧饱和度
                - temp: 体温
                - motion: 运动数据 (750x6)
        
        Returns:
            预处理后的输入字典
        """
        # 模型输入格式
        inputs = {
            'ppg': vital_signs['ppg'].reshape(1, 1, 1500).astype(np.float32),
            'hr_features': np.array([
                [vital_signs.get('heart_rate', 70),
                 vital_signs.get('hr_min', 60),
                 vital_signs.get('hr_max', 80),
                 vital_signs.get('hr_std', 5)]
            ], dtype=np.float32),
            'hrv_features': np.array([
                [vital_signs.get('hrv_sdnn', 50),
                 vital_signs.get('hrv_rmssd', 40),
                 vital_signs.get('hrv_pnn50', 20)]
            ], dtype=np.float32),
            'spo2_features': np.array([
                [vital_signs.get('spo2', 98),
                 vital_signs.get('spo2_min', 95),
                 vital_signs.get('spo2_max', 100)]
            ], dtype=np.float32),
            'temp_features': np.array([
                [vital_signs.get('temp', 36.5),
                 vital_signs.get('temp_std', 0.2)]
            ], dtype=np.float32),
            'motion': vital_signs.get('motion', 
                np.zeros((750, 6), dtype=np.float32)).reshape(1, 750, 6)
        }
        
        return inputs
    
    def inference(self, inputs: dict) -> dict:
        """执行推理
        
        Args:
            inputs: 预处理后的输入
        
        Returns:
            推理结果
        """
        start_time = time.perf_counter()
        
        if self.use_mock:
            # 模拟推理
            outputs = self._mock_inference(inputs)
        else:
            # 真实TFLite推理
            outputs = self._tflite_inference(inputs)
        
        # 记录推理时间
        inference_time = (time.perf_counter() - start_time) * 1000  # 毫秒
        self.inference_times.append(inference_time)
        
        outputs['inference_time_ms'] = inference_time
        
        return outputs
    
    def _tflite_inference(self, inputs: dict) -> dict:
        """TFLite推理"""
        # 设置输入张量
        for i, input_detail in enumerate(self.input_details):
            input_name = input_detail['name']
            # 根据名称匹配输入
            for key, value in inputs.items():
                if key in input_name:
                    self.interpreter.set_tensor(input_detail['index'], value)
                    break
        
        # 执行推理
        self.interpreter.invoke()
        
        # 获取输出
        outputs = {}
        for output_detail in self.output_details:
            output_data = self.interpreter.get_tensor(output_detail['index'])
            outputs[output_detail['name']] = output_data
        
        return outputs
    
    def _mock_inference(self, inputs: dict) -> dict:
        """模拟推理（用于演示）"""
        # 提取关键指标
        hr = inputs['hr_features'][0, 0]
        spo2 = inputs['spo2_features'][0, 0]
        
        # 模拟异常分数计算
        # 正常范围: 心率60-100, 血氧95-100
        hr_anomaly = 0.0
        if hr < 50:
            hr_anomaly = (50 - hr) / 50  # 心动过缓
        elif hr > 110:
            hr_anomaly = (hr - 110) / 110  # 心动过速
        
        spo2_anomaly = 0.0
        if spo2 < 90:
            spo2_anomaly = (90 - spo2) / 10  # 低血氧
        
        # 综合异常分数
        anomaly_score = max(hr_anomaly, spo2_anomaly)
        anomaly_score = min(anomaly_score, 1.0)  # 限制在[0, 1]
        
        # 模拟其他输出
        outputs = {
            'anomaly_score': np.array([[anomaly_score]], dtype=np.float32),
            'sleep_stage_logits': np.random.randn(1, 5).astype(np.float32),
            'recovery_score': np.array([[0.8]], dtype=np.float32)
        }
        
        return outputs
    
    def get_avg_inference_time(self) -> float:
        """获取平均推理时间"""
        if len(self.inference_times) == 0:
            return 0.0
        return np.mean(self.inference_times)


class VitalSignsSimulator:
    """生理指标模拟器"""
    
    def __init__(self, sampling_rate: int = 50):
        """
        Args:
            sampling_rate: 采样率 (Hz)
        """
        self.sampling_rate = sampling_rate
        self.time_counter = 0
        
        # 基础生理参数
        self.base_hr = 70  # 基础心率
        self.base_spo2 = 98  # 基础血氧
        self.base_temp = 36.5  # 基础体温
        
        # 异常模拟开关
        self.trigger_low_spo2 = False
        self.trigger_high_hr = False
        self.trigger_low_hr = False
    
    def generate_ppg_signal(self, duration: float = 30.0) -> np.ndarray:
        """生成PPG信号 (光电容积脉搏波)
        
        Args:
            duration: 信号时长（秒）
        
        Returns:
            PPG信号数组
        """
        num_samples = int(duration * self.sampling_rate)
        t = np.linspace(0, duration, num_samples)
        
        # 心率（带随机波动）
        hr = self.base_hr + np.random.randn() * 5
        if self.trigger_high_hr:
            hr = 120 + np.random.randn() * 10
        elif self.trigger_low_hr:
            hr = 45 + np.random.randn() * 5
        
        # 生成PPG波形（简化模型）
        freq = hr / 60.0  # 转换为Hz
        ppg = np.sin(2 * np.pi * freq * t)
        ppg += 0.3 * np.sin(4 * np.pi * freq * t)  # 二次谐波
        ppg += 0.1 * np.random.randn(num_samples)  # 噪声
        
        # 归一化
        ppg = (ppg - ppg.mean()) / (ppg.std() + 1e-8)
        
        return ppg
    
    def get_current_vital_signs(self) -> dict:
        """获取当前生理指标"""
        # 生成PPG信号（30秒，1500个采样点）
        ppg = self.generate_ppg_signal(duration=30.0)
        
        # 心率相关
        hr = self.base_hr + np.random.randn() * 3
        if self.trigger_high_hr:
            hr = 120 + np.random.randn() * 10
        elif self.trigger_low_hr:
            hr = 45 + np.random.randn() * 5
        
        # 血氧相关
        spo2 = self.base_spo2 + np.random.randn() * 0.5
        if self.trigger_low_spo2:
            spo2 = 85 + np.random.randn() * 3
        
        # 体温
        temp = self.base_temp + np.random.randn() * 0.1
        
        # 运动数据（加速度和陀螺仪）
        motion = np.random.randn(750, 6) * 0.1
        
        vital_signs = {
            'ppg': ppg,
            'heart_rate': hr,
            'hr_min': hr - 5,
            'hr_max': hr + 5,
            'hr_std': 3.0,
            'hrv_sdnn': 50 + np.random.randn() * 10,
            'hrv_rmssd': 40 + np.random.randn() * 8,
            'hrv_pnn50': 20 + np.random.randn() * 5,
            'spo2': spo2,
            'spo2_min': spo2 - 1,
            'spo2_max': spo2 + 1,
            'temp': temp,
            'temp_std': 0.2,
            'motion': motion
        }
        
        self.time_counter += 1
        
        return vital_signs


class AlertSystem:
    """预警系统"""
    
    def __init__(self):
        self.alert_history = deque(maxlen=100)
        self.is_alerting = False
        
        # 阈值设置
        self.thresholds = {
            'anomaly_score': 0.3,  # 异常分数阈值
            'spo2_low': 90,  # 低血氧阈值
            'hr_low': 50,  # 心动过缓阈值
            'hr_high': 110  # 心动过速阈值
        }
    
    def check_and_alert(self, vital_signs: dict, inference_result: dict):
        """检查并触发预警
        
        Args:
            vital_signs: 生理指标
            inference_result: 推理结果
        """
        alerts = []
        
        # 1. 异常分数检查
        anomaly_score = inference_result.get('anomaly_score', [[0]])[0][0]
        if anomaly_score > self.thresholds['anomaly_score']:
            alerts.append({
                'type': 'ANOMALY',
                'level': 'HIGH' if anomaly_score > 0.6 else 'MEDIUM',
                'message': f'检测到异常！异常分数: {anomaly_score:.3f}',
                'value': anomaly_score
            })
        
        # 2. 血氧检查
        spo2 = vital_signs.get('spo2', 100)
        if spo2 < self.thresholds['spo2_low']:
            alerts.append({
                'type': 'LOW_SPO2',
                'level': 'CRITICAL' if spo2 < 85 else 'HIGH',
                'message': f'⚠️ 低血氧预警！当前血氧: {spo2:.1f}%',
                'value': spo2
            })
        
        # 3. 心率检查
        hr = vital_signs.get('heart_rate', 70)
        if hr < self.thresholds['hr_low']:
            alerts.append({
                'type': 'LOW_HR',
                'level': 'HIGH',
                'message': f'⚠️ 心动过缓预警！当前心率: {hr:.0f} bpm',
                'value': hr
            })
        elif hr > self.thresholds['hr_high']:
            alerts.append({
                'type': 'HIGH_HR',
                'level': 'HIGH',
                'message': f'⚠️ 心动过速预警！当前心率: {hr:.0f} bpm',
                'value': hr
            })
        
        # 触发预警
        if alerts:
            self._trigger_alert(alerts)
        
        return alerts
    
    def _trigger_alert(self, alerts: list):
        """触发声光预警"""
        for alert in alerts:
            # 记录预警
            alert['timestamp'] = datetime.now().isoformat()
            self.alert_history.append(alert)
            
            # 打印预警信息（带颜色和声音提示）
            level_symbols = {
                'CRITICAL': '🚨🚨🚨',
                'HIGH': '⚠️⚠️',
                'MEDIUM': '⚠️'
            }
            
            symbol = level_symbols.get(alert['level'], '⚠️')
            print(f"\n{symbol} {alert['message']} {symbol}")
            
            # 模拟声音预警（实际应用中可以使用winsound或其他库）
            if alert['level'] == 'CRITICAL':
                print("🔊 BEEP! BEEP! BEEP!")
            elif alert['level'] == 'HIGH':
                print("🔊 BEEP!")


class EdgeInferenceDemo:
    """端侧推理演示系统"""
    
    def __init__(self, model_path: str = None):
        """
        Args:
            model_path: TFLite模型路径（可选）
        """
        print("=" * 70)
        print("🏥 端侧AI实时预警系统")
        print("=" * 70)
        print()
        
        # 初始化各组件
        self.engine = EdgeInferenceEngine(model_path)
        self.simulator = VitalSignsSimulator()
        self.alert_system = AlertSystem()
        
        # 运行状态
        self.is_running = False
        self.monitoring_thread = None
        
        # 统计信息
        self.total_inferences = 0
        self.total_alerts = 0
    
    def start_monitoring(self, interval: float = 2.0):
        """启动实时监测
        
        Args:
            interval: 监测间隔（秒）
        """
        self.is_running = True
        
        print("🚀 启动实时监测...")
        print(f"   监测间隔: {interval} 秒")
        print(f"   推理引擎: {'TFLite' if not self.engine.use_mock else '模拟模式'}")
        print()
        print("💡 提示: 按 Ctrl+C 停止监测")
        print("-" * 70)
        print()
        
        try:
            while self.is_running:
                # 1. 获取生理指标
                vital_signs = self.simulator.get_current_vital_signs()
                
                # 2. 预处理
                inputs = self.engine.preprocess_data(vital_signs)
                
                # 3. 推理
                outputs = self.engine.inference(inputs)
                
                # 4. 异常检测与预警
                alerts = self.alert_system.check_and_alert(vital_signs, outputs)
                
                # 5. 显示状态
                self._display_status(vital_signs, outputs, alerts)
                
                # 更新统计
                self.total_inferences += 1
                self.total_alerts += len(alerts)
                
                # 等待下一次监测
                time.sleep(interval)
                
        except KeyboardInterrupt:
            print("\n\n⏸ 停止监测...")
            self.stop_monitoring()
    
    def _display_status(self, vital_signs: dict, outputs: dict, alerts: list):
        """显示实时状态"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        
        # 提取关键指标
        hr = vital_signs.get('heart_rate', 0)
        spo2 = vital_signs.get('spo2', 0)
        temp = vital_signs.get('temp', 0)
        anomaly_score = outputs.get('anomaly_score', [[0]])[0][0]
        inference_time = outputs.get('inference_time_ms', 0)
        
        # 状态指示
        status = "🟢 正常" if len(alerts) == 0 else "🔴 预警"
        
        # 格式化输出
        print(f"[{timestamp}] {status}")
        print(f"  ❤️  心率: {hr:6.1f} bpm  |  🫁 血氧: {spo2:5.1f}%  |  🌡️  体温: {temp:4.1f}°C")
        print(f"  ⚡ 异常分数: {anomaly_score:5.3f}  |  ⏱️  推理时间: {inference_time:5.2f} ms")
        
        if alerts:
            print(f"  📢 预警数量: {len(alerts)}")
        
        print()
    
    def stop_monitoring(self):
        """停止监测"""
        self.is_running = False
        
        # 显示统计信息
        print("=" * 70)
        print("📊 监测统计")
        print("=" * 70)
        print(f"总推理次数: {self.total_inferences}")
        print(f"总预警次数: {self.total_alerts}")
        print(f"平均推理时间: {self.engine.get_avg_inference_time():.2f} ms")
        print(f"预警历史记录: {len(self.alert_system.alert_history)}")
        print("=" * 70)
    
    def simulate_anomaly(self, anomaly_type: str, duration: float = 10.0):
        """模拟异常情况
        
        Args:
            anomaly_type: 异常类型 ('low_spo2', 'high_hr', 'low_hr')
            duration: 持续时间（秒）
        """
        print(f"\n🧪 模拟异常: {anomaly_type} (持续 {duration} 秒)")
        print("-" * 70)
        
        # 设置异常标志
        if anomaly_type == 'low_spo2':
            self.simulator.trigger_low_spo2 = True
        elif anomaly_type == 'high_hr':
            self.simulator.trigger_high_hr = True
        elif anomaly_type == 'low_hr':
            self.simulator.trigger_low_hr = True
        
        # 等待指定时间
        def reset_anomaly():
            time.sleep(duration)
            self.simulator.trigger_low_spo2 = False
            self.simulator.trigger_high_hr = False
            self.simulator.trigger_low_hr = False
            print("\n✓ 异常模拟结束，恢复正常\n")
        
        threading.Thread(target=reset_anomaly, daemon=True).start()


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='端侧AI推理Demo')
    parser.add_argument(
        '--model',
        type=str,
        default=None,
        help='TFLite模型路径'
    )
    parser.add_argument(
        '--interval',
        type=float,
        default=2.0,
        help='监测间隔（秒）'
    )
    parser.add_argument(
        '--simulate',
        type=str,
        choices=['low_spo2', 'high_hr', 'low_hr', 'none'],
        default='none',
        help='模拟异常类型'
    )
    
    args = parser.parse_args()
    
    # 创建演示系统
    demo = EdgeInferenceDemo(model_path=args.model)
    
    # 如果需要模拟异常，延迟触发
    if args.simulate != 'none':
        def trigger_anomaly():
            time.sleep(5)  # 5秒后触发异常
            demo.simulate_anomaly(args.simulate, duration=15)
        
        threading.Thread(target=trigger_anomaly, daemon=True).start()
    
    # 启动监测
    demo.start_monitoring(interval=args.interval)


if __name__ == '__main__':
    main()
