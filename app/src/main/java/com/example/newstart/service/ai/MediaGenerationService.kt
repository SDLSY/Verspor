package com.example.newstart.service.ai

import com.example.newstart.network.models.ImageGenerationData
import com.example.newstart.network.models.ImageGenerationRequest
import com.example.newstart.network.models.VideoGenerationData
import com.example.newstart.network.models.VideoGenerationRequest
import com.example.newstart.network.models.VideoJobData
import com.example.newstart.repository.NetworkRepository

class MediaGenerationService(
    private val networkRepository: NetworkRepository
) {

    suspend fun generateImage(
        prompt: String,
        size: String = "1024x1024",
        profile: String = "medical_wellness_product"
    ): ImageGenerationData? {
        return networkRepository.generateImage(
            ImageGenerationRequest(
                prompt = prompt,
                size = size,
                profile = profile
            )
        ).getOrNull()
    }

    suspend fun generateVideo(
        prompt: String,
        durationSec: Int = 10,
        profile: String = "sleep_guidance"
    ): VideoGenerationData? {
        return networkRepository.generateVideo(
            VideoGenerationRequest(
                prompt = prompt,
                durationSec = durationSec,
                profile = profile
            )
        ).getOrNull()
    }

    suspend fun getVideoJob(jobId: String): VideoJobData? {
        return networkRepository.getVideoJob(jobId).getOrNull()
    }
}
