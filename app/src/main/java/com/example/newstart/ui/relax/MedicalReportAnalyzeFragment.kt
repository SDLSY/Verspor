package com.example.newstart.ui.relax

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.newstart.R
import com.example.newstart.databinding.FragmentMedicalReportAnalyzeBinding
import com.example.newstart.util.PerformanceTelemetry
import com.example.newstart.xfyun.ocr.XfyunOcrClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.min

class MedicalReportAnalyzeFragment : Fragment() {

    private var _binding: FragmentMedicalReportAnalyzeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MedicalReportAnalyzeViewModel by viewModels()
    private val ocrClient by lazy { XfyunOcrClient() }
    private var isEditorExpanded: Boolean = false
    private var isMetricsExpanded: Boolean = false

    private var ocrStartElapsedMs: Long = 0L

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            viewModel.onCaptureFailed(getString(R.string.medical_report_permission_required))
        }
    }

    private val capturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            viewModel.onCaptureFailed(getString(R.string.medical_report_ocr_empty))
            return@registerForActivityResult
        }
        recognizeText(bitmap)
    }

    private val pickDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        processSelectedDocument(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicalReportAnalyzeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        observeData()
    }

    private fun setupActions() {
        binding.btnMedicalBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.navigation_relax_hub)
            }
        }
        binding.btnMedicalCapture.setOnClickListener {
            if (hasCameraPermission()) {
                launchCameraCapture()
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        binding.btnMedicalPickFile.setOnClickListener {
            pickDocument.launch(arrayOf("application/pdf", "image/*"))
        }
        binding.btnMedicalReparse.setOnClickListener {
            viewModel.reparseDraft(binding.etMedicalOcrEditable.text?.toString().orEmpty())
        }
        binding.btnMedicalConfirm.setOnClickListener {
            viewModel.confirmDraft(binding.etMedicalOcrEditable.text?.toString().orEmpty())
        }
        binding.btnMedicalToggleMetrics.setOnClickListener {
            isMetricsExpanded = !isMetricsExpanded
            renderMetricsVisibility()
        }
        binding.btnMedicalToggleEditor.setOnClickListener {
            isEditorExpanded = !isEditorExpanded
            renderEditorVisibility()
        }
        renderMetricsVisibility()
        renderEditorVisibility()
    }

    private fun observeData() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.progressMedicalAnalyze.visibility = if (state.isAnalyzing) View.VISIBLE else View.GONE
            binding.tvMedicalStatus.text = state.statusText
            binding.tvMedicalMetrics.text = state.metricsText
            binding.tvMedicalReadable.text = state.readableReportText.ifBlank {
                getString(R.string.medical_report_readable_empty)
            }
            binding.tvMedicalOcrRaw.text = state.rawOcrText.ifBlank {
                getString(R.string.medical_report_ocr_empty)
            }
            val currentText = binding.etMedicalOcrEditable.text?.toString().orEmpty()
            if (currentText != state.editableOcrText) {
                binding.etMedicalOcrEditable.setText(state.editableOcrText)
            }
            binding.btnMedicalConfirm.isEnabled = state.canConfirm
            binding.btnMedicalReparse.isEnabled = state.editableOcrText.isNotBlank()
        }

        viewModel.toastEvent.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.consumeToast()
            }
        }
    }

    private fun renderEditorVisibility() {
        binding.layoutMedicalEditorContainer.visibility = if (isEditorExpanded) View.VISIBLE else View.GONE
        binding.btnMedicalToggleEditor.text = getString(
            if (isEditorExpanded) {
                R.string.medical_report_hide_editor
            } else {
                R.string.medical_report_show_editor
            }
        )
    }

    private fun renderMetricsVisibility() {
        binding.layoutMedicalMetricsContainer.visibility = if (isMetricsExpanded) View.VISIBLE else View.GONE
        binding.btnMedicalToggleMetrics.text = getString(
            if (isMetricsExpanded) {
                R.string.medical_report_hide_metrics
            } else {
                R.string.medical_report_show_metrics
            }
        )
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun launchCameraCapture() {
        viewModel.onCaptureStarted()
        capturePreview.launch(null)
    }

    private fun recognizeText(bitmap: Bitmap) {
        viewLifecycleOwner.lifecycleScope.launch {
            ocrStartElapsedMs = PerformanceTelemetry.nowElapsedMs()
            runCatching {
                withContext(Dispatchers.IO) { ocrClient.recognize(bitmap) }
            }.onSuccess { result ->
                PerformanceTelemetry.recordDuration(
                    metric = "ocr_latency",
                    startElapsedMs = ocrStartElapsedMs,
                    attributes = mapOf("success" to "true")
                )
                val imageTag = "camera_preview_${System.currentTimeMillis()}"
                viewModel.onOcrTextReady(result, imageTag, reportType = "PHOTO")
            }.onFailure { error ->
                PerformanceTelemetry.recordDuration(
                    metric = "ocr_latency",
                    startElapsedMs = ocrStartElapsedMs,
                    attributes = mapOf("success" to "false")
                )
                viewModel.onCaptureFailed(error.message ?: getString(R.string.medical_report_ocr_empty))
            }
        }
    }

    private fun processSelectedDocument(uri: Uri) {
        val context = context ?: return
        val contentResolver = context.contentResolver
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val mimeType = contentResolver.getType(uri).orEmpty()
        val isPdf = mimeType.equals("application/pdf", ignoreCase = true) ||
            uri.toString().lowercase().endsWith(".pdf")
        val isImage = mimeType.startsWith("image/")

        if (!isPdf && !isImage) {
            viewModel.onCaptureFailed(getString(R.string.medical_report_file_unsupported))
            return
        }

        val statusText = if (isPdf) {
            getString(R.string.medical_report_processing_pdf)
        } else {
            getString(R.string.medical_report_processing_image)
        }
        viewModel.onImportStarted(statusText)
        viewLifecycleOwner.lifecycleScope.launch {
            ocrStartElapsedMs = PerformanceTelemetry.nowElapsedMs()
            try {
                val ocrText = withContext(Dispatchers.Default) {
                    if (isPdf) extractTextFromPdf(uri) else extractTextFromImageUri(uri)
                }
                PerformanceTelemetry.recordDuration(
                    metric = "ocr_latency",
                    startElapsedMs = ocrStartElapsedMs,
                    attributes = mapOf(
                        "success" to ocrText.isNotBlank().toString(),
                        "input" to if (isPdf) "pdf" else "image_file"
                    )
                )
                if (ocrText.isBlank()) {
                    viewModel.onCaptureFailed(getString(R.string.medical_report_file_empty))
                } else {
                    val reportType = if (isPdf) "PDF" else "IMAGE_FILE"
                    viewModel.onOcrTextReady(ocrText, uri.toString(), reportType)
                }
            } catch (t: Throwable) {
                PerformanceTelemetry.recordDuration(
                    metric = "ocr_latency",
                    startElapsedMs = ocrStartElapsedMs,
                    attributes = mapOf(
                        "success" to "false",
                        "input" to if (isPdf) "pdf" else "image_file"
                    )
                )
                viewModel.onCaptureFailed(
                    t.message ?: getString(R.string.medical_report_file_open_failed)
                )
            }
        }
    }

    private suspend fun extractTextFromImageUri(uri: Uri): String {
        val bitmap = loadBitmapFromUri(uri)
        return ocrClient.recognize(bitmap)
    }

    private suspend fun extractTextFromPdf(uri: Uri): String {
        val resolver = requireContext().contentResolver
        val descriptor = resolver.openFileDescriptor(uri, "r")
            ?: throw IOException(getString(R.string.medical_report_file_open_failed))
        return descriptor.use { parcelFileDescriptor ->
            PdfRenderer(parcelFileDescriptor).use { renderer ->
                val pageTexts = mutableListOf<String>()
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val pageBitmap = renderPdfPage(page)
                        val pageText = ocrClient.recognize(pageBitmap).trim()
                        if (pageText.isNotBlank()) {
                            pageTexts += pageText
                        }
                    }
                }
                pageTexts.joinToString(separator = "\n")
            }
        }
    }

    private fun renderPdfPage(page: PdfRenderer.Page): Bitmap {
        val targetWidth = min(page.width * 2, 2200)
        val targetHeight = (page.height.toFloat() * targetWidth / page.width).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
            page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    @Suppress("DEPRECATION")
    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        val resolver = requireContext().contentResolver
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            MediaStore.Images.Media.getBitmap(resolver, uri)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
