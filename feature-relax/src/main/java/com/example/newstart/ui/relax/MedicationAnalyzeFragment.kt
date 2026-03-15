package com.example.newstart.ui.relax

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R
import com.example.newstart.feature.relax.databinding.FragmentMedicationAnalyzeBinding

class MedicationAnalyzeFragment : Fragment() {

    private var _binding: FragmentMedicationAnalyzeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MedicationAnalyzeViewModel by viewModels()

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            capturePreview.launch(null)
        } else {
            Toast.makeText(requireContext(), getString(R.string.medication_analyze_camera_required), Toast.LENGTH_SHORT).show()
        }
    }

    private val capturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            return@registerForActivityResult
        }
        val asset = ImageImportHelper.saveBitmapToCache(requireContext(), bitmap, "medication")
        viewModel.prepareImage(asset.file.absolutePath, asset.uriString, asset.mimeType)
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        val asset = ImageImportHelper.copyUriToCache(requireContext(), uri, "medication")
        viewModel.prepareImage(asset.file.absolutePath, asset.uriString, asset.mimeType)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicationAnalyzeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        observeData()
    }

    private fun setupActions() {
        binding.btnMedicationBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.navigation_intervention_center)
            }
        }
        binding.btnMedicationCapture.setOnClickListener {
            if (hasCameraPermission()) {
                capturePreview.launch(null)
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        binding.btnMedicationPickFile.setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }
        binding.btnMedicationCloudAnalyze.setOnClickListener {
            viewModel.analyzeSelectedImage()
        }
        binding.btnMedicationSave.setOnClickListener {
            viewModel.saveRecord(
                recognizedName = binding.etMedicationName.text?.toString().orEmpty(),
                dosageForm = binding.etMedicationForm.text?.toString().orEmpty(),
                specification = binding.etMedicationSpecification.text?.toString().orEmpty(),
                activeIngredientsText = binding.etMedicationIngredients.text?.toString().orEmpty(),
                matchedSymptomsText = binding.etMedicationSymptoms.text?.toString().orEmpty(),
                usageSummary = binding.etMedicationUsageSummary.text?.toString().orEmpty(),
                riskLevel = binding.etMedicationRiskLevel.text?.toString().orEmpty(),
                riskFlagsText = binding.etMedicationRiskFlags.text?.toString().orEmpty(),
                advice = binding.etMedicationAdvice.text?.toString().orEmpty()
            )
        }
    }

    private fun observeData() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.progressMedicationAnalyze.visibility = if (state.isAnalyzing) View.VISIBLE else View.GONE
            binding.tvMedicationStatus.text = state.statusText
            binding.btnMedicationCloudAnalyze.isEnabled = state.hasSelectedImage
            if (state.previewImageUri.isNotBlank()) {
                binding.ivMedicationPreview.setImageURI(Uri.parse(state.previewImageUri))
            }
            syncText(binding.etMedicationName, state.recognizedName)
            syncText(binding.etMedicationForm, state.dosageForm)
            syncText(binding.etMedicationSpecification, state.specification)
            syncText(binding.etMedicationIngredients, state.activeIngredientsText)
            syncText(binding.etMedicationSymptoms, state.matchedSymptomsText)
            syncText(binding.etMedicationUsageSummary, state.usageSummary)
            syncText(binding.etMedicationRiskLevel, state.riskLevel)
            syncText(binding.etMedicationRiskFlags, state.riskFlagsText)
            syncText(binding.etMedicationAdvice, state.advice)
            binding.tvMedicationEvidence.text = buildString {
                append(getString(R.string.medication_analyze_confidence_label, (state.confidence * 100).toInt()))
                if (state.requiresManualReview) {
                    append("\n")
                    append(getString(R.string.medication_analyze_manual_review_required))
                }
                if (state.evidenceText.isNotBlank()) {
                    append("\n")
                    append(state.evidenceText)
                }
            }
            binding.tvMedicationMetadata.text = state.metadataText
        }

        viewModel.toastEvent.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.consumeToast()
            }
        }
    }

    private fun syncText(view: android.widget.EditText, target: String) {
        if (view.text?.toString().orEmpty() != target) {
            view.setText(target)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
