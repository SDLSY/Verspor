package com.example.newstart.ui.intervention

import com.example.newstart.intervention.PrescriptionItemType

data class InterventionActionUiModel(
    val title: String,
    val subtitle: String,
    val protocolCode: String,
    val durationSec: Int,
    val assetRef: String,
    val itemType: PrescriptionItemType
)
