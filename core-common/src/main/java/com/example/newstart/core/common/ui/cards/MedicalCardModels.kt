package com.example.newstart.core.common.ui.cards

data class EvidenceCardModel(
    val title: String,
    val value: String,
    val note: String = "",
    val badgeText: String = "",
    val tone: CardTone = CardTone.NEUTRAL
)

data class MetricRangeCardModel(
    val metricName: String,
    val valueText: String,
    val rangeText: String,
    val statusText: String,
    val helperText: String = "",
    val progressPercent: Int = 50,
    val tone: CardTone = CardTone.NEUTRAL
)

data class MiniTrendCardModel(
    val title: String,
    val valueText: String,
    val trendText: String,
    val supportingText: String = "",
    val progressPercent: Int = 50,
    val tone: CardTone = CardTone.NEUTRAL
)

data class RiskSummaryCardModel(
    val badgeText: String,
    val title: String,
    val summary: String,
    val supportingText: String = "",
    val bullets: List<String> = emptyList(),
    val tone: CardTone = CardTone.NEUTRAL
)

data class ActionGroupCardModel(
    val category: String,
    val headline: String,
    val supportingText: String = "",
    val detailLines: List<String> = emptyList(),
    val actionLabel: String = "",
    val actionId: String = "",
    val enabled: Boolean = true,
    val tone: CardTone = CardTone.NEUTRAL
)

data class BodySelectionCardModel(
    val title: String,
    val subtitle: String,
    val selectedCount: Int,
    val highlightedZones: List<String> = emptyList()
)

enum class CardTone {
    NEUTRAL,
    POSITIVE,
    WARNING,
    NEGATIVE,
    INFO
}
