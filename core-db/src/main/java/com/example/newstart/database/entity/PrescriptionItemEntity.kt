package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "prescription_items",
    foreignKeys = [
        ForeignKey(
            entity = PrescriptionBundleEntity::class,
            parentColumns = ["id"],
            childColumns = ["bundleId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bundleId"]),
        Index(value = ["protocolCode"]),
        Index(value = ["timingSlot"])
    ]
)
data class PrescriptionItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val bundleId: String,
    val itemType: String,
    val protocolCode: String,
    val assetRef: String,
    val durationSec: Int,
    val sequenceOrder: Int,
    val timingSlot: String,
    val isRequired: Boolean,
    val status: String = "PENDING"
)
