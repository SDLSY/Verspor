"""
多模态Transformer时序睡眠建模实现
Multi-Modal Transformer for Sleep Analysis (MMTSA)

功能：
1. 融合7种生理信号（心率、HRV、血氧、体温、PPG、加速度、陀螺仪）
2. 基于Transformer架构进行时序建模
3. 多任务学习：睡眠分期、恢复指数预测、异常检测

作者：Sleep Health Team
日期：2026-01-27
"""

import torch
import torch.nn as nn
import torch.nn.functional as F
import numpy as np
from typing import Dict, Tuple, Optional
import math


class PositionalEncoding(nn.Module):
    """位置编码器，为时序数据添加位置信息"""
    
    def __init__(self, d_model: int, max_len: int = 5000, dropout: float = 0.1):
        super(PositionalEncoding, self).__init__()
        self.dropout = nn.Dropout(p=dropout)
        
        # 创建位置编码矩阵
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(torch.arange(0, d_model, 2).float() * 
                            (-math.log(10000.0) / d_model))
        
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        pe = pe.unsqueeze(0).transpose(0, 1)
        
        self.register_buffer('pe', pe)
    
    def forward(self, x):
        """
        Args:
            x: Tensor, shape [seq_len, batch_size, embedding_dim]
        """
        x = x + self.pe[:x.size(0), :]
        return self.dropout(x)


class PPGEncoder(nn.Module):
    """PPG信号编码器（1D-CNN）
    
    PPG(光电容积脉搏波)信号包含丰富的心血管信息，
    使用CNN提取局部模式特征
    """
    
    def __init__(self, in_channels: int = 1, out_dim: int = 64):
        super(PPGEncoder, self).__init__()
        
        # 多尺度卷积，捕获不同频率的特征
        self.conv1 = nn.Conv1d(in_channels, 32, kernel_size=7, stride=2, padding=3)
        self.bn1 = nn.BatchNorm1d(32)
        
        self.conv2 = nn.Conv1d(32, 64, kernel_size=5, stride=2, padding=2)
        self.bn2 = nn.BatchNorm1d(64)
        
        self.conv3 = nn.Conv1d(64, out_dim, kernel_size=3, stride=2, padding=1)
        self.bn3 = nn.BatchNorm1d(out_dim)
        
        self.pool = nn.AdaptiveAvgPool1d(1)
        self.dropout = nn.Dropout(0.3)
    
    def forward(self, x):
        """
        Args:
            x: Tensor, shape [batch_size, 1, seq_len] (PPG波形)
        Returns:
            Tensor, shape [batch_size, out_dim]
        """
        x = F.relu(self.bn1(self.conv1(x)))
        x = self.dropout(x)
        
        x = F.relu(self.bn2(self.conv2(x)))
        x = self.dropout(x)
        
        x = F.relu(self.bn3(self.conv3(x)))
        x = self.pool(x).squeeze(-1)  # [batch_size, out_dim]
        
        return x


class MLPEncoder(nn.Module):
    """MLP编码器（用于统计特征）
    
    心率、HRV、体温等统计特征使用MLP编码
    """
    
    def __init__(self, in_dim: int, out_dim: int):
        super(MLPEncoder, self).__init__()
        
        self.fc1 = nn.Linear(in_dim, out_dim * 2)
        self.fc2 = nn.Linear(out_dim * 2, out_dim)
        self.bn1 = nn.BatchNorm1d(out_dim * 2)
        self.dropout = nn.Dropout(0.2)
    
    def forward(self, x):
        """
        Args:
            x: Tensor, shape [batch_size, in_dim]
        Returns:
            Tensor, shape [batch_size, out_dim]
        """
        x = F.relu(self.bn1(self.fc1(x)))
        x = self.dropout(x)
        x = self.fc2(x)
        return x


