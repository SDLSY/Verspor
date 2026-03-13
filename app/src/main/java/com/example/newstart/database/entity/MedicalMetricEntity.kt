package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "medical_metrics",
    foreignKeys = [
        ForeignKey(
            entity = MedicalReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["reportId"]),
        Index(value = ["metricCode"]),
        Index(value = ["isAbnormal"])
    ]
)
data class MedicalMetricEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val reportId: String,
    val metricCode: String,
    val metricName: String,
    val metricValue: Float,
    val unit: String,
    val refLow: Float?,
    val refHigh: Float?,
    val isAbnormal: Boolean,
    val confidence: Float
)

