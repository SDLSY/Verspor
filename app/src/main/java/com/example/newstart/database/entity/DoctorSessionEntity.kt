package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "doctor_sessions",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["status"])
    ]
)
data class DoctorSessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String,
    val domain: String,
    val chiefComplaint: String,
    val riskLevel: String
)