class LSTMEncoder(nn.Module):
    """LSTM编码器（用于运动数据）
    
    加速度和陀螺仪数据具有明显的时序依赖性，
    使用LSTM提取时序特征
    """
    
    def __init__(self, in_dim: int, out_dim: int, num_layers: int = 2):
        super(LSTMEncoder, self).__init__()
        
        self.lstm = nn.LSTM(
            input_size=in_dim,
            hidden_size=out_dim,
            num_layers=num_layers,
            batch_first=True,
            dropout=0.2 if num_layers > 1 else 0,
            bidirectional=True
        )
        self.fc = nn.Linear(out_dim * 2, out_dim)  # 双向LSTM，需要合并
    
    def forward(self, x):
        """
        Args:
            x: Tensor, shape [batch_size, seq_len, in_dim]
        Returns:
            Tensor, shape [batch_size, out_dim]
        """
        # LSTM输出
        lstm_out, (h_n, c_n) = self.lstm(x)
        
        # 使用最后一个时刻的输出
        # h_n: [num_layers*2, batch_size, out_dim]
        # 取最后一层的前向和后向隐状态
        forward_hidden = h_n[-2, :, :]
        backward_hidden = h_n[-1, :, :]
        hidden = torch.cat([forward_hidden, backward_hidden], dim=1)
        
        # 映射到目标维度
        output = self.fc(hidden)  # [batch_size, out_dim]
        return output


