package com.example.newstart.ui.trend

import com.example.newstart.intervention.PersonalizationLevel
import com.example.newstart.intervention.PersonalizationMissingInput
import com.example.newstart.ui.intervention.InterventionActionUiModel

enum class ReportConfidenceLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class HealthPeriodReportUiModel(
    val period: TimeRange,
    val title: String,
    val headline: String,
    val sourceLabel: String,
    val sampleHint: String,
    val riskLabel: String,
    val riskSummary: String,
    val highlightsText: String,
    val metricChangesText: String,
    val interventionSummary: String,
    val nextFocusTitle: String,
    val nextFocusDetail: String,
    val primaryAction: InterventionActionUiModel?,
    val secondaryAction: InterventionActionUiModel?,
    val personalizationLevel: PersonalizationLevel,
    val missingInputs: List<PersonalizationMissingInput>,
    val reportConfidence: ReportConfidenceLevel
)
