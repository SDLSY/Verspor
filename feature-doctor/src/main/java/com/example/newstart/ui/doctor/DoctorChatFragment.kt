package com.example.newstart.ui.doctor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newstart.core.common.R
import com.example.newstart.feature.doctor.databinding.FragmentDoctorChatBinding
import com.example.newstart.service.ai.MediaGenerationService
import com.example.newstart.service.ai.SpeechService
import com.example.newstart.ui.avatar.AvatarAudioHost
import com.example.newstart.ui.avatar.AvatarController
import com.example.newstart.ui.doctor.DOCTOR_PREFILL_MESSAGE_KEY
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.xfyun.XfyunConfig
import com.example.newstart.xfyun.speech.XfyunIatWsClient
import com.example.newstart.xfyun.speech.XfyunTtsWsClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class DoctorChatFragment : Fragment() {

    companion object {
        private const val AUTO_RESUME_DELAY_MS = 1_100L
        private const val SPEECH_RESUME_COOLDOWN_MS = 1_200L
        private const val TAG = "DoctorChatFragment"
    }

    private enum class VoiceCallState {
        IDLE,
        CALL_READY,
        RECORDING,
        TRANSCRIBING,
        WAITING_REPLY,
        SPEAKING,
        COOLDOWN
    }

    private var _binding: FragmentDoctorChatBinding? = null
    private val binding get() = _binding!!
    private val bindingOrNull get() = _binding

    private val viewModel: DoctorChatViewModel by viewModels()
    private val adapter = DoctorChatAdapter { action ->
        viewModel.onMessageAction(action)
    }

    private val networkRepository = NetworkRepository()
    private val speechService = SpeechService(networkRepository)
    private val mediaGenerationService = MediaGenerationService(networkRepository)
    private val xfyunIatClient = XfyunIatWsClient()
    private val xfyunTtsClient = XfyunTtsWsClient()

    private var isRecording = false
    private var isVoiceCallModeActive = false
    private var voiceCallState = VoiceCallState.IDLE
    private var lastAutoSpokenMessageId: String? = null
    private var currentImageUrl: String? = null
    private var currentVideoJobId: String? = null
    private var currentVideoUrl: String? = null
    private var recordPermissionDenied = false
    private var isSupportPanelExpanded = false
    private var pendingAssistantPlayback = false
    private var autoResumeJob: Job? = null
    private var voiceRecognitionJob: Job? = null
    private var suppressListeningUntilMs: Long = 0L

    private val requestRecordAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                recordPermissionDenied = false
                startVoiceRecording()
            } else {
                recordPermissionDenied = true
                setVoiceCallState(if (isVoiceCallModeActive) VoiceCallState.CALL_READY else VoiceCallState.IDLE)
                Toast.makeText(requireContext(), getString(R.string.doctor_voice_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDoctorChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupList()
        setupActions()
        consumePrefillMessage()
        observeAvatarAudio()
        observeViewModel()
        seedMultimodalDefaults()
    }

    private fun setupList() {
        binding.rvDoctorChat.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvDoctorChat.adapter = adapter
    }

    private fun setupActions() {
        binding.btnDoctorSend.setOnClickListener {
            sendCurrentInput()
        }
        binding.etDoctorInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendCurrentInput()
                true
            } else {
                false
            }
        }
        binding.btnDoctorRec1Start.setOnClickListener { viewModel.onRecommendationStart(0) }
        binding.btnDoctorRec2Start.setOnClickListener { viewModel.onRecommendationStart(1) }
        binding.btnDoctorAddInfo.setOnClickListener {
            binding.etDoctorInput.requestFocus()
            if (binding.etDoctorInput.text.isNullOrBlank()) {
                binding.etDoctorInput.setText(getString(R.string.doctor_add_info) + ":")
                binding.etDoctorInput.setSelection(binding.etDoctorInput.text?.length ?: 0)
            }
        }
        binding.btnDoctorGenerateAssessment.setOnClickListener { viewModel.generateAssessmentNow() }
        binding.btnDoctorRestart.setOnClickListener { viewModel.restartConversation() }
        binding.btnDoctorHistory.setOnClickListener { showHistoryDialog() }
        binding.btnDoctorOpenInterventionCenter.setOnClickListener {
            findNavController().navigate(R.id.navigation_intervention_center)
        }
        binding.btnDoctorOpenLiveAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), DoctorLiveAvatarActivity::class.java))
        }
        binding.btnDoctorSupportToggle.setOnClickListener {
            isSupportPanelExpanded = !isSupportPanelExpanded
            renderSupportPanel()
        }
        binding.chipDoctorQSleep.setOnClickListener {
            viewModel.onQuickQuestionAsked(getString(R.string.doctor_quick_question_sleep_text))
        }
        binding.chipDoctorQStress.setOnClickListener {
            viewModel.onQuickQuestionAsked(getString(R.string.doctor_quick_question_stress_text))
        }
        binding.chipDoctorQTraining.setOnClickListener {
            viewModel.onQuickQuestionAsked(getString(R.string.doctor_quick_question_training_text))
        }

        binding.btnDoctorVoiceToggle.setOnClickListener {
            toggleVoiceRecording()
        }
        binding.btnDoctorVoiceCallMode.setOnClickListener {
            toggleVoiceCallMode()
        }

        binding.btnDoctorMultimodalToggle.setOnClickListener {
            val expanded = !binding.layoutDoctorMultimodalContent.isVisible
            binding.layoutDoctorMultimodalContent.isVisible = expanded
            binding.btnDoctorMultimodalToggle.text = if (expanded) {
                getString(R.string.doctor_multimodal_toggle_close)
            } else {
                getString(R.string.doctor_multimodal_toggle_open)
            }
        }

        binding.chipDoctorImageExampleCover.setOnClickListener {
            binding.etDoctorImagePrompt.setText(getString(R.string.doctor_image_example_cover))
        }
        binding.chipDoctorImageExampleEducation.setOnClickListener {
            binding.etDoctorImagePrompt.setText(getString(R.string.doctor_image_example_education))
        }
        binding.chipDoctorImageExampleUi.setOnClickListener {
            binding.etDoctorImagePrompt.setText(getString(R.string.doctor_image_example_ui))
        }
        binding.btnDoctorGenerateImage.setOnClickListener {
            generateDoctorImage()
        }
        binding.btnDoctorOpenImage.setOnClickListener {
            currentImageUrl?.let(::openExternalUrl)
        }

        binding.chipDoctorVideoExampleSleep.setOnClickListener {
            binding.etDoctorVideoPrompt.setText(getString(R.string.doctor_video_example_sleep))
        }
        binding.chipDoctorVideoExampleRecovery.setOnClickListener {
            binding.etDoctorVideoPrompt.setText(getString(R.string.doctor_video_example_recovery))
        }
        binding.chipDoctorVideoExampleStory.setOnClickListener {
            binding.etDoctorVideoPrompt.setText(getString(R.string.doctor_video_example_story))
        }
        binding.btnDoctorGenerateVideo.setOnClickListener {
            generateDoctorVideo()
        }
        binding.btnDoctorPollVideo.setOnClickListener {
            pollDoctorVideo()
        }
        binding.btnDoctorOpenVideo.setOnClickListener {
            currentVideoUrl?.let(::openExternalUrl)
        }
    }

    private fun observeAvatarAudio() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AvatarController.isAudioPlaying.collectLatest { isPlaying ->
                    val currentBinding = bindingOrNull ?: return@collectLatest
                    if (isPlaying) {
                        autoResumeJob?.cancel()
                        pendingAssistantPlayback = false
                        setVoiceCallState(VoiceCallState.SPEAKING)
                    } else if (!isRecording && viewModel.uiState.value?.isSending != true && !pendingAssistantPlayback) {
                        if (isVoiceCallModeActive) {
                            suppressListeningUntilMs = SystemClock.elapsedRealtime() + SPEECH_RESUME_COOLDOWN_MS
                            setVoiceCallState(VoiceCallState.COOLDOWN)
                            resumeContinuousListening(SPEECH_RESUME_COOLDOWN_MS)
                        } else if (currentBinding.tvDoctorVoiceStatus.text == getString(R.string.doctor_voice_status_speaking)) {
                            setVoiceCallState(VoiceCallState.IDLE)
                        }
                    }
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            val currentBinding = bindingOrNull ?: return@observe
            val hasUserMessage = state.messages.any { it.role == DoctorRole.USER }
            val hasRecommendations = state.recommendedInterventions.isNotEmpty()
            val canContinueInquiry = state.currentStage != DoctorInquiryStage.COMPLETED &&
                state.currentStage != DoctorInquiryStage.ESCALATED

            currentBinding.tvDoctorRiskLevel.text = state.riskLevelText
            currentBinding.tvDoctorRiskScore.text = getString(R.string.doctor_risk_score_format, state.riskScore)
            currentBinding.tvDoctorDataFreshness.text = state.dataFreshnessText
            currentBinding.tvDoctorSuggestionSource.text = state.suggestionSourceText
            currentBinding.tvDoctorConfidence.text = state.confidenceText
            currentBinding.tvDoctorModelStatus.text = state.modelStatusText
            currentBinding.cardDoctorExplanation.isVisible = state.recommendationExplanation.visible
            currentBinding.tvDoctorExplanationSummary.text = state.recommendationExplanation.summary
            currentBinding.tvDoctorExplanationMeta.text = state.recommendationExplanation.metaLabel
            currentBinding.tvDoctorExplanationReasons.text = if (state.recommendationExplanation.reasons.isEmpty()) {
                getString(R.string.doctor_explanation_reasons_empty)
            } else {
                state.recommendationExplanation.reasons.joinToString("\n") { "- $it" }
            }
            currentBinding.tvDoctorExplanationNextStep.text = if (state.recommendationExplanation.nextStep.isBlank()) {
                getString(R.string.doctor_explanation_next_step_empty)
            } else {
                getString(R.string.doctor_explanation_next_step_format, state.recommendationExplanation.nextStep)
            }

            currentBinding.cardDoctorRecommendations.isVisible = hasRecommendations
            currentBinding.layoutDoctorControls.isVisible = true
            currentBinding.rvDoctorChat.isVisible = true
            currentBinding.layoutDoctorConversationEmpty.isVisible = state.messages.isEmpty()
            currentBinding.btnDoctorAddInfo.isVisible = hasUserMessage && canContinueInquiry
            currentBinding.btnDoctorGenerateAssessment.isVisible = hasUserMessage && canContinueInquiry
            currentBinding.btnDoctorRestart.isVisible = hasUserMessage

            currentBinding.btnDoctorGenerateAssessment.isEnabled = state.canGenerateAssessment && !state.isSending
            currentBinding.btnDoctorRestart.isEnabled = !state.isSending
            currentBinding.btnDoctorAddInfo.isEnabled = !state.isSending
            currentBinding.btnDoctorHistory.isEnabled = state.historySummaries.isNotEmpty()

            bindRecommendationCards(state.recommendedInterventions)

            adapter.submitList(state.messages) {
                val listBinding = bindingOrNull ?: return@submitList
                if (state.messages.isNotEmpty()) {
                    listBinding.rvDoctorChat.scrollToPosition(state.messages.lastIndex)
                }
                maybeAutoSpeakLatestAssistant(state)
            }

            currentBinding.btnDoctorSend.isEnabled = !state.isSending
            currentBinding.btnDoctorVoiceToggle.isEnabled = !state.isSending || isRecording
            currentBinding.btnDoctorVoiceCallMode.isEnabled = !state.isSending || isVoiceCallModeActive
            currentBinding.btnDoctorSend.text = if (state.isSending) {
                getString(R.string.doctor_sending)
            } else {
                getString(R.string.doctor_send)
            }

            if (state.isSending && !isRecording && !AvatarController.isAudioPlaying.value) {
                autoResumeJob?.cancel()
                setVoiceCallState(VoiceCallState.WAITING_REPLY)
            } else if (!state.isSending && !isRecording && !AvatarController.isAudioPlaying.value && !pendingAssistantPlayback) {
                if (voiceCallState == VoiceCallState.WAITING_REPLY) {
                    setVoiceCallState(
                        if (isVoiceCallModeActive) VoiceCallState.COOLDOWN else VoiceCallState.IDLE
                    )
                }
                if (isVoiceCallModeActive) {
                    suppressListeningUntilMs = SystemClock.elapsedRealtime() + SPEECH_RESUME_COOLDOWN_MS
                    resumeContinuousListening(SPEECH_RESUME_COOLDOWN_MS)
                }
            }
        }

        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is DoctorEvent.NavigateToBreathing -> {
                    findNavController().navigate(
                        R.id.navigation_breathing_coach,
                        bundleOf(
                            "protocolType" to event.protocolType,
                            "durationSec" to event.durationSec,
                            "taskId" to event.taskId
                        )
                    )
                    viewModel.consumeEvent()
                }

                is DoctorEvent.Toast -> {
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    viewModel.consumeEvent()
                }

                null -> Unit
            }
        }
    }

    private fun maybeAutoSpeakLatestAssistant(state: DoctorUiState) {
        val latestAssistant = state.messages.lastOrNull { it.role == DoctorRole.ASSISTANT && !it.isPending } ?: return
        if (lastAutoSpokenMessageId == null) {
            lastAutoSpokenMessageId = latestAssistant.id
            return
        }
        if (latestAssistant.id == lastAutoSpokenMessageId) {
            return
        }
        lastAutoSpokenMessageId = latestAssistant.id
        val currentBinding = bindingOrNull ?: return
        if (!currentBinding.switchDoctorAutoSpeech.isChecked) {
            if (isVoiceCallModeActive) {
                suppressListeningUntilMs = SystemClock.elapsedRealtime() + SPEECH_RESUME_COOLDOWN_MS
                setVoiceCallState(VoiceCallState.COOLDOWN)
                resumeContinuousListening(SPEECH_RESUME_COOLDOWN_MS)
            }
            return
        }

        val profile = when (latestAssistant.messageType) {
            DoctorMessageType.ASSESSMENT -> "doctor_summary"
            DoctorMessageType.FOLLOW_UP -> "calm_assistant"
            DoctorMessageType.TEXT -> "calm_assistant"
        }

        viewLifecycleOwner.lifecycleScope.launch {
            setVoiceCallState(VoiceCallState.SPEAKING)
            pendingAssistantPlayback = true
            val audioUrl = runCatching {
                if (XfyunConfig.ttsCredentials.isReady) {
                    android.util.Log.i(TAG, "Using Xfyun TTS for doctor reply, type=${latestAssistant.messageType}")
                    xfyunTtsClient.synthesize(latestAssistant.content)
                } else {
                    android.util.Log.i(TAG, "Falling back to cloud TTS for doctor reply, type=${latestAssistant.messageType}")
                    speechService.synthesize(
                        text = latestAssistant.content,
                        profile = profile
                    )?.audioUrl.orEmpty()
                }
            }.getOrElse {
                speechService.synthesize(
                    text = latestAssistant.content,
                    profile = profile
                )?.audioUrl.orEmpty()
            }
            if (audioUrl.isBlank()) {
                pendingAssistantPlayback = false
                suppressListeningUntilMs = SystemClock.elapsedRealtime() + SPEECH_RESUME_COOLDOWN_MS
                setVoiceCallState(if (isVoiceCallModeActive) VoiceCallState.COOLDOWN else VoiceCallState.IDLE)
                if (isVoiceCallModeActive) {
                    resumeContinuousListening(SPEECH_RESUME_COOLDOWN_MS)
                }
                return@launch
            }
            (activity as? AvatarAudioHost)?.speakAvatarAudio(latestAssistant.content, audioUrl)
        }
    }

    private fun showHistoryDialog() {
        val state = viewModel.uiState.value ?: return
        val items = state.historySummaries
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.doctor_history_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val labels = items.map { item ->
            "${item.title}\n${item.subtitle}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.doctor_history_title)
            .setItems(labels) { _, which ->
                viewModel.loadSession(items[which].sessionId)
            }
            .show()
    }

    private fun bindRecommendationCards(recommendations: List<DoctorRecommendation>) {
        val rec1 = recommendations.getOrNull(0)
        binding.tvDoctorRec1Title.text = rec1?.let { recommendationTitle(it) }
            ?: getString(R.string.doctor_recommendation_placeholder)
        binding.tvDoctorRec1Reason.text = rec1?.reason
            ?: getString(R.string.doctor_recommendation_reason_placeholder)
        binding.tvDoctorRec1Effect.text = rec1?.expectedEffect
            ?: getString(R.string.doctor_recommendation_effect_placeholder)
        binding.tvDoctorRec1Confidence.text = rec1?.let {
            getString(R.string.doctor_confidence_format, it.confidence)
        } ?: getString(R.string.doctor_confidence_default)
        binding.btnDoctorRec1Start.isEnabled = rec1 != null

        val rec2 = recommendations.getOrNull(1)
        binding.tvDoctorRec2Title.text = rec2?.let { recommendationTitle(it) }
            ?: getString(R.string.doctor_recommendation_placeholder)
        binding.tvDoctorRec2Reason.text = rec2?.reason
            ?: getString(R.string.doctor_recommendation_reason_placeholder)
        binding.tvDoctorRec2Effect.text = rec2?.expectedEffect
            ?: getString(R.string.doctor_recommendation_effect_placeholder)
        binding.tvDoctorRec2Confidence.text = rec2?.let {
            getString(R.string.doctor_confidence_format, it.confidence)
        } ?: getString(R.string.doctor_confidence_default)
        binding.btnDoctorRec2Start.isEnabled = rec2 != null
    }

    private fun recommendationTitle(recommendation: DoctorRecommendation): String {
        val protocolLabel = when (recommendation.protocolType) {
            "BREATH_4_6" -> getString(R.string.doctor_protocol_46)
            "BREATH_4_7_8" -> getString(R.string.doctor_protocol_478)
            "BOX" -> getString(R.string.doctor_protocol_box)
            else -> recommendation.protocolType
        }
        return getString(
            R.string.doctor_recommendation_title_format,
            protocolLabel,
            recommendation.durationSec / 60
        )
    }

    private fun seedMultimodalDefaults() {
        binding.etDoctorImagePrompt.setText(getString(R.string.doctor_image_example_cover))
        binding.etDoctorVideoPrompt.setText(getString(R.string.doctor_video_example_sleep))
        binding.tvDoctorImageStatus.text = getString(R.string.doctor_image_status_idle)
        binding.tvDoctorVideoStatus.text = getString(R.string.doctor_video_status_idle)
        renderSupportPanel()
        setVoiceCallState(VoiceCallState.IDLE)
    }

    private fun renderSupportPanel() {
        binding.layoutDoctorSupportContent.isVisible = isSupportPanelExpanded
        binding.btnDoctorSupportToggle.text = if (isSupportPanelExpanded) {
            getString(R.string.doctor_support_toggle_close)
        } else {
            getString(R.string.doctor_support_toggle_open)
        }
    }

    private fun sendCurrentInput() {
        val text = binding.etDoctorInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        viewModel.sendMessage(text)
        binding.etDoctorInput.setText("")
        if (!isRecording && !AvatarController.isAudioPlaying.value) {
            setVoiceCallState(VoiceCallState.WAITING_REPLY)
        }
    }

    private fun consumePrefillMessage() {
        val prefill = findNavController()
            .previousBackStackEntry
            ?.savedStateHandle
            ?.remove<String>(DOCTOR_PREFILL_MESSAGE_KEY)
            .orEmpty()
        if (prefill.isBlank()) return
        binding.etDoctorInput.setText(prefill)
        binding.etDoctorInput.setSelection(prefill.length)
    }

    private fun toggleVoiceRecording() {
        if (isRecording) {
            stopVoiceRecognition()
            return
        }
        if (AvatarController.isAudioPlaying.value) {
            (activity as? AvatarAudioHost)?.stopAvatarAudio()
        }
        requestVoiceRecording(forcePrompt = true)
    }

    private fun toggleVoiceCallMode() {
        isVoiceCallModeActive = !isVoiceCallModeActive
        if (!isVoiceCallModeActive) {
            stopContinuousVoiceSession()
        } else {
            recordPermissionDenied = false
            binding.switchDoctorAutoSpeech.isChecked = true
            suppressListeningUntilMs = SystemClock.elapsedRealtime() + 320L
            (activity as? AvatarAudioHost)?.stopAvatarAudio()
            setVoiceCallState(VoiceCallState.CALL_READY)
            requestVoiceRecording(forcePrompt = true)
        }
    }

    private fun startVoiceRecording() {
        if (isRecording) return
        autoResumeJob?.cancel()
        if (AvatarController.isAudioPlaying.value) return
        if (XfyunConfig.iatCredentials.isReady) {
            startXfyunVoiceRecognition()
            return
        }
        Toast.makeText(requireContext(), getString(R.string.doctor_voice_not_configured), Toast.LENGTH_SHORT).show()
        setVoiceCallState(if (isVoiceCallModeActive) VoiceCallState.CALL_READY else VoiceCallState.IDLE)
    }

    private fun startXfyunVoiceRecognition() {
        isRecording = true
        setVoiceCallState(VoiceCallState.RECORDING)
        android.util.Log.i(TAG, "Starting Xfyun IAT recognition, voiceCallMode=$isVoiceCallModeActive")
        voiceRecognitionJob?.cancel()
        voiceRecognitionJob = lifecycleScope.launch {
            val transcript = runCatching {
                xfyunIatClient.recognizeOnce(
                    hotWords = listOf("睡眠", "恢复", "压力", "呼吸", "血氧", "训练", "疲劳")
                )
            }.getOrElse { throwable ->
                if (throwable !is CancellationException) {
                    Toast.makeText(
                        requireContext(),
                        throwable.message ?: getString(R.string.doctor_error_retry),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                ""
            }.trim()
            voiceRecognitionJob = null
            isRecording = false
            if (!isAdded || _binding == null) return@launch
            handleRecognizedTranscript(transcript)
        }
    }

    private fun stopVoiceRecognition() {
        autoResumeJob?.cancel()
        xfyunIatClient.cancelCurrentRecognition()
        voiceRecognitionJob?.cancel()
        voiceRecognitionJob = null
        isRecording = false
        setVoiceCallState(if (isVoiceCallModeActive) VoiceCallState.CALL_READY else VoiceCallState.IDLE)
    }

    private fun handleRecognizedTranscript(transcript: String) {
        android.util.Log.i(TAG, "Received transcript from Xfyun IAT: '${transcript.take(48)}'")
        if (transcript.isBlank()) {
            setVoiceCallState(if (isVoiceCallModeActive) VoiceCallState.COOLDOWN else VoiceCallState.IDLE)
            Toast.makeText(requireContext(), getString(R.string.doctor_voice_transcription_empty), Toast.LENGTH_SHORT).show()
            if (isVoiceCallModeActive) {
                suppressListeningUntilMs = SystemClock.elapsedRealtime() + SPEECH_RESUME_COOLDOWN_MS
                resumeContinuousListening(SPEECH_RESUME_COOLDOWN_MS)
            }
            return
        }
        if (isVoiceCallModeActive) {
            binding.etDoctorInput.setText(transcript)
            binding.etDoctorInput.setSelection(transcript.length)
            Toast.makeText(requireContext(), getString(R.string.doctor_voice_call_transcription_done), Toast.LENGTH_SHORT).show()
            sendCurrentInput()
        } else {
            binding.etDoctorInput.setText(transcript)
            binding.etDoctorInput.setSelection(transcript.length)
            setVoiceCallState(VoiceCallState.IDLE)
            Toast.makeText(requireContext(), getString(R.string.doctor_voice_transcription_done), Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateDoctorImage() {
        val prompt = binding.etDoctorImagePrompt.text?.toString().orEmpty().trim()
        if (prompt.isBlank()) return
        binding.tvDoctorImageStatus.text = getString(R.string.doctor_image_status_generating)
        binding.btnDoctorOpenImage.visibility = View.GONE
        currentImageUrl = null
        lifecycleScope.launch {
            val result = mediaGenerationService.generateImage(
                prompt = prompt,
                profile = currentImageProfile()
            )
            if (result?.imageUrl.isNullOrBlank()) {
                binding.tvDoctorImageStatus.text = getString(R.string.doctor_image_status_failed)
                return@launch
            }
            currentImageUrl = result!!.imageUrl
            loadImagePreview(result.imageUrl)
            binding.btnDoctorOpenImage.visibility = View.VISIBLE
            binding.tvDoctorImageStatus.text = getString(R.string.doctor_image_status_done)
        }
    }

    private fun generateDoctorVideo() {
        val prompt = binding.etDoctorVideoPrompt.text?.toString().orEmpty().trim()
        if (prompt.isBlank()) return
        binding.tvDoctorVideoStatus.text = getString(R.string.doctor_video_status_generating)
        binding.btnDoctorOpenVideo.visibility = View.GONE
        currentVideoUrl = null
        lifecycleScope.launch {
            val result = mediaGenerationService.generateVideo(
                prompt = prompt,
                profile = currentVideoProfile()
            )
            if (result == null || result.jobId.isBlank()) {
                binding.tvDoctorVideoStatus.text = getString(R.string.doctor_video_status_failed)
                return@launch
            }
            currentVideoJobId = result.jobId
            binding.tvDoctorVideoStatus.text = getString(R.string.doctor_video_status_queued) + " · " + result.jobId
        }
    }

    private fun pollDoctorVideo() {
        val jobId = currentVideoJobId ?: return
        lifecycleScope.launch {
            val result = mediaGenerationService.getVideoJob(jobId)
            if (result == null) {
                binding.tvDoctorVideoStatus.text = getString(R.string.doctor_video_status_failed)
                return@launch
            }
            currentVideoUrl = result.videoUrl.takeIf { it.isNotBlank() }
            binding.tvDoctorVideoStatus.text = when (result.status.lowercase()) {
                "queued", "running" -> getString(R.string.doctor_video_status_queued)
                "succeeded", "completed" -> getString(R.string.doctor_video_status_queued)
                else -> getString(R.string.doctor_video_status_failed)
            } + " · " + jobId
            binding.btnDoctorOpenVideo.visibility = if (currentVideoUrl.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun currentImageProfile(): String {
        return when (binding.chipGroupDoctorImageProfiles.checkedChipId) {
            com.example.newstart.feature.doctor.R.id.chip_doctor_image_profile_education -> "medical_education_illustration"
            com.example.newstart.feature.doctor.R.id.chip_doctor_image_profile_cover -> "clinical_ui_cover"
            else -> "medical_wellness_product"
        }
    }

    private fun currentVideoProfile(): String {
        return when (binding.chipGroupDoctorVideoProfiles.checkedChipId) {
            com.example.newstart.feature.doctor.R.id.chip_doctor_video_profile_recovery -> "recovery_demo"
            com.example.newstart.feature.doctor.R.id.chip_doctor_video_profile_story -> "calm_product_story"
            else -> "sleep_guidance"
        }
    }

    private suspend fun loadImagePreview(url: String) {
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (url.startsWith("data:image")) {
                    val base64 = url.substringAfter("base64,")
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        }
        if (bitmap != null) {
            binding.imgDoctorGeneratedImage.setImageBitmap(bitmap)
        }
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(requireContext(), it.message ?: "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestVoiceRecording(forcePrompt: Boolean = false) {
        if (!isAdded || _binding == null || isRecording) return
        if (viewModel.uiState.value?.isSending == true) return
        if (pendingAssistantPlayback) return
        if (AvatarController.isAudioPlaying.value) return
        if (SystemClock.elapsedRealtime() < suppressListeningUntilMs) return
        if (hasRecordAudioPermission()) {
            startVoiceRecording()
            return
        }
        if (!forcePrompt && recordPermissionDenied) {
            setVoiceCallState(if (isVoiceCallModeActive) VoiceCallState.CALL_READY else VoiceCallState.IDLE)
            return
        }
        requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun resumeContinuousListening(delayMs: Long = AUTO_RESUME_DELAY_MS) {
        if (!isVoiceCallModeActive) return
        autoResumeJob?.cancel()
        autoResumeJob = lifecycleScope.launch {
            delay(delayMs)
            if (!isVoiceCallModeActive || !isAdded || _binding == null) return@launch
            if (
                isRecording ||
                pendingAssistantPlayback ||
                AvatarController.isAudioPlaying.value ||
                viewModel.uiState.value?.isSending == true ||
                SystemClock.elapsedRealtime() < suppressListeningUntilMs
            ) {
                return@launch
            }
            requestVoiceRecording(forcePrompt = false)
        }
    }

    private fun stopContinuousVoiceSession() {
        autoResumeJob?.cancel()
        suppressListeningUntilMs = 0L
        pendingAssistantPlayback = false
        stopVoiceRecognition()
        (activity as? AvatarAudioHost)?.stopAvatarAudio()
        setVoiceCallState(VoiceCallState.IDLE)
    }

    private fun setVoiceCallState(state: VoiceCallState) {
        voiceCallState = state
        binding.tvDoctorVoiceStatus.text = when (state) {
            VoiceCallState.IDLE -> getString(R.string.doctor_voice_status_idle)
            VoiceCallState.CALL_READY -> getString(R.string.doctor_voice_status_call_ready)
            VoiceCallState.RECORDING -> getString(R.string.doctor_voice_status_recording)
            VoiceCallState.TRANSCRIBING -> getString(R.string.doctor_voice_status_transcribing)
            VoiceCallState.WAITING_REPLY -> getString(R.string.doctor_voice_status_waiting_reply)
            VoiceCallState.SPEAKING -> getString(R.string.doctor_voice_status_speaking)
            VoiceCallState.COOLDOWN -> getString(R.string.doctor_voice_status_cooldown)
        }
        binding.btnDoctorVoiceToggle.text = if (state == VoiceCallState.RECORDING) {
            getString(R.string.doctor_voice_toggle_stop)
        } else {
            getString(R.string.doctor_voice_toggle_start)
        }
        binding.btnDoctorVoiceCallMode.text = if (isVoiceCallModeActive) {
            getString(R.string.doctor_voice_call_end)
        } else {
            getString(R.string.doctor_voice_call_start)
        }
    }

    override fun onDestroyView() {
        stopContinuousVoiceSession()
        super.onDestroyView()
        _binding = null
    }
}


