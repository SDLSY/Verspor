package com.example.newstart.repository

import com.example.newstart.database.entity.InterventionExecutionEntity
import com.example.newstart.database.entity.RelaxSessionEntity
import com.example.newstart.intervention.InterventionExperienceMetadata
import com.example.newstart.intervention.InterventionExperienceModality
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object InterventionExperienceCodec {

    private val gson = Gson()
    private val metadataType = object : TypeToken<InterventionExperienceMetadata>() {}.type

    fun toJson(metadata: InterventionExperienceMetadata?): String? {
        return metadata?.let { gson.toJson(it) }
    }

    fun fromJson(raw: String?): InterventionExperienceMetadata? {
        if (raw.isNullOrBlank()) return null
        return runCatching { gson.fromJson<InterventionExperienceMetadata>(raw, metadataType) }.getOrNull()
    }

    fun resolveModality(protocolType: String, metadataJson: String? = null): InterventionExperienceModality {
        val metadata = fromJson(metadataJson)
        if (metadata != null) {
            return metadata.modality
        }
        return when {
            protocolType.startsWith("ZEN_", ignoreCase = true) -> InterventionExperienceModality.ZEN
            protocolType.startsWith("BREATH", ignoreCase = true) -> InterventionExperienceModality.BREATH_VISUAL
            protocolType.startsWith("TASK_", ignoreCase = true) -> InterventionExperienceModality.TASK
            protocolType.contains("SOUNDSCAPE", ignoreCase = true) -> InterventionExperienceModality.SOUNDSCAPE
            protocolType.contains("AUDIO", ignoreCase = true) -> InterventionExperienceModality.AUDIO
            else -> InterventionExperienceModality.GUIDED
        }
    }

    fun resolveModality(protocolType: String, session: RelaxSessionEntity): InterventionExperienceModality {
        return resolveModality(protocolType, session.metadataJson)
    }

    fun resolveModality(protocolType: String, execution: InterventionExecutionEntity): InterventionExperienceModality {
        return resolveModality(protocolType, execution.metadataJson)
    }
}
