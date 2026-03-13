package com.example.newstart.service.ai

import com.example.newstart.intervention.PrescriptionBundleDetails
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.repository.PrescriptionRepository

class PrescriptionAiService(
    private val prescriptionRepository: PrescriptionRepository
) {

    suspend fun getLatestBundle(): PrescriptionBundleDetails? {
        return prescriptionRepository.getLatestActiveBundle()
    }

    suspend fun generateForTrigger(triggerType: ProfileTriggerType): PrescriptionBundleDetails? {
        return prescriptionRepository.generateForTrigger(triggerType)
    }
}
