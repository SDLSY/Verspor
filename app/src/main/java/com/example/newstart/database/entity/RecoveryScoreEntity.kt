package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

/**
 * 恢复指数实体类
 */
@Entity(
    tableName = "recovery_scores",
    foreignKeys = [
        ForeignKey(
            entity = SleepDataEntity::class,
            parentColumns = ["id"],
            childColumns = ["sleepRecordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sleepRecordId"), Index("date")]
)
data class RecoveryScoreEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val sleepRecordId: String,
    val date: Long,
    val score: Int,                         // 总分 (0-100)
    val sleepEfficiencyScore: Float,        // 睡眠效率得分
    val hrvRecoveryScore: Float,            // HRV恢复得分
    val deepSleepScore: Float,              // 深睡得分
    val temperatureRhythmScore: Float,      // 体温节律得分
    val oxygenStabilityScore: Float,        // 血氧稳定性得分
    val level: String,                      // "EXCELLENT", "GOOD", "FAIR", "POOR"
    val createdAt: Long = System.currentTimeMillis()
)
