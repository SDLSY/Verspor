package com.example.newstart.service.ai

import com.example.newstart.network.models.SpeechSynthesisData
import com.example.newstart.network.models.SpeechSynthesisRequest
import com.example.newstart.network.models.SpeechTranscriptionData
import com.example.newstart.network.models.SpeechTranscriptionRequest
import com.example.newstart.repository.NetworkRepository
import java.io.File

class SpeechService(
    private val networkRepository: NetworkRepository
) {

    suspend fun transcribe(audioUrl: String, mimeType: String = "audio/mpeg", hint: String = ""): SpeechTranscriptionData? {
        return networkRepository.transcribeSpeech(
            SpeechTranscriptionRequest(
                audioUrl = audioUrl,
                mimeType = mimeType,
                hint = hint
            )
        ).getOrNull()
    }

    suspend fun transcribeFile(
        file: File,
        mimeType: String = "audio/mp4",
        hint: String = ""
    ): SpeechTranscriptionData? {
        return networkRepository.transcribeSpeechFile(
            file = file,
            mimeType = mimeType,
            hint = hint
        ).getOrNull()
    }

    suspend fun synthesize(
        text: String,
        voice: String = "alloy",
        profile: String = "calm_assistant"
    ): SpeechSynthesisData? {
        return networkRepository.synthesizeSpeech(
            SpeechSynthesisRequest(
                text = text,
                voice = voice,
                profile = profile
            )
        ).getOrNull()
    }
}
