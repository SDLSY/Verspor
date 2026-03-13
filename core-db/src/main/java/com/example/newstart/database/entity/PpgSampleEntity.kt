package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * PPG 波形采样实体（用于趋势页）
 */
@Entity(
    tableName = "ppg_samples",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["sleepRecordId"])
    ]
)
data class PpgSampleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sleepRecordId: String,
    val timestamp: Long,
    val ppgValue: Float
)
