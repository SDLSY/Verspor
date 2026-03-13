"""
快速开始示例 - 简化版（不需要PyTorch）

这个脚本演示多模态Transformer的基本概念，不需要安装PyTorch
只展示数据结构和可视化
"""

import numpy as np
import matplotlib.pyplot as plt
import matplotlib
matplotlib.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']  # 中文字体
matplotlib.rcParams['axes.unicode_minus'] = False  # 负号显示


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
    
    print("\n数据形状:")
    print(f"  PPG波形: {ppg.shape}")
    print(f"  采样时长: {duration}秒")
    print(f"  采样率: {fs_ppg}Hz")
    
    return t_ppg, ppg


def simulate_inference_results():
    """模拟模型推理结果"""
    print("\n" + "="*60)
    print("模拟模型推理...")
    print("="*60)
    
    # 睡眠分期概率（模拟值）
    sleep_stages = ['清醒', 'N1轻睡', 'N2浅睡', 'N3深睡', 'REM快速眼动']
    # 模拟一个N2浅睡状态
    stage_probs = np.array([0.05, 0.15, 0.60, 0.15, 0.05])
    predicted_stage = np.argmax(stage_probs)
    
    # 恢复指数（模拟值）
    recovery = 78.5
    
    # 异常检测（模拟值）
    anomaly_prob = 0.12
    
    print("\n预测结果:")
    print(f"  睡眠分期: {sleep_stages[predicted_stage]}")
    print(f"  恢复指数: {recovery:.1f}/100")
    print(f"  异常检测: {'异常' if anomaly_prob > 0.5 else '正常'} (概率: {anomaly_prob:.2%})")
    
    print("\n各睡眠分期概率:")
    for i, stage in enumerate(sleep_stages):
        prob = stage_probs[i]
        bar = '█' * int(prob * 50)
        print(f"  {stage:8s}: {bar:50s} {prob:.1%}")
    
    return {
        'sleep_stage': predicted_stage,
        'stage_probs': stage_probs,
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
    ax1.plot(t_ppg, ppg, linewidth=0.8, alpha=0.8, color='#3498db')
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
    
    # 彩色分段（修复版本）
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
    print(f"\n✓ 可视化结果已保存到: {output_path}")
    
    # 显示图像
    plt.show()


def main():
    """主函数"""
    print("\n" + "="*70)
    print("  多模态Transformer睡眠分析 - 快速开始示例 (简化版)  ".center(70))
    print("="*70)
    
    print("\n说明：这是简化版演示，不需要安装PyTorch")
    print("展示多模态Transformer的基本概念和可视化效果\n")
    
    # 1. 创建演示数据
    t_ppg, ppg = create_demo_data()
    
    # 2. 模拟推理结果
    results = simulate_inference_results()
    
    # 3. 可视化结果
    visualize_results(t_ppg, ppg, results)
    
    print("\n" + "="*70)
    print("  演示完成！  ".center(70))
    print("="*70)
    print("\n提示:")
    print("  1. 当前是简化版，展示了概念和可视化")
    print("  2. 完整版需要安装PyTorch：pip install torch")
    print("  3. 完整版包含真实的神经网络模型推理")
    print("\n安装完整环境后，可运行：")
    print("  python quick_start.py  # (需要PyTorch)")
    print("\n")


if __name__ == '__main__':
    main()
