package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "intervention_executions",
    foreignKeys = [
        ForeignKey(
            entity = InterventionTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["startedAt"])
    ]
)
data class InterventionExecutionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val startedAt: Long,
    val endedAt: Long,
    val elapsedSec: Int,
    val beforeStress: Float,
    val afterStress: Float,
    val beforeHr: Int,
    val afterHr: Int,
    val effectScore: Float,
    val completionType: String
)

