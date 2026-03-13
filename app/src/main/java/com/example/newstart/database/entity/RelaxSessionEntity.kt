package com.example.newstart.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "relax_sessions")
data class RelaxSessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long,
    val protocolType: String,
    val durationSec: Int,
    val preStress: Float,
    val postStress: Float,
    val preHr: Int,
    val postHr: Int,
    val preHrv: Int,
    val postHrv: Int,
    val preMotion: Float,
    val postMotion: Float,
    val effectScore: Float
)