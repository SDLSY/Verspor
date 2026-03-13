"""
多模态Transformer睡眠分析模型训练脚本

功能：
1. 数据加载与预处理
2. 模型训练
3. 模型评估
4. 模型导出（PyTorch -> TFLite）

使用方法：
    python train.py --data_dir ./data --epochs 100 --batch_size 64
"""

import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
from torch.optim.lr_scheduler import CosineAnnealingLR
import numpy as np
import argparse
import os
from tqdm import tqdm
from typing import Dict, Tuple
import json
from datetime import datetime

from MultiModalTransformer import (
    MultiModalTransformer,
    MultiTaskLoss,
    create_model
)


class SleepDataset(Dataset):
    """睡眠数据集类
    
    用于加载和预处理睡眠数据
    """
    
    def __init__(
        self,
        data_dir: str,
        split: str = 'train',
        ppg_seq_len: int = 1500,
        motion_seq_len: int = 750
    ):
        """
        Args:
            data_dir: 数据目录路径
            split: 'train', 'val', 或 'test'
            ppg_seq_len: PPG序列长度
            motion_seq_len: 运动序列长度
        """
        self.data_dir = data_dir
        self.split = split
        self.ppg_seq_len = ppg_seq_len
        self.motion_seq_len = motion_seq_len
        
        # 加载数据索引
        self.samples = self._load_sample_list()
        
        print(f"加载 {split} 数据集: {len(self.samples)} 个样本")
    
    def _load_sample_list(self):
        """加载样本列表"""
        # 这里假设数据已经预处理并保存为numpy文件
        # 实际应用中，需要根据数据格式调整
        index_file = os.path.join(self.data_dir, f'{self.split}_index.txt')
        
        if not os.path.exists(index_file):
            # 如果索引文件不存在，创建模拟数据用于测试
            print(f"警告: {index_file} 不存在，使用模拟数据")
            return [f'sample_{i}' for i in range(100)]
        
        with open(index_file, 'r') as f:
            samples = [line.strip() for line in f.readlines()]
        
        return samples
    
    def __len__(self):
        return len(self.samples)
    
    def __getitem__(self, idx):
        """获取一个样本
        
        Returns:
            data: Dict包含所有模态数据
            labels: Dict包含所有标签
        """
        sample_id = self.samples[idx]
        
        # 加载数据文件（这里使用模拟数据）
        # 实际应用中需要从文件加载真实数据
        data = self._load_sample_data(sample_id)
        labels = self._load_sample_labels(sample_id)
        
        return data, labels
    
    def _load_sample_data(self, sample_id: str) -> Dict[str, torch.Tensor]:
        """加载样本数据（模拟数据）"""
        # 实际应用中，从numpy文件或数据库加载
        # 这里生成模拟数据用于测试
        
        data = {
            'ppg': torch.randn(1, self.ppg_seq_len),
            'hr_features': torch.randn(4),
            'hrv_features': torch.randn(3),
            'spo2_features': torch.randn(3),
            'temp_features': torch.randn(2),
            'motion': torch.randn(self.motion_seq_len, 6)
        }
        
        return data
    
    def _load_sample_labels(self, sample_id: str) -> Dict[str, torch.Tensor]:
        """加载样本标签（模拟标签）"""
        labels = {
            'sleep_stage': torch.randint(0, 5, (1,)).item(),  # 0-4: 5种睡眠分期
            'recovery_score': torch.rand(1) * 100,  # 0-100
            'anomaly': torch.randint(0, 2, (1,)).float()  # 0或1
        }
        
        return labels


def collate_fn(batch):
    """自定义batch整理函数"""
    data_batch = {}
    labels_batch = {}
    
    # 收集所有样本的数据
    for key in batch[0][0].keys():
        data_batch[key] = torch.stack([item[0][key] for item in batch])
    
    for key in batch[0][1].keys():
        if key == 'sleep_stage':
            labels_batch[key] = torch.tensor([item[1][key] for item in batch])
        else:
            labels_batch[key] = torch.stack([item[1][key] for item in batch])
    
    return data_batch, labels_batch


