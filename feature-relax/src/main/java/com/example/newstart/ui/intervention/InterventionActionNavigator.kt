package com.example.newstart.ui.intervention

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R

object InterventionActionNavigator {

    fun navigate(fragment: Fragment, action: InterventionActionUiModel, sheetTag: String): Boolean {
        return when {
            action.assetRef.startsWith("task://") -> {
                PrescriptionNoteBottomSheet.newInstance(action)
                    .show(fragment.childFragmentManager, sheetTag)
                true
            }

            action.assetRef.startsWith("breathing://") -> {
                fragment.findNavController().navigate(
                    R.id.navigation_breathing_coach,
                    bundleOf(
                        "protocolType" to action.protocolCode,
                        "durationSec" to action.durationSec,
                        "taskId" to ""
                    )
                )
                true
            }

            action.assetRef.startsWith("session://") || action.assetRef.startsWith("audio://") -> {
                fragment.findNavController().navigate(
                    R.id.navigation_intervention_session,
                    bundleOf(
                        "protocolCode" to action.protocolCode,
                        "protocolTitle" to action.title,
                        "itemType" to action.itemType.name,
                        "durationSec" to action.durationSec,
                        "rationale" to action.subtitle
                    )
                )
                true
            }

            action.assetRef == "screen://doctor" -> {
                fragment.findNavController().navigate(R.id.navigation_doctor)
                true
            }

            action.assetRef == "screen://medical-report" -> {
                fragment.findNavController().navigate(R.id.navigation_medical_report_analyze)
                true
            }

            else -> false
        }
    }
}

