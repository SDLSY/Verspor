package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "intervention_profile_snapshots",
    indices = [
        Index(value = ["generatedAt"]),
        Index(value = ["triggerType"])
    ]
)
data class InterventionProfileSnapshotEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val generatedAt: Long = System.currentTimeMillis(),
    val triggerType: String,
    val domainScoresJson: String,
    val evidenceFactsJson: String,
    val redFlagsJson: String
)