class Trainer:
    """训练器类"""
    
    def __init__(
        self,
        model: nn.Module,
        train_loader: DataLoader,
        val_loader: DataLoader,
        criterion: nn.Module,
        optimizer: optim.Optimizer,
        scheduler: optim.lr_scheduler._LRScheduler,
        device: str,
        save_dir: str = './checkpoints'
    ):
        self.model = model
        self.train_loader = train_loader
        self.val_loader = val_loader
        self.criterion = criterion
        self.optimizer = optimizer
        self.scheduler = scheduler
        self.device = device
        self.save_dir = save_dir
        
        os.makedirs(save_dir, exist_ok=True)
        
        # 训练历史
        self.history = {
            'train_loss': [],
            'val_loss': [],
            'train_acc': [],
            'val_acc': []
        }
    
    def train_epoch(self, epoch: int) -> Dict[str, float]:
        """训练一个epoch"""
        self.model.train()
        
        total_loss = 0
        stage_correct = 0
        total_samples = 0
        
        pbar = tqdm(self.train_loader, desc=f'Epoch {epoch}')
        
        for batch_idx, (data, labels) in enumerate(pbar):
            # 将数据移到设备
            for key in data:
                data[key] = data[key].to(self.device)
            for key in labels:
                labels[key] = labels[key].to(self.device)
            
            # 前向传播
            stage_logits, recovery_pred, anomaly_pred = self.model(
                data['ppg'],
                data['hr_features'],
                data['hrv_features'],
                data['spo2_features'],
                data['temp_features'],
                data['motion']
            )
            
            # 计算损失
            loss, loss_dict = self.criterion(
                stage_logits,
                labels['sleep_stage'],
                recovery_pred,
                labels['recovery_score'],
                anomaly_pred,
                labels['anomaly']
            )
            
            # 反向传播
            self.optimizer.zero_grad()
            loss.backward()
            
            # 梯度裁剪（防止梯度爆炸）
            torch.nn.utils.clip_grad_norm_(self.model.parameters(), max_norm=1.0)
            
            self.optimizer.step()
            
            # 统计
            total_loss += loss.item()
            stage_pred = torch.argmax(stage_logits, dim=1)
            stage_correct += (stage_pred == labels['sleep_stage']).sum().item()
            total_samples += len(labels['sleep_stage'])
            
            # 更新进度条
            pbar.set_postfix({
                'loss': f"{loss.item():.4f}",
                'acc': f"{stage_correct/total_samples:.4f}"
            })
        
        avg_loss = total_loss / len(self.train_loader)
        avg_acc = stage_correct / total_samples
        
        return {
            'loss': avg_loss,
            'accuracy': avg_acc
        }
    
    def validate(self) -> Dict[str, float]:
        """验证"""
        self.model.eval()
        
        total_loss = 0
        stage_correct = 0
        total_samples = 0
        
        with torch.no_grad():
            for data, labels in tqdm(self.val_loader, desc='Validation'):
                # 将数据移到设备
                for key in data:
                    data[key] = data[key].to(self.device)
                for key in labels:
                    labels[key] = labels[key].to(self.device)
                
                # 前向传播
                stage_logits, recovery_pred, anomaly_pred = self.model(
                    data['ppg'],
                    data['hr_features'],
                    data['hrv_features'],
                    data['spo2_features'],
                    data['temp_features'],
                    data['motion']
                )
                
                # 计算损失
                loss, _ = self.criterion(
                    stage_logits,
                    labels['sleep_stage'],
                    recovery_pred,
                    labels['recovery_score'],
                    anomaly_pred,
                    labels['anomaly']
                )
                
                # 统计
                total_loss += loss.item()
                stage_pred = torch.argmax(stage_logits, dim=1)
                stage_correct += (stage_pred == labels['sleep_stage']).sum().item()
                total_samples += len(labels['sleep_stage'])
        
        avg_loss = total_loss / len(self.val_loader)
        avg_acc = stage_correct / total_samples
        
        return {
            'loss': avg_loss,
            'accuracy': avg_acc
        }
    
    def train(self, num_epochs: int):
        """完整训练流程"""
        best_val_loss = float('inf')
        
        print(f"\n{'='*60}")
        print(f"开始训练 - 共 {num_epochs} 个epochs")
        print(f"{'='*60}\n")
        
        for epoch in range(1, num_epochs + 1):
            # 训练
            train_metrics = self.train_epoch(epoch)
            
            # 验证
            val_metrics = self.validate()
            
            # 更新学习率
            self.scheduler.step()
            
            # 记录历史
            self.history['train_loss'].append(train_metrics['loss'])
            self.history['val_loss'].append(val_metrics['loss'])
            self.history['train_acc'].append(train_metrics['accuracy'])
            self.history['val_acc'].append(val_metrics['accuracy'])
            
            # 打印结果
            print(f"\nEpoch {epoch}/{num_epochs}")
            print(f"  Train - Loss: {train_metrics['loss']:.4f}, Acc: {train_metrics['accuracy']:.4f}")
            print(f"  Val   - Loss: {val_metrics['loss']:.4f}, Acc: {val_metrics['accuracy']:.4f}")
            print(f"  LR: {self.scheduler.get_last_lr()[0]:.6f}")
            
            # 保存最佳模型
            if val_metrics['loss'] < best_val_loss:
                best_val_loss = val_metrics['loss']
                self.save_checkpoint(epoch, is_best=True)
                print(f"  ✓ 保存最佳模型 (val_loss: {best_val_loss:.4f})")
            
            # 定期保存检查点
            if epoch % 10 == 0:
                self.save_checkpoint(epoch, is_best=False)
        
        # 保存训练历史
        self.save_history()
        
        print(f"\n{'='*60}")
        print(f"训练完成！最佳验证损失: {best_val_loss:.4f}")
        print(f"{'='*60}\n")
    
    def save_checkpoint(self, epoch: int, is_best: bool = False):
        """保存检查点"""
        checkpoint = {
            'epoch': epoch,
            'model_state_dict': self.model.state_dict(),
            'optimizer_state_dict': self.optimizer.state_dict(),
            'scheduler_state_dict': self.scheduler.state_dict(),
            'history': self.history
        }
        
        if is_best:
            path = os.path.join(self.save_dir, 'best_model.pth')
        else:
            path = os.path.join(self.save_dir, f'checkpoint_epoch_{epoch}.pth')
        
        torch.save(checkpoint, path)
    
    def save_history(self):
        """保存训练历史"""
        history_path = os.path.join(self.save_dir, 'training_history.json')
        with open(history_path, 'w') as f:
            json.dump(self.history, f, indent=2)


