"""
快速开始示例

这个脚本演示如何快速测试多模态Transformer模型：
1. 创建模拟数据
2. 加载模型
3. 运行推理
4. 可视化结果
"""

import torch
import numpy as np
import matplotlib.pyplot as plt
from MultiModalTransformer import create_model, MultiModalTransformer
from preprocessing import DataPreprocessor
import os


def create_demo_data():
    """创建演示数据（模拟睡眠期间的生理信号）"""
    print("\n" + "="*60)
    print("创建演示数据...")
    print("="*60)
    
    # 模拟30秒窗口的数据
    duration = 30  # 秒
    
    # 1. PPG波形 (50Hz采样)
    fs_ppg = 50
    t_ppg = np.linspace(0, duration, duration * fs_ppg)
    # 模拟心率60bpm的PPG信号
    hr = 60  # 心率
    ppg = np.sin(2 * np.pi * hr / 60 * t_ppg) + 0.3 * np.random.randn(len(t_ppg))
    ppg = (ppg - np.mean(ppg)) / (np.std(ppg) + 1e-8)
    
    # 2. 心率特征
    hr_features = np.array([
        58.5,  # 平均心率
        3.2,   # 标准差
        52.0,  # 最小值
        65.0   # 最大值
    ])
    
    # 3. HRV特征
    hrv_features = np.array([
        45.2,  # RMSSD
        52.8,  # SDNN
        1.5    # LF/HF比率
    ])
    
    # 4. 血氧特征
    spo2_features = np.array([
        97.5,  # 平均血氧
        0.8,   # 标准差
        96.0   # 最小值
    ])
    
    # 5. 体温特征
    temp_features = np.array([
        36.5,  # 平均体温
        -0.01  # 温度趋势（轻微下降）
    ])
    
    # 6. 运动数据 (25Hz采样)
    fs_motion = 25
    t_motion = np.linspace(0, duration, duration * fs_motion)
    # 睡眠期间运动较少
    motion = 0.1 * np.random.randn(len(t_motion), 6)  # 6轴传感器
    
    # 转换为PyTorch张量
    data = {
        'ppg': torch.FloatTensor(ppg).reshape(1, 1, -1),
        'hr_features': torch.FloatTensor(hr_features).unsqueeze(0),
        'hrv_features': torch.FloatTensor(hrv_features).unsqueeze(0),
        'spo2_features': torch.FloatTensor(spo2_features).unsqueeze(0),
        'temp_features': torch.FloatTensor(temp_features).unsqueeze(0),
        'motion': torch.FloatTensor(motion).unsqueeze(0)
    }
    
    print("\n数据形状:")
    for key, value in data.items():
        print(f"  {key}: {value.shape}")
    
    return data, t_ppg, ppg


def run_inference(model, data, device='cpu'):
    """运行模型推理"""
    print("\n" + "="*60)
    print("运行模型推理...")
    print("="*60)
    
    model.eval()
    
    # 将数据移到设备
    for key in data:
        data[key] = data[key].to(device)
    
    # 推理
    with torch.no_grad():
        stage_logits, recovery_score, anomaly_score = model(
            data['ppg'],
            data['hr_features'],
            data['hrv_features'],
            data['spo2_features'],
            data['temp_features'],
            data['motion']
        )
    
    # 解析结果
    sleep_stages = ['清醒', 'N1轻睡', 'N2浅睡', 'N3深睡', 'REM快速眼动']
    stage_probs = torch.softmax(stage_logits, dim=1)[0]
    predicted_stage = torch.argmax(stage_probs).item()
    
    recovery = recovery_score[0, 0].item()
    anomaly_prob = anomaly_score[0, 0].item()
    
    print("\n预测结果:")
    print(f"  睡眠分期: {sleep_stages[predicted_stage]}")
    print(f"  恢复指数: {recovery:.1f}/100")
    print(f"  异常检测: {'异常' if anomaly_prob > 0.5 else '正常'} (概率: {anomaly_prob:.2%})")
    
    print("\n各睡眠分期概率:")
    for i, stage in enumerate(sleep_stages):
        prob = stage_probs[i].item()
        bar = '█' * int(prob * 50)
        print(f"  {stage:8s}: {bar:50s} {prob:.1%}")
    
    return {
        'sleep_stage': predicted_stage,
        'stage_probs': stage_probs.cpu().numpy(),
        'recovery_score': recovery,
        'anomaly_prob': anomaly_prob
    }


