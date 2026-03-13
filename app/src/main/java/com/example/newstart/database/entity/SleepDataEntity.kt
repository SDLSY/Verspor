package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.newstart.database.Converters
import java.util.*

/**
 * 睡眠数据实体类
 */
@Entity(tableName = "sleep_records")
@TypeConverters(Converters::class)
data class SleepDataEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val date: Long,                     // 日期时间戳
    val bedTime: Long,                  // 上床时间
    val wakeTime: Long,                 // 起床时间
    val totalSleepMinutes: Int,         // 总睡眠时长（分钟）
    val deepSleepMinutes: Int,          // 深睡时长
    val lightSleepMinutes: Int,         // 浅睡时长
    val remSleepMinutes: Int,           // REM时长
    val awakeMinutes: Int,              // 清醒时长
    val sleepEfficiency: Float,         // 睡眠效率 (0-100)
    val fallAsleepMinutes: Int,         // 入睡时长
    val awakeCount: Int,                // 夜间觉醒次数
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
