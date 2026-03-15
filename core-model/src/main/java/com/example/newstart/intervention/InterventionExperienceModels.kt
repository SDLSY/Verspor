package com.example.newstart.intervention

data class RelaxRealtimeFeedback(
    val signalState: RelaxSignalState = RelaxSignalState.FALLBACK,
    val relaxSignal: Float = 0.5f,
    val heartRate: Int = 0,
    val hrv: Int = 0,
    val motion: Float = 0f,
    val updatedAt: Long = 0L,
    val hasRealtimeData: Boolean = false,
    val summary: String = ""
)

enum class RelaxSignalState {
    CALM,
    STEADY,
    ACTIVE,
    FALLBACK
}

enum class HapticPatternMode {
    BREATH,
    CALM_HEARTBEAT;

    companion object {
        fun fromStorageValue(value: String?): HapticPatternMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: BREATH
        }
    }
}

enum class ZenInteractionMode {
    MIST_ERASE,
    WAVE_GARDEN;

    companion object {
        fun fromProtocol(protocolCode: String): ZenInteractionMode {
            return if (protocolCode.equals("ZEN_MIST_ERASE_5M", ignoreCase = true)) {
                MIST_ERASE
            } else {
                WAVE_GARDEN
            }
        }
    }
}

enum class InterventionExperienceModality {
    BREATH_VISUAL,
    HAPTIC,
    ZEN,
    SOUNDSCAPE,
    GUIDED,
    AUDIO,
    TASK
}

data class AdaptiveSoundscapeState(
    val mixLevels: Map<String, Float> = emptyMap(),
    val adaptiveEnabled: Boolean = true,
    val manualAdjustCount: Int = 0,
    val dominantLayerLabel: String = ""
)

data class InterventionExperienceMetadata(
    val modality: InterventionExperienceModality,
    val sessionVariant: String = "",
    val hapticEnabled: Boolean = false,
    val preferredHapticMode: HapticPatternMode? = null,
    val realtimeSignalAvailable: Boolean = false,
    val avgRelaxSignal: Float? = null,
    val peakHeartRate: Int? = null,
    val averageHrv: Int? = null,
    val soundscapeMix: Map<String, Float> = emptyMap(),
    val manualAdjustCount: Int = 0,
    val completionQuality: Int = 0,
    val fallbackMode: String? = null,
    val interactionTouches: Int = 0
)
