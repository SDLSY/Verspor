"""
数据预处理模块

功能：
1. 从原始传感器数据中提取特征
2. 数据清洗和标准化
3. 窗口划分和数据增强
"""

import numpy as np
import pandas as pd
from scipy import signal
from scipy.interpolate import interp1d
from typing import Dict, Tuple, List, Optional
import warnings
warnings.filterwarnings('ignore')


class DataPreprocessor:
    """数据预处理器"""
    
    def __init__(self, window_size: int = 30):
        """
        Args:
            window_size: 窗口大小（秒）
        """
        self.window_size = window_size
        
        # 数据范围（用于标准化）
        self.hr_range = (40, 180)  # 心率范围
        self.spo2_range = (70, 100)  # 血氧范围
        self.temp_range = (34, 40)  # 体温范围
    
    def clean_data(
        self,
        data: np.ndarray,
        method: str = 'iqr',
        threshold: float = 3.0
    ) -> np.ndarray:
        """数据清洗：去除异常值
        
        Args:
            data: 输入数据
            method: 清洗方法 ('iqr' 或 'zscore')
            threshold: 阈值
        
        Returns:
            清洗后的数据
        """
        if method == 'iqr':
            # 四分位距方法
            q1 = np.percentile(data, 25)
            q3 = np.percentile(data, 75)
            iqr = q3 - q1
            
            lower_bound = q1 - threshold * iqr
            upper_bound = q3 + threshold * iqr
            
            # 将异常值替换为中位数
            median = np.median(data)
            data_clean = np.where(
                (data < lower_bound) | (data > upper_bound),
                median,
                data
            )
        
        elif method == 'zscore':
            # Z-score方法
            mean = np.mean(data)
            std = np.std(data)
            
            z_scores = np.abs((data - mean) / std)
            data_clean = np.where(z_scores > threshold, mean, data)
        
        else:
            raise ValueError(f"未知的清洗方法: {method}")
        
        return data_clean
    
    def interpolate_missing(
        self,
        data: np.ndarray,
        timestamps: np.ndarray,
        method: str = 'cubic'
    ) -> Tuple[np.ndarray, np.ndarray]:
        """插值处理缺失值
        
        Args:
            data: 数据数组（可能包含NaN）
            timestamps: 时间戳数组
            method: 插值方法 ('linear', 'cubic', 'nearest')
        
        Returns:
            插值后的数据和时间戳
        """
        # 找出非NaN的数据点
        valid_mask = ~np.isnan(data)
        
        if np.sum(valid_mask) < 2:
            # 如果有效数据点少于2个，用均值填充
            return np.full_like(data, np.nanmean(data)), timestamps
        
        # 创建插值函数
        f = interp1d(
            timestamps[valid_mask],
            data[valid_mask],
            kind=method,
            bounds_error=False,
            fill_value='extrapolate'
        )
        
        # 插值
        data_interpolated = f(timestamps)
        
        return data_interpolated, timestamps
    
    def bandpass_filter(
        self,
        data: np.ndarray,
        fs: float,
        lowcut: float,
        highcut: float,
        order: int = 4
    ) -> np.ndarray:
        """带通滤波（用于PPG信号）
        
        Args:
            data: 输入信号
            fs: 采样频率
            lowcut: 低截止频率
            highcut: 高截止频率
            order: 滤波器阶数
        
        Returns:
            滤波后的信号
        """
        nyquist = 0.5 * fs
        low = lowcut / nyquist
        high = highcut / nyquist
        
        b, a = signal.butter(order, [low, high], btype='band')
        filtered_data = signal.filtfilt(b, a, data)
        
        return filtered_data
    
    def lowpass_filter(
        self,
        data: np.ndarray,
        fs: float,
        cutoff: float,
        order: int = 4
    ) -> np.ndarray:
        """低通滤波（用于加速度信号）
        
        Args:
            data: 输入信号
            fs: 采样频率
            cutoff: 截止频率
            order: 滤波器阶数
        
        Returns:
            滤波后的信号
        """
        nyquist = 0.5 * fs
        normal_cutoff = cutoff / nyquist
        
        b, a = signal.butter(order, normal_cutoff, btype='low')
        filtered_data = signal.filtfilt(b, a, data)
        
        return filtered_data
    
    def extract_hr_features(
        self,
        hr_data: np.ndarray
    ) -> np.ndarray:
        """提取心率统计特征
        
        Args:
            hr_data: 心率时序数据
        
        Returns:
            特征向量 [mean, std, min, max]
        """
        features = np.array([
            np.mean(hr_data),
            np.std(hr_data),
            np.min(hr_data),
            np.max(hr_data)
        ])
        
        return features
    
    def extract_hrv_features(
        self,
        rr_intervals: np.ndarray
    ) -> np.ndarray:
        """提取HRV特征
        
        Args:
            rr_intervals: RR间期数组（毫秒）
        
        Returns:
            特征向量 [rmssd, sdnn, lf_hf_ratio]
        """
        # 时域特征
        rmssd = np.sqrt(np.mean(np.diff(rr_intervals) ** 2))
        sdnn = np.std(rr_intervals)
        
        # 频域特征
        lf, hf = self._compute_frequency_features(rr_intervals)
        lf_hf_ratio = lf / hf if hf > 0 else 0
        
        features = np.array([rmssd, sdnn, lf_hf_ratio])
        
        return features
    
    def _compute_frequency_features(
        self,
        rr_intervals: np.ndarray
    ) -> Tuple[float, float]:
        """计算HRV频域特征
        
        Returns:
            lf: 低频功率 (0.04-0.15 Hz)
            hf: 高频功率 (0.15-0.4 Hz)
        """
        # 重采样到4Hz
        rr_times = np.cumsum(rr_intervals) / 1000.0  # 转换为秒
        f_interp = interp1d(
            rr_times,
            rr_intervals,
            kind='cubic',
            bounds_error=False,
            fill_value='extrapolate'
        )
        
        fs = 4.0  # 4Hz采样
        t_regular = np.arange(rr_times[0], rr_times[-1], 1/fs)
        rr_regular = f_interp(t_regular)
        
        # 计算功率谱密度
        freqs, psd = signal.welch(
            rr_regular,
            fs=fs,
            nperseg=min(256, len(rr_regular))
        )
        
        # 计算LF和HF功率
        lf_band = (freqs >= 0.04) & (freqs < 0.15)
        hf_band = (freqs >= 0.15) & (freqs < 0.4)
        
        lf = np.trapz(psd[lf_band], freqs[lf_band])
        hf = np.trapz(psd[hf_band], freqs[hf_band])
        
        return lf, hf
    
    def extract_spo2_features(
        self,
        spo2_data: np.ndarray
    ) -> np.ndarray:
        """提取血氧特征
        
        Args:
            spo2_data: 血氧时序数据
        
        Returns:
            特征向量 [mean, std, min]
        """
        features = np.array([
            np.mean(spo2_data),
            np.std(spo2_data),
            np.min(spo2_data)
        ])
        
        return features
    
    def extract_temp_features(
        self,
        temp_data: np.ndarray,
        timestamps: np.ndarray
    ) -> np.ndarray:
        """提取体温特征
        
        Args:
            temp_data: 体温时序数据
            timestamps: 时间戳
        
        Returns:
            特征向量 [mean, trend]
        """
        mean_temp = np.mean(temp_data)
        
        # 计算温度趋势（线性回归斜率）
        if len(temp_data) > 1:
            coeffs = np.polyfit(timestamps, temp_data, 1)
            trend = coeffs[0]
        else:
            trend = 0
        
        features = np.array([mean_temp, trend])
        
        return features
    
    def process_window(
        self,
        raw_data: Dict[str, np.ndarray],
        window_start: int,
        window_end: int
    ) -> Dict[str, np.ndarray]:
        """处理一个窗口的数据
        
        Args:
            raw_data: 原始数据字典
                - ppg: PPG波形 (shape: [n,])
                - hr: 心率数据 (shape: [n,])
                - spo2: 血氧数据 (shape: [n,])
                - temp: 体温数据 (shape: [n,])
                - acc: 加速度数据 (shape: [n, 3])
                - gyro: 陀螺仪数据 (shape: [n, 3])
                - rr_intervals: RR间期 (shape: [m,])
            window_start: 窗口起始索引
            window_end: 窗口结束索引
        
        Returns:
            处理后的特征字典
        """
        # 提取窗口数据
        ppg_window = raw_data['ppg'][window_start:window_end]
        hr_window = raw_data['hr'][window_start:window_end]
        spo2_window = raw_data['spo2'][window_start:window_end]
        temp_window = raw_data['temp'][window_start:window_end]
        acc_window = raw_data['acc'][window_start:window_end]
        gyro_window = raw_data['gyro'][window_start:window_end]
        
        # PPG滤波
        ppg_filtered = self.bandpass_filter(
            ppg_window,
            fs=50,  # PPG采样率50Hz
            lowcut=0.5,
            highcut=5.0
        )
        
        # 加速度滤波
        acc_filtered = np.zeros_like(acc_window)
        for i in range(3):
            acc_filtered[:, i] = self.lowpass_filter(
                acc_window[:, i],
                fs=25,  # 加速度采样率25Hz
                cutoff=5.0
            )
        
        # 提取特征
        hr_features = self.extract_hr_features(hr_window)
        
        # HRV特征（使用整个记录的RR间期）
        if 'rr_intervals' in raw_data and len(raw_data['rr_intervals']) > 5:
            hrv_features = self.extract_hrv_features(raw_data['rr_intervals'])
        else:
            hrv_features = np.zeros(3)
        
        spo2_features = self.extract_spo2_features(spo2_window)
        
        timestamps = np.arange(len(temp_window))
        temp_features = self.extract_temp_features(temp_window, timestamps)
        
        # 合并运动数据
        motion = np.concatenate([acc_filtered, gyro_window], axis=1)  # [n, 6]
        
        # 标准化
        ppg_normalized = (ppg_filtered - np.mean(ppg_filtered)) / (np.std(ppg_filtered) + 1e-8)
        
        processed_data = {
            'ppg': ppg_normalized.reshape(1, -1),  # [1, seq_len]
            'hr_features': hr_features,  # [4,]
            'hrv_features': hrv_features,  # [3,]
            'spo2_features': spo2_features,  # [3,]
            'temp_features': temp_features,  # [2,]
            'motion': motion  # [seq_len, 6]
        }
        
        return processed_data


