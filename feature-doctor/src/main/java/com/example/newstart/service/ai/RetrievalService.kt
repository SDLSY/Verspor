package com.example.newstart.service.ai

import android.content.Context

object RetrievalService {

    fun buildDoctorRagContext(context: Context, question: String): String {
        return LocalDoctorRetrievalService.buildRagContext(context, question)
    }
}
