package com.example.newstart.ui.avatar

data class AvatarNarration(
    val text: String,
    val audioDataUrl: String,
    val source: String,
    val modelLabel: String,
    val semanticAction: String = ""
)