class MultiModalTransformer(nn.Module):
    """多模态Transformer睡眠分析模型
    
    架构：
    1. 各模态独立编码器（PPG-CNN, HR-MLP, HRV-MLP, Temp-MLP, Motion-LSTM）
    2. 多模态融合（Transformer Encoder）
    3. 多任务输出（睡眠分期、恢复指数、异常检测）
    """
    
    def __init__(
        self,
        ppg_seq_len: int = 1500,  # PPG序列长度（30秒×50Hz）
        motion_seq_len: int = 750,  # 运动序列长度（30秒×25Hz）
        d_model: int = 176,  # Transformer模型维度
        nhead: int = 8,  # 注意力头数
        num_layers: int = 4,  # Transformer层数
        dim_feedforward: int = 512,  # 前馈网络维度
        dropout: float = 0.1,
        num_sleep_stages: int = 5  # 睡眠分期数（清醒、N1、N2、N3、REM）
    ):
        super(MultiModalTransformer, self).__init__()
        
        # ===== 1. 各模态编码器 =====
        # PPG编码器（CNN）: 输出64维
        self.ppg_encoder = PPGEncoder(in_channels=1, out_dim=64)
        
        # 心率特征编码器（MLP）: 输入4维（均值、标准差、最小值、最大值） -> 输出32维
        self.hr_encoder = MLPEncoder(in_dim=4, out_dim=32)
        
        # HRV特征编码器（MLP）: 输入3维（RMSSD、SDNN、LF/HF比） -> 输出32维
        self.hrv_encoder = MLPEncoder(in_dim=3, out_dim=32)
        
        # 血氧特征编码器（MLP）: 输入3维（均值、标准差、最小值） -> 输出16维
        self.spo2_encoder = MLPEncoder(in_dim=3, out_dim=16)
        
        # 体温特征编码器（MLP）: 输入2维（均值、趋势） -> 输出16维
        self.temp_encoder = MLPEncoder(in_dim=2, out_dim=16)
        
        # 运动特征编码器（LSTM）: 输入6维（3轴加速度+3轴陀螺仪） -> 输出32维
        self.motion_encoder = LSTMEncoder(in_dim=6, out_dim=32, num_layers=2)
        
        # 总维度: 64 + 32 + 32 + 16 + 16 + 32 = 192
        # 为了与d_model=176对齐，添加投影层
        self.feature_projection = nn.Linear(192, d_model)
        
        # ===== 2. 位置编码 =====
        self.pos_encoder = PositionalEncoding(d_model, dropout=dropout)
        
        # ===== 3. Transformer编码器 =====
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=nhead,
            dim_feedforward=dim_feedforward,
            dropout=dropout,
            activation='gelu',  # 使用GELU激活函数（效果优于ReLU）
            batch_first=True
        )
        self.transformer_encoder = nn.TransformerEncoder(
            encoder_layer,
            num_layers=num_layers
        )
        
        # ===== 4. 多任务输出头 =====
        # 睡眠分期分类头（5类：清醒、N1、N2、N3、REM）
        self.sleep_stage_head = nn.Sequential(
            nn.Linear(d_model, 128),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(128, num_sleep_stages)
        )
        
        # 恢复指数回归头（0-100分）
        self.recovery_head = nn.Sequential(
            nn.Linear(d_model, 128),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(128, 64),
            nn.ReLU(),
            nn.Linear(64, 1),
            nn.Sigmoid()  # 归一化到[0,1]，后续乘以100得分
        )
        
        # 异常检测头（二分类：正常/异常）
        self.anomaly_head = nn.Sequential(
            nn.Linear(d_model, 64),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(64, 1),
            nn.Sigmoid()
        )
    
    def forward(
        self,
        ppg: torch.Tensor,
        hr_features: torch.Tensor,
        hrv_features: torch.Tensor,
        spo2_features: torch.Tensor,
        temp_features: torch.Tensor,
        motion: torch.Tensor
    ) -> Tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        """
        前向传播
        
        Args:
            ppg: PPG波形, shape [batch_size, 1, ppg_seq_len]
            hr_features: 心率统计特征, shape [batch_size, 4]
            hrv_features: HRV特征, shape [batch_size, 3]
            spo2_features: 血氧特征, shape [batch_size, 3]
            temp_features: 体温特征, shape [batch_size, 2]
            motion: 运动数据, shape [batch_size, motion_seq_len, 6]
        
        Returns:
            sleep_stage_logits: 睡眠分期logits, shape [batch_size, 5]
            recovery_score: 恢复指数, shape [batch_size, 1]
            anomaly_score: 异常检测分数, shape [batch_size, 1]
        """
        batch_size = ppg.size(0)
        
        # ===== 1. 各模态特征编码 =====
        ppg_feat = self.ppg_encoder(ppg)  # [batch_size, 64]
        hr_feat = self.hr_encoder(hr_features)  # [batch_size, 32]
        hrv_feat = self.hrv_encoder(hrv_features)  # [batch_size, 32]
        spo2_feat = self.spo2_encoder(spo2_features)  # [batch_size, 16]
        temp_feat = self.temp_encoder(temp_features)  # [batch_size, 16]
        motion_feat = self.motion_encoder(motion)  # [batch_size, 32]
        
        # ===== 2. 多模态特征融合 =====
        # 拼接所有模态特征
        fused_features = torch.cat([
            ppg_feat,
            hr_feat,
            hrv_feat,
            spo2_feat,
            temp_feat,
            motion_feat
        ], dim=-1)  # [batch_size, 192]
        
        # 投影到Transformer维度
        fused_features = self.feature_projection(fused_features)  # [batch_size, d_model]
        
        # 添加序列维度（这里我们将融合特征视为单个时间步）
        # 在实际应用中，可以将窗口划分为多个子窗口，形成序列
        fused_features = fused_features.unsqueeze(1)  # [batch_size, 1, d_model]
        
        # 添加位置编码
        fused_features = fused_features.transpose(0, 1)  # [1, batch_size, d_model]
        fused_features = self.pos_encoder(fused_features)
        fused_features = fused_features.transpose(0, 1)  # [batch_size, 1, d_model]
        
        # ===== 3. Transformer编码 =====
        transformer_output = self.transformer_encoder(fused_features)  # [batch_size, 1, d_model]
        transformer_output = transformer_output.squeeze(1)  # [batch_size, d_model]
        
        # ===== 4. 多任务输出 =====
        sleep_stage_logits = self.sleep_stage_head(transformer_output)  # [batch_size, 5]
        recovery_score = self.recovery_head(transformer_output) * 100  # [batch_size, 1], 范围[0, 100]
        anomaly_score = self.anomaly_head(transformer_output)  # [batch_size, 1]
        
        return sleep_stage_logits, recovery_score, anomaly_score


