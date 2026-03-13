package com.example.newstart.ui.avatar

import com.example.newstart.core.common.R as CommonR

object DesktopAvatarNarrationPolicy {

    fun shouldUseSparkX(context: PageNarrationContext): Boolean {
        val hasRisk = context.riskSummary.isNotBlank()
        val hasDenseContext = context.visibleHighlights.joinToString(separator = " ").length > 160
        val isComplexPage = context.destinationId in setOf(
            CommonR.id.navigation_doctor,
            CommonR.id.navigation_trend,
            CommonR.id.navigation_medical_report_analyze
        )
        return hasRisk || hasDenseContext || isComplexPage
    }
}
