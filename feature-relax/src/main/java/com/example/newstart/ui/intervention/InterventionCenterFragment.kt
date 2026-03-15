package com.example.newstart.ui.intervention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R
import com.example.newstart.feature.relax.databinding.FragmentInterventionCenterBinding

class InterventionCenterFragment : Fragment() {

    private var _binding: FragmentInterventionCenterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInterventionCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnInterventionCenterBack.setOnClickListener {
            if (!findNavController().navigateUp()) {
                findNavController().navigate(R.id.navigation_home)
            }
        }
        binding.cardInterventionSymptom.setOnClickListener {
            findNavController().navigate(R.id.navigation_relax_hub)
        }
        binding.cardInterventionRelax.setOnClickListener {
            findNavController().navigate(R.id.navigation_relax_center_legacy)
        }
        binding.cardInterventionBreathing.setOnClickListener {
            findNavController().navigate(R.id.navigation_breathing_coach)
        }
        binding.cardInterventionReport.setOnClickListener {
            findNavController().navigate(R.id.navigation_medical_report_analyze)
        }
        binding.cardInterventionMedication.setOnClickListener {
            findNavController().navigate(R.id.navigation_medication_analyze)
        }
        binding.cardInterventionFood.setOnClickListener {
            findNavController().navigate(R.id.navigation_food_analyze)
        }
        binding.cardInterventionReview.setOnClickListener {
            findNavController().navigate(R.id.navigation_relax_review)
        }
        binding.btnInterventionCenterPrimary.setOnClickListener {
            findNavController().navigate(R.id.navigation_relax_center_legacy)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

