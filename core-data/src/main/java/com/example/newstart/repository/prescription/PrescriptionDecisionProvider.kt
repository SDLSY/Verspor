package com.example.newstart.repository.prescription

import com.example.newstart.repository.prescription.PrescriptionDecisionPayload
import com.example.newstart.repository.prescription.PrescriptionDecisionRequest

interface PrescriptionDecisionProvider {
    val providerId: String

    suspend fun generate(request: PrescriptionDecisionRequest): PrescriptionDecisionPayload?
}

object PrescriptionDecisionProviders {
    fun default(): List<PrescriptionDecisionProvider> {
        return listOf(ApiPrescriptionDecisionProvider())
    }
}
