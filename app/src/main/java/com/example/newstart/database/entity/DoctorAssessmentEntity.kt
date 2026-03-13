package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "doctor_assessments",
    foreignKeys = [
        ForeignKey(
            entity = DoctorSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["createdAt"])
    ]
)
data class DoctorAssessmentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val suspectedIssuesJson: String,
    val symptomFactsJson: String,
    val missingInfoJson: String,
    val redFlagsJson: String,
    val recommendedDepartment: String,
    val doctorSummary: String,
    val nextStepAdviceJson: String,
    val disclaimer: String
)
