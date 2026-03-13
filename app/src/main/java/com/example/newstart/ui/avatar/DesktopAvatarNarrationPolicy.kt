package com.example.newstart.ui.avatar

object DesktopAvatarNarrationPolicy {

    fun shouldUseSparkX(context: PageNarrationContext): Boolean {
        val hasRisk = context.riskSummary.isNotBlank()
        val hasDenseContext = context.visibleHighlights.joinToString(separator = " ").length > 160
        val isComplexPage = context.destinationId in setOf(
            com.example.newstart.R.id.navigation_doctor,
            com.example.newstart.R.id.navigation_trend,
            com.example.newstart.R.id.navigation_medical_report_analyze
        )
        return hasRisk || hasDenseContext || isComplexPage
    }
}
