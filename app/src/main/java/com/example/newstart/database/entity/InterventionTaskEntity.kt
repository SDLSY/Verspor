package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "intervention_tasks",
    indices = [
        Index(value = ["date"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class InterventionTaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val date: Long,
    val sourceType: String,
    val triggerReason: String,
    val bodyZone: String,
    val protocolType: String,
    val durationSec: Int,
    val plannedAt: Long,
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