class MultiTaskLoss(nn.Module):
    """多任务损失函数
    
    结合睡眠分期、恢复指数预测、异常检测三个任务的损失
    """
    
    def __init__(
        self,
        alpha: float = 1.0,  # 睡眠分期权重
        beta: float = 0.5,  # 恢复指数权重
        gamma: float = 0.3  # 异常检测权重
    ):
        super(MultiTaskLoss, self).__init__()
        self.alpha = alpha
        self.beta = beta
        self.gamma = gamma
        
        # 睡眠分期：交叉熵损失（处理类别不平衡）
        self.stage_criterion = nn.CrossEntropyLoss()
        
        # 恢复指数：MSE损失
        self.recovery_criterion = nn.MSELoss()
        
        # 异常检测：二元交叉熵损失
        self.anomaly_criterion = nn.BCELoss()
    
    def forward(
        self,
        stage_logits: torch.Tensor,
        stage_labels: torch.Tensor,
        recovery_pred: torch.Tensor,
        recovery_true: torch.Tensor,
        anomaly_pred: torch.Tensor,
        anomaly_labels: torch.Tensor
    ) -> Tuple[torch.Tensor, Dict[str, float]]:
        """
        计算总损失
        
        Returns:
            total_loss: 总损失
            loss_dict: 各任务损失字典（用于监控）
        """
        # 各任务损失
        stage_loss = self.stage_criterion(stage_logits, stage_labels)
        recovery_loss = self.recovery_criterion(recovery_pred, recovery_true)
        anomaly_loss = self.anomaly_criterion(anomaly_pred, anomaly_labels)
        
        # 加权总损失
        total_loss = (
            self.alpha * stage_loss +
            self.beta * recovery_loss +
            self.gamma * anomaly_loss
        )
        
        # 损失字典（用于记录）
        loss_dict = {
            'total': total_loss.item(),
            'stage': stage_loss.item(),
            'recovery': recovery_loss.item(),
            'anomaly': anomaly_loss.item()
        }
        
        return total_loss, loss_dict


def create_model(device: str = 'cuda') -> MultiModalTransformer:
    """创建模型实例"""
    model = MultiModalTransformer(
        ppg_seq_len=1500,
        motion_seq_len=750,
        d_model=176,
        nhead=8,
        num_layers=4,
        dim_feedforward=512,
        dropout=0.1,
        num_sleep_stages=5
    )
    
    model = model.to(device)
    
    # 打印模型参数量
    total_params = sum(p.numel() for p in model.parameters())
    trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"Total parameters: {total_params:,}")
    print(f"Trainable parameters: {trainable_params:,}")
    
    return model


if __name__ == '__main__':
    # 测试模型
    print("=" * 60)
    print("多模态Transformer睡眠分析模型测试")
    print("=" * 60)
    
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    print(f"使用设备: {device}\n")
    
    # 创建模型
    model = create_model(device)
    
    # 创建模拟输入数据
    batch_size = 4
    ppg = torch.randn(batch_size, 1, 1500).to(device)  # PPG波形
    hr_features = torch.randn(batch_size, 4).to(device)  # 心率特征
    hrv_features = torch.randn(batch_size, 3).to(device)  # HRV特征
    spo2_features = torch.randn(batch_size, 3).to(device)  # 血氧特征
    temp_features = torch.randn(batch_size, 2).to(device)  # 体温特征
    motion = torch.randn(batch_size, 750, 6).to(device)  # 运动数据
    
    # 前向传播
    print("前向传播测试...")
    stage_logits, recovery_score, anomaly_score = model(
        ppg, hr_features, hrv_features, spo2_features, temp_features, motion
    )
    
    print(f"睡眠分期logits形状: {stage_logits.shape}")
    print(f"恢复指数形状: {recovery_score.shape}")
    print(f"异常检测分数形状: {anomaly_score.shape}")
    
    # 测试损失函数
    print("\n损失函数测试...")
    criterion = MultiTaskLoss(alpha=1.0, beta=0.5, gamma=0.3)
    
    stage_labels = torch.randint(0, 5, (batch_size,)).to(device)
    recovery_true = torch.rand(batch_size, 1).to(device) * 100
    anomaly_labels = torch.randint(0, 2, (batch_size, 1)).float().to(device)
    
    total_loss, loss_dict = criterion(
        stage_logits, stage_labels,
        recovery_score, recovery_true,
        anomaly_score, anomaly_labels
    )
    
    print(f"总损失: {loss_dict['total']:.4f}")
    print(f"  - 睡眠分期损失: {loss_dict['stage']:.4f}")
    print(f"  - 恢复指数损失: {loss_dict['recovery']:.4f}")
    print(f"  - 异常检测损失: {loss_dict['anomaly']:.4f}")
    
    print("\n✓ 模型测试通过！")