def create_sliding_windows(
    data: Dict[str, np.ndarray],
    window_size: int = 30,
    stride: int = 15,
    fs: int = 1
) -> List[Dict[str, np.ndarray]]:
    """创建滑动窗口
    
    Args:
        data: 原始数据
        window_size: 窗口大小（秒）
        stride: 步长（秒）
        fs: 采样频率
    
    Returns:
        窗口列表
    """
    preprocessor = DataPreprocessor(window_size)
    
    # 计算窗口大小（样本数）
    window_samples = window_size * fs
    stride_samples = stride * fs
    
    # 数据长度
    data_len = len(data['hr'])
    
    windows = []
    
    for start in range(0, data_len - window_samples + 1, stride_samples):
        end = start + window_samples
        
        window_data = preprocessor.process_window(data, start, end)
        windows.append(window_data)
    
    return windows


if __name__ == '__main__':
    print("=" * 60)
    print("数据预处理模块测试")
    print("=" * 60)
    
    # 创建模拟数据
    duration = 120  # 2分钟
    fs_hr = 1  # 心率采样率1Hz
    fs_ppg = 50  # PPG采样率50Hz
    fs_motion = 25  # 运动传感器采样率25Hz
    
    raw_data = {
        'ppg': np.random.randn(duration * fs_ppg),
        'hr': np.random.randint(60, 100, duration * fs_hr),
        'spo2': np.random.randint(95, 100, duration * fs_hr),
        'temp': np.random.uniform(36.0, 37.0, duration * fs_hr),
        'acc': np.random.randn(duration * fs_motion, 3),
        'gyro': np.random.randn(duration * fs_motion, 3),
        'rr_intervals': np.random.uniform(600, 1000, 100)  # RR间期
    }
    
    print(f"\n原始数据:")
    for key, value in raw_data.items():
        print(f"  {key}: shape {value.shape}")
    
    # 创建滑动窗口
    print(f"\n创建滑动窗口 (window_size=30s, stride=15s)...")
    windows = create_sliding_windows(raw_data, window_size=30, stride=15)
    
    print(f"生成窗口数: {len(windows)}")
    
    if len(windows) > 0:
        print(f"\n单个窗口特征:")
        for key, value in windows[0].items():
            print(f"  {key}: shape {value.shape}")
    
    print("\n✓ 预处理测试通过！")
