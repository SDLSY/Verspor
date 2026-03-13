package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 健康指标实体
 */
@Entity(
    tableName = "health_metrics"
)
data class HealthMetricsEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val sleepRecordId: String,
    val timestamp: Long,

    // 原始采样（用于趋势页）
    val heartRateSample: Int,
    val bloodOxygenSample: Int,
    val temperatureSample: Float,
    val stepsSample: Int = 0,
    val accMagnitudeSample: Float = 0f,

    // 心率
    val heartRateCurrent: Int,
    val heartRateAvg: Int,
    val heartRateMin: Int,
    val heartRateMax: Int,
    val heartRateTrend: String,

    // 血氧
    val bloodOxygenCurrent: Int,
    val bloodOxygenAvg: Int,
    val bloodOxygenMin: Int,
    val bloodOxygenStability: String,

    // 体温
    val temperatureCurrent: Float,
    val temperatureAvg: Float,
    val temperatureStatus: String,

    // HRV
    val hrvCurrent: Int,
    val hrvBaseline: Int,
    val hrvRecoveryRate: Float,
    val hrvTrend: String
)
