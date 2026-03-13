package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "medical_reports",
    indices = [
        Index(value = ["reportDate"]),
        Index(value = ["parseStatus"]),
        Index(value = ["riskLevel"])
    ]
)
data class MedicalReportEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val reportDate: Long,
    val reportType: String,
    val imageUri: String,
    val ocrTextDigest: String,
    val parseStatus: String,
    val riskLevel: String,
    val createdAt: Long = System.currentTimeMillis()
)

