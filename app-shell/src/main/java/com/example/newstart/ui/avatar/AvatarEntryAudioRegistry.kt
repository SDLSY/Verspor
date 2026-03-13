package com.example.newstart.ui.avatar

import android.content.Context
import com.example.newstart.core.common.R as CommonR

object AvatarEntryAudioRegistry {

    fun audioResIdForPageKey(pageKey: String): Int? {
        return when (pageKey) {
            "home" -> CommonR.raw.avatar_entry_home
            "doctor" -> CommonR.raw.avatar_entry_doctor
            "trend" -> CommonR.raw.avatar_entry_trend
            "device" -> CommonR.raw.avatar_entry_device
            "profile" -> CommonR.raw.avatar_entry_profile
            "intervention_center" -> CommonR.raw.avatar_entry_intervention_center
            "symptom_guide" -> CommonR.raw.avatar_entry_symptom_guide
            "relax_center" -> CommonR.raw.avatar_entry_relax_center
            "breathing_coach" -> CommonR.raw.avatar_entry_breathing_coach
            "medical_report" -> CommonR.raw.avatar_entry_medical_report
            "relax_review" -> CommonR.raw.avatar_entry_relax_review
            "assessment_baseline" -> CommonR.raw.avatar_entry_assessment_baseline
            "intervention_profile" -> CommonR.raw.avatar_entry_intervention_profile
            "intervention_session" -> CommonR.raw.avatar_entry_intervention_session
            else -> null
        }
    }

    fun hasBundledAudio(pageKey: String): Boolean = audioResIdForPageKey(pageKey) != null

    fun audioSource(context: Context, pageKey: String): String? {
        val resId = audioResIdForPageKey(pageKey) ?: return null
        return "android.resource://${context.packageName}/$resId"
    }
}
