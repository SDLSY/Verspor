package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "prescription_bundles",
    foreignKeys = [
        ForeignKey(
            entity = InterventionProfileSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileSnapshotId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["triggerType"]),
        Index(value = ["profileSnapshotId"]),
        Index(value = ["status"])
    ]
)
data class PrescriptionBundleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val triggerType: String,
    val profileSnapshotId: String,
    val primaryGoal: String,
    val riskLevel: String,
    val rationale: String,
    val evidenceJson: String,
    val status: String = "ACTIVE"
)