def main():
    # 解析命令行参数
    parser = argparse.ArgumentParser(description='训练多模态Transformer睡眠分析模型')
    parser.add_argument('--data_dir', type=str, default='./data', help='数据目录')
    parser.add_argument('--save_dir', type=str, default='./checkpoints', help='模型保存目录')
    parser.add_argument('--epochs', type=int, default=100, help='训练轮数')
    parser.add_argument('--batch_size', type=int, default=64, help='批大小')
    parser.add_argument('--lr', type=float, default=1e-4, help='学习率')
    parser.add_argument('--device', type=str, default='cuda', help='设备 (cuda/cpu)')
    args = parser.parse_args()
    
    # 设置设备
    device = args.device if torch.cuda.is_available() else 'cpu'
    print(f"使用设备: {device}")
    
    # 创建数据集
    print("\n加载数据集...")
    train_dataset = SleepDataset(args.data_dir, split='train')
    val_dataset = SleepDataset(args.data_dir, split='val')
    
    train_loader = DataLoader(
        train_dataset,
        batch_size=args.batch_size,
        shuffle=True,
        num_workers=4,
        collate_fn=collate_fn,
        pin_memory=True if device == 'cuda' else False
    )
    
    val_loader = DataLoader(
        val_dataset,
        batch_size=args.batch_size,
        shuffle=False,
        num_workers=4,
        collate_fn=collate_fn,
        pin_memory=True if device == 'cuda' else False
    )
    
    # 创建模型
    print("\n创建模型...")
    model = create_model(device)
    
    # 创建损失函数
    criterion = MultiTaskLoss(alpha=1.0, beta=0.5, gamma=0.3)
    
    # 创建优化器
    optimizer = optim.AdamW(
        model.parameters(),
        lr=args.lr,
        weight_decay=0.01
    )
    
    # 创建学习率调度器
    scheduler = CosineAnnealingLR(optimizer, T_max=args.epochs)
    
    # 创建训练器
    trainer = Trainer(
        model=model,
        train_loader=train_loader,
        val_loader=val_loader,
        criterion=criterion,
        optimizer=optimizer,
        scheduler=scheduler,
        device=device,
        save_dir=args.save_dir
    )
    
    # 开始训练
    trainer.train(num_epochs=args.epochs)
    
    print("\n任务完成！")


if __name__ == '__main__':
    main()
