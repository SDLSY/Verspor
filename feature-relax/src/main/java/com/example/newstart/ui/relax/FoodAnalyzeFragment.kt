package com.example.newstart.ui.relax

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.newstart.core.common.R
import com.example.newstart.feature.relax.databinding.FragmentFoodAnalyzeBinding

class FoodAnalyzeFragment : Fragment() {

    private var _binding: FragmentFoodAnalyzeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FoodAnalyzeViewModel by viewModels()

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            capturePreview.launch(null)
        } else {
            Toast.makeText(requireContext(), getString(R.string.food_analyze_camera_required), Toast.LENGTH_SHORT).show()
        }
    }

    private val capturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            return@registerForActivityResult
        }
        val asset = ImageImportHelper.saveBitmapToCache(requireContext(), bitmap, "food")
        viewModel.prepareImage(asset.file.absolutePath, asset.uriString, asset.mimeType)
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        val asset = ImageImportHelper.copyUriToCache(requireContext(), uri, "food")
        viewModel.prepareImage(asset.file.absolutePath, asset.uriString, asset.mimeType)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoodAnalyzeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        observeData()
    }

    private fun setupActions() {
        binding.btnFoodBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.navigation_intervention_center)
            }
        }
        binding.btnFoodCapture.setOnClickListener {
            if (hasCameraPermission()) {
                capturePreview.launch(null)
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        binding.btnFoodPickFile.setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }
        binding.btnFoodCloudAnalyze.setOnClickListener {
            viewModel.analyzeSelectedImage()
        }
        binding.btnFoodSave.setOnClickListener {
            viewModel.saveRecord(
                mealType = binding.etFoodMealType.text?.toString().orEmpty(),
                foodItemsText = binding.etFoodItems.text?.toString().orEmpty(),
                estimatedCaloriesText = binding.etFoodCalories.text?.toString().orEmpty(),
                carbohydrateText = binding.etFoodCarbohydrate.text?.toString().orEmpty(),
                proteinText = binding.etFoodProtein.text?.toString().orEmpty(),
                fatText = binding.etFoodFat.text?.toString().orEmpty(),
                nutritionRiskLevel = binding.etFoodRiskLevel.text?.toString().orEmpty(),
                nutritionFlagsText = binding.etFoodFlags.text?.toString().orEmpty(),
                dailyContribution = binding.etFoodContribution.text?.toString().orEmpty(),
                advice = binding.etFoodAdvice.text?.toString().orEmpty()
            )
        }
    }

    private fun observeData() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.progressFoodAnalyze.visibility = if (state.isAnalyzing) View.VISIBLE else View.GONE
            binding.tvFoodStatus.text = state.statusText
            binding.btnFoodCloudAnalyze.isEnabled = state.hasSelectedImage
            if (state.previewImageUri.isNotBlank()) {
                binding.ivFoodPreview.setImageURI(Uri.parse(state.previewImageUri))
            }
            syncText(binding.etFoodMealType, state.mealType)
            syncText(binding.etFoodItems, state.foodItemsText)
            syncText(binding.etFoodCalories, state.estimatedCaloriesText)
            syncText(binding.etFoodCarbohydrate, state.carbohydrateText)
            syncText(binding.etFoodProtein, state.proteinText)
            syncText(binding.etFoodFat, state.fatText)
            syncText(binding.etFoodRiskLevel, state.nutritionRiskLevel)
            syncText(binding.etFoodFlags, state.nutritionFlagsText)
            syncText(binding.etFoodContribution, state.dailyContribution)
            syncText(binding.etFoodAdvice, state.advice)
            binding.tvFoodResultNote.text = buildString {
                append(getString(R.string.food_analyze_confidence_label, (state.confidence * 100).toInt()))
                if (state.requiresManualReview) {
                    append("\n")
                    append(getString(R.string.food_analyze_manual_review_required))
                }
            }
            binding.tvFoodResultNote.visibility =
                if (binding.tvFoodResultNote.text.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        viewModel.toastEvent.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.consumeToast()
            }
        }
    }

    private fun syncText(view: EditText, target: String) {
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
