package com.example.newstart.ui.relax

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.example.newstart.R
import com.example.newstart.databinding.BottomSheetSymptomDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class SymptomDetailBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val RESULT_KEY = "symptom_detail_result"
        private const val ARG_ZONE = "zone"
        private const val ARG_SIDE = "side"

        fun newInstance(
            zone: SymptomBodyZone,
            side: SurfaceSide
        ): SymptomDetailBottomSheet {
            return SymptomDetailBottomSheet().apply {
                arguments = bundleOf(
                    ARG_ZONE to zone.name,
                    ARG_SIDE to side.name
                )
            }
        }
    }

    private var _binding: BottomSheetSymptomDetailBinding? = null
    private val binding get() = _binding!!

    private val zone: SymptomBodyZone
        get() = SymptomBodyZone.valueOf(requireArguments().getString(ARG_ZONE).orEmpty())

    private val side: SurfaceSide
        get() = SurfaceSide.valueOf(requireArguments().getString(ARG_SIDE).orEmpty())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSymptomDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvSymptomSheetTitle.text = getString(
            R.string.symptom_sheet_title_format,
            sideLabel(side),
            zoneLabel(zone)
        )
        binding.tvSymptomSheetSubtitle.text = getString(R.string.symptom_sheet_subtitle)
        binding.sliderSeverity.value = 5f
        binding.tvSeverityValue.text = getString(R.string.symptom_sheet_severity_value, 5)
        binding.chipDurationRecentDay.isChecked = true
        populateSymptomChips()
        binding.sliderSeverity.addOnChangeListener { _, value, _ ->
            binding.tvSeverityValue.text =
                getString(R.string.symptom_sheet_severity_value, value.toInt())
        }
        binding.btnSymptomSheetSave.setOnClickListener {
            saveSelection()
        }
    }

    private fun populateSymptomChips() {
        binding.chipGroupSheetSymptoms.removeAllViews()
        symptomsFor(zone).forEach { label ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isClickable = true
                isCheckedIconVisible = false
                setEnsureMinTouchTargetSize(false)
            }
            binding.chipGroupSheetSymptoms.addView(chip)
        }
    }

    private fun saveSelection() {
        val selectedChipId = binding.chipGroupSheetSymptoms.checkedChipId
        val selectedChip = if (selectedChipId == View.NO_ID) {
            null
        } else {
            binding.chipGroupSheetSymptoms.findViewById<Chip>(selectedChipId)
        }
        val customNote = binding.etSymptomOther.text?.toString().orEmpty().trim()
        val symptomLabel = selectedChip?.text?.toString().orEmpty().ifBlank { customNote }
        if (symptomLabel.isBlank()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.symptom_sheet_pick_required),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val durationLabel = when (binding.chipGroupSheetDuration.checkedChipId) {
            R.id.chip_duration_recent_hour -> getString(R.string.symptom_duration_recent_hour)
            R.id.chip_duration_recent_three_days -> getString(R.string.symptom_duration_recent_three_days)
            else -> getString(R.string.symptom_duration_recent_day)
        }
        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                ARG_ZONE to zone.name,
                ARG_SIDE to side.name,
                "symptomLabel" to symptomLabel,
                "severity" to binding.sliderSeverity.value.toInt(),
                "durationLabel" to durationLabel,
                "note" to customNote
            )
        )
        dismiss()
    }

    private fun symptomsFor(zone: SymptomBodyZone): List<String> {
        return when (zone) {
            SymptomBodyZone.HEAD -> listOf("头痛", "太阳穴痛", "出汗")
            SymptomBodyZone.CHEST -> listOf("胸闷", "出汗")
            SymptomBodyZone.ABDOMEN -> listOf("腹痛", "出汗")
            SymptomBodyZone.LIMB -> listOf("手腕痛", "膝盖痛", "出汗")
        }
    }

    private fun zoneLabel(zone: SymptomBodyZone): String {
        return when (zone) {
            SymptomBodyZone.HEAD -> getString(R.string.symptom_zone_head)
            SymptomBodyZone.CHEST -> getString(R.string.symptom_zone_chest)
            SymptomBodyZone.ABDOMEN -> getString(R.string.symptom_zone_abdomen)
            SymptomBodyZone.LIMB -> getString(R.string.symptom_zone_limb)
        }
    }

    private fun sideLabel(side: SurfaceSide): String {
        return when (side) {
            SurfaceSide.FRONT -> getString(R.string.symptom_surface_front)
            SurfaceSide.BACK -> getString(R.string.symptom_surface_back)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
