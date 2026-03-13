package com.example.newstart.service.ai

import android.content.Context
import com.example.newstart.core.common.R
import java.util.Locale

private data class DoctorRagDoc(
    val keywords: Set<String>,
    val title: String,
    val content: String
)

object LocalDoctorRetrievalService {

    fun buildRagContext(context: Context, question: String): String {
        val q = question.lowercase(Locale.ROOT)
        return ragDocs(context)
            .map { doc ->
                val score = doc.keywords.count { keyword -> q.contains(keyword) }
                doc to score
            }
            .sortedByDescending { it.second }
            .take(2)
            .joinToString("\n") { (doc, _) -> "- ${doc.title}: ${doc.content}" }
    }

    private fun ragDocs(context: Context): List<DoctorRagDoc> {
        return listOf(
            DoctorRagDoc(
                keywords = setOf("sleep", "insomnia", "rest", "失眠", "睡眠"),
                title = context.getString(R.string.doctor_rag_sleep_title),
                content = context.getString(R.string.doctor_rag_sleep_content)
            ),
            DoctorRagDoc(
                keywords = setOf("stress", "pressure", "hrv", "压力", "焦虑"),
                title = context.getString(R.string.doctor_rag_stress_title),
                content = context.getString(R.string.doctor_rag_stress_content)
            ),
            DoctorRagDoc(
                keywords = setOf("exercise", "train", "run", "训练", "运动"),
                title = context.getString(R.string.doctor_rag_training_title),
                content = context.getString(R.string.doctor_rag_training_content)
            ),
            DoctorRagDoc(
                keywords = setOf("spo2", "oxygen", "breathing", "血氧", "呼吸"),
                title = context.getString(R.string.doctor_rag_oxygen_title),
                content = context.getString(R.string.doctor_rag_oxygen_content)
            )
        )
    }
}
