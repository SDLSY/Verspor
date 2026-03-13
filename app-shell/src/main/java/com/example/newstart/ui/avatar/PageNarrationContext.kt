package com.example.newstart.ui.avatar

data class PageNarrationContext(
    val destinationId: Int,
    val pageKey: String,
    val pageTitle: String,
    val pageSubtitle: String = "",
    val visibleHighlights: List<String> = emptyList(),
    val userStateSummary: String = "",
    val riskSummary: String = "",
    val actionHint: String = "",
    val trigger: String = "enter"
)
