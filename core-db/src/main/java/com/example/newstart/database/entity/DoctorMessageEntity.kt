package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "doctor_messages",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["sessionId", "timestamp"])
    ]
)
data class DoctorMessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String = "",
    val role: String,
    val messageType: String = "TEXT",
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payloadJson: String? = null,
    val actionProtocolType: String? = null,
    val actionDurationSec: Int? = null
)