def visualize_results(t_ppg, ppg, results):
    """可视化结果"""
    print("\n" + "="*60)
    print("生成可视化...")
    print("="*60)
    
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle('多模态Transformer睡眠分析结果', fontsize=16, fontweight='bold')
    
    # 1. PPG波形
    ax1 = axes[0, 0]
    ax1.plot(t_ppg, ppg, linewidth=0.8, alpha=0.8)
    ax1.set_xlabel('时间 (秒)', fontsize=10)
    ax1.set_ylabel('PPG信号 (标准化)', fontsize=10)
    ax1.set_title('PPG波形 (30秒窗口)', fontsize=11, fontweight='bold')
    ax1.grid(True, alpha=0.3)
    
    # 2. 睡眠分期概率分布
    ax2 = axes[0, 1]
    sleep_stages = ['清醒', 'N1', 'N2', 'N3', 'REM']
    colors = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7']
    bars = ax2.bar(sleep_stages, results['stage_probs'], color=colors, alpha=0.8, edgecolor='black')
    ax2.set_ylabel('概率', fontsize=10)
    ax2.set_title('睡眠分期概率分布', fontsize=11, fontweight='bold')
    ax2.set_ylim([0, 1])
    ax2.grid(axis='y', alpha=0.3)
    
    # 在条形图上显示数值
    for bar in bars:
        height = bar.get_height()
        ax2.text(bar.get_x() + bar.get_width()/2., height,
                f'{height:.1%}',
                ha='center', va='bottom', fontsize=9)
    
    # 3. 恢复指数仪表盘
    ax3 = axes[1, 0]
    recovery = results['recovery_score']
    
    # 绘制仪表盘
    theta = np.linspace(np.pi, 0, 100)
    r = np.ones_like(theta)
    
    # 背景半圆
    ax3.fill_between(theta, 0, r, color='lightgray', alpha=0.3)
    
    # 彩色分段
    colors_gauge = ['#FF6B6B', '#FFD93D', '#6BCB77']
    thresholds = [np.pi * 2/3, np.pi * 1/3, 0]
    for i in range(len(colors_gauge)):
        # 确定上限阈值
        upper_threshold = thresholds[i-1] if i > 0 else np.pi
        # 创建掩码
        mask = (theta >= thresholds[i]) & (theta < upper_threshold)
        theta_seg = theta[mask]
        r_seg = r[mask]
        ax3.fill_between(theta_seg, 0, r_seg, color=colors_gauge[i], alpha=0.5)
    
    # 指针
    recovery_angle = np.pi * (1 - recovery / 100)
    ax3.plot([recovery_angle, recovery_angle], [0, 0.9], 'k-', linewidth=3)
    ax3.plot(recovery_angle, 0.9, 'ko', markersize=10)
    
    # 刻度
    for angle, label in zip([np.pi, np.pi*2/3, np.pi/3, 0], ['0', '33', '67', '100']):
        ax3.text(angle, 1.15, label, ha='center', va='center', fontsize=9)
    
    ax3.text(np.pi/2, -0.3, f'{recovery:.1f}/100', 
             ha='center', va='center', fontsize=20, fontweight='bold')
    ax3.set_xlim([0, np.pi])
    ax3.set_ylim([-0.5, 1.3])
    ax3.axis('off')
    ax3.set_title('恢复指数', fontsize=11, fontweight='bold')
    
    # 4. 异常检测结果
    ax4 = axes[1, 1]
    anomaly_prob = results['anomaly_prob']
    
    # 绘制仪表
    categories = ['正常', '异常']
    probs = [1 - anomaly_prob, anomaly_prob]
    colors_anomaly = ['#6BCB77', '#FF6B6B']
    
    wedges, texts, autotexts = ax4.pie(
        probs,
        labels=categories,
        colors=colors_anomaly,
        autopct='%1.1f%%',
        startangle=90,
        explode=(0.05 if anomaly_prob < 0.5 else 0, 0 if anomaly_prob < 0.5 else 0.05),
        textprops={'fontsize': 10}
    )
    
    for autotext in autotexts:
        autotext.set_color('white')
        autotext.set_fontweight('bold')
    
    ax4.set_title('异常检测', fontsize=11, fontweight='bold')
    
    plt.tight_layout()
    
    # 保存图像
    output_path = 'demo_results.png'
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"\n可视化结果已保存到: {output_path}")
    
    # 显示图像
    plt.show()


def main():
    """主函数"""
    print("\n" + "="*70)
    print("  多模态Transformer睡眠分析 - 快速开始示例  ".center(70))
    print("="*70)
    
    # 设置设备
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    print(f"\n使用设备: {device}")
    
    # 1. 创建模型
    print("\n" + "="*60)
    print("创建模型...")
    print("="*60)
    model = create_model(device)
    
    # 2. 创建演示数据
    data, t_ppg, ppg = create_demo_data()
    
    # 3. 运行推理
    results = run_inference(model, data, device)
    
    # 4. 可视化结果
    visualize_results(t_ppg, ppg, results)
    
    print("\n" + "="*70)
    print("  演示完成！  ".center(70))
    print("="*70)
    print("\n提示:")
    print("  1. 当前使用的是随机初始化模型，实际应用需要加载训练好的权重")
    print("  2. 运行 train.py 训练模型")
    print("  3. 运行 export_model.py 导出模型用于Android端")
    print("\n")


if __name__ == '__main__':
    main()
