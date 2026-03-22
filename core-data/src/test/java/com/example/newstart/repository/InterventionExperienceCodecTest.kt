package com.example.newstart.repository

import com.example.newstart.intervention.HapticPatternMode
import com.example.newstart.intervention.InterventionExperienceMetadata
import com.example.newstart.intervention.InterventionExperienceModality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InterventionExperienceCodecTest {

    @Test
    fun toJson_and_fromJson_round_trip_preserves_metadata() {
        val metadata = InterventionExperienceMetadata(
            modality = InterventionExperienceModality.ZEN,
            sessionVariant = "night-calm",
            hapticEnabled = true,
            preferredHapticMode = HapticPatternMode.BREATH,
            avgRelaxSignal = 0.78f,
            completionQuality = 92
        )

        val json = InterventionExperienceCodec.toJson(metadata)
        val restored = InterventionExperienceCodec.fromJson(json)

        assertNotNull(json)
        assertEquals(metadata, restored)
    }

    @Test
    fun fromJson_returns_null_for_invalid_payload() {
        val restored = InterventionExperienceCodec.fromJson("{not-json")

        assertNull(restored)
    }

    @Test
    fun resolveModality_prefers_metadata_payload_over_protocol_name() {
        val json = InterventionExperienceCodec.toJson(
            InterventionExperienceMetadata(modality = InterventionExperienceModality.HAPTIC)
        )

        val modality = InterventionExperienceCodec.resolveModality("ZEN_MIST_ERASE_5M", json)

        assertEquals(InterventionExperienceModality.HAPTIC, modality)
    }

    @Test
    fun resolveModality_falls_back_to_protocol_prefixes() {
        assertEquals(
            InterventionExperienceModality.BREATH_VISUAL,
            InterventionExperienceCodec.resolveModality("BREATH_478")
        )
        assertEquals(
            InterventionExperienceModality.SOUNDSCAPE,
            InterventionExperienceCodec.resolveModality("SOUNDSCAPE_SLEEP_AUDIO_15M")
        )
        assertEquals(
            InterventionExperienceModality.GUIDED,
            InterventionExperienceCodec.resolveModality("UNKNOWN_PROTOCOL")
        )
    }
}
