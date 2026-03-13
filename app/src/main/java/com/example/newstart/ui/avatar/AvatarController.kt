package com.example.newstart.ui.avatar

import com.example.newstart.util.PerformanceTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AvatarGuideEvent {
    data class PageEntered(val destinationId: Int) : AvatarGuideEvent
    data object AvatarTapped : AvatarGuideEvent
    data class CustomText(
        val text: String,
        val durationMs: Long = 3_000L
    ) : AvatarGuideEvent
}

/**
 * Global singleton controller to orchestrate persistent avatar behavior.
 */
object AvatarController {

    const val ANIM_POINTING = 0
    const val ANIM_WAVING = 1
    const val ANIM_JUMPING = 2

    private const val DEFAULT_SPEAK_DURATION_MS = 3_000L
    private const val MIN_AUTO_SPEAK_MS = 2_400L
    private const val MAX_AUTO_SPEAK_MS = 4_200L
    private const val PER_CHAR_SPEAK_MS = 48L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speakJob: Job? = null

    private val _currentRole = MutableStateFlow(AvatarAnimRole.IDLE)
    val currentRole: StateFlow<AvatarAnimRole> = _currentRole.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying.asStateFlow()

    private val _speechText = MutableStateFlow("")
    val speechText: StateFlow<String> = _speechText.asStateFlow()

    private val _currentDestinationId = MutableStateFlow(-1)
    val currentDestinationId: StateFlow<Int> = _currentDestinationId.asStateFlow()

    fun onPageEntered(destinationId: Int) {
        _currentDestinationId.value = destinationId
    }

    fun onAvatarTapped() {
        emitGuide(AvatarGuideEvent.AvatarTapped)
    }

    fun emitGuide(event: AvatarGuideEvent) {
        when (event) {
            is AvatarGuideEvent.PageEntered -> handlePageEntered(event.destinationId)
            AvatarGuideEvent.AvatarTapped -> handleAvatarTapped()
            is AvatarGuideEvent.CustomText -> speakInternal(
                text = event.text,
                durationMs = event.durationMs,
                trigger = "custom"
            )
        }
    }

    /**
     * Legacy compatibility API.
     */
    fun performAction(actionIndex: Int) {
        _currentRole.value = when (actionIndex) {
            ANIM_POINTING -> AvatarAnimRole.SPEAK
            ANIM_JUMPING -> AvatarAnimRole.EMPHASIS
            else -> AvatarAnimRole.IDLE
        }
    }

    fun performSemanticAction(action: String) {
        speakJob?.cancel()
        _currentRole.value = when (action.lowercase()) {
            "wave", "encourage", "listen" -> AvatarAnimRole.EMPHASIS
            "point", "alert" -> AvatarAnimRole.SPEAK
            else -> AvatarAnimRole.IDLE
        }
        if (_currentRole.value != AvatarAnimRole.IDLE) {
            speakJob = scope.launch {
                delay(520L)
                if (!_isSpeaking.value && !_isAudioPlaying.value) {
                    _currentRole.value = AvatarAnimRole.IDLE
                }
            }
        }
    }

    /**
     * Legacy compatibility API.
     */
    fun speak(text: String, durationMs: Long = DEFAULT_SPEAK_DURATION_MS) {
        speakInternal(text = text, durationMs = durationMs, trigger = "manual")
    }

    fun stopSpeaking() {
        speakJob?.cancel()
        _isSpeaking.value = false
        _isAudioPlaying.value = false
        _speechText.value = ""
        _currentRole.value = AvatarAnimRole.IDLE
    }

    fun speakWithAudio(text: String, trigger: String = "tts") {
        if (text.isBlank()) return
        speakJob?.cancel()
        _speechText.value = text
        _isSpeaking.value = true
        _isAudioPlaying.value = true
        _currentRole.value = AvatarAnimRole.EMPHASIS

        speakJob = scope.launch {
            delay(280L)
            if (_speechText.value == text && _isAudioPlaying.value) {
                _currentRole.value = AvatarAnimRole.SPEAK
            }
        }

        PerformanceTelemetry.record(
            metric = "avatar_dialogue_trigger_count",
            value = 1.0,
            unit = "count",
            attributes = mapOf(
                "trigger" to trigger,
                "destination" to _currentDestinationId.value.toString()
            )
        )
    }

    fun stopAudio() {
        speakJob?.cancel()
        _isAudioPlaying.value = false
        _isSpeaking.value = false
        _speechText.value = ""
        _currentRole.value = AvatarAnimRole.IDLE
    }

    private fun handlePageEntered(destinationId: Int) {
        _currentDestinationId.value = destinationId
    }

    private fun handleAvatarTapped() {
        if (!_isSpeaking.value && !_isAudioPlaying.value) {
            _currentRole.value = AvatarAnimRole.EMPHASIS
            speakJob = scope.launch {
                delay(520L)
                if (!_isSpeaking.value && !_isAudioPlaying.value) {
                    _currentRole.value = AvatarAnimRole.IDLE
                }
            }
        }
    }

    private fun speakInternal(text: String, durationMs: Long, trigger: String) {
        if (text.isBlank()) {
            return
        }

        speakJob?.cancel()
        _speechText.value = text
        _isSpeaking.value = true
        _isAudioPlaying.value = false
        _currentRole.value = AvatarAnimRole.SPEAK

        PerformanceTelemetry.record(
            metric = "avatar_dialogue_trigger_count",
            value = 1.0,
            unit = "count",
            attributes = mapOf(
                "trigger" to trigger,
                "destination" to _currentDestinationId.value.toString()
            )
        )

        speakJob = scope.launch {
            delay(durationMs.coerceAtLeast(500L))
            if (_speechText.value == text) {
                _isSpeaking.value = false
                _isAudioPlaying.value = false
                _speechText.value = ""
                _currentRole.value = AvatarAnimRole.IDLE
            }
        }
    }

    private fun String.estimatedSpeakDuration(): Long {
        val normalizedLength = trim().length.coerceAtLeast(8)
        return (normalizedLength * PER_CHAR_SPEAK_MS)
            .coerceIn(MIN_AUTO_SPEAK_MS, MAX_AUTO_SPEAK_MS)
    }
}
