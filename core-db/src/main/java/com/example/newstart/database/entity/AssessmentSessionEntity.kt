package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "assessment_sessions",
    indices = [
        Index(value = ["scaleCode"]),
        Index(value = ["completedAt"]),
        Index(value = ["freshnessUntil"])
    ]
)
data class AssessmentSessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val scaleCode: String,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val totalScore: Int = -1,
    val severityLevel: String = "PENDING",
    val freshnessUntil: Long = 0L,
    val source: String = "BASELINE"
)
