package com.example.newstart.repository.prescription

import com.example.newstart.intervention.InterventionProfileSnapshot

object PrescriptionRagContextBuilder {

    private data class RagSnippet(
        val key: String,
        val title: String,
        val content: String
    )

    private val snippets = listOf(
        RagSnippet(
            key = "sleep_high_arousal",
            title = "睡前高唤醒",
            content = "当睡眠扰动和压力同时偏高时，优先考虑睡前减刺激流程，再接身体扫描或 NSDR，而不是一开始就安排高执行成本训练。"
        ),
        RagSnippet(
            key = "stress_relief",
            title = "高压力减负",
            content = "白天压力或焦虑偏高时，优先选择短时、清晰、低门槛的减压方案，例如节律呼吸或渐进式肌肉放松。"
        ),
        RagSnippet(
            key = "fatigue_recovery",
            title = "疲劳恢复",
            content = "恢复能力偏低、疲劳负荷高时，优先安排恢复步行和轻拉伸，避免连续堆叠静息类训练。"
        ),
        RagSnippet(
            key = "adherence",
            title = "依从性",
            content = "近期连续未完成同类干预时，应降低该类处方优先级，改成更容易开始、反馈更直接的替代方案。"
        ),
        RagSnippet(
            key = "doctor_priority",
            title = "医生优先",
            content = "出现胸痛、晕厥、严重呼吸困难、明显抑郁高风险或其他红旗时，必须把医生评估前置，放松训练只能作为辅助。"
        ),
        RagSnippet(
            key = "blood_pressure",
            title = "血压相关管理",
            content = "当证据涉及血压波动或血压异常时，优先考虑稳态恢复方案，例如节律呼吸和低强度步行，并加强就医提醒。"
        )
    )

    fun build(snapshot: InterventionProfileSnapshot): String {
        val keys = linkedSetOf<String>()
        val sleep = snapshot.domainScores["sleepDisturbance"] ?: 0
        val stress = snapshot.domainScores["stressLoad"] ?: 0
        val fatigue = snapshot.domainScores["fatigueLoad"] ?: 0
        val recovery = snapshot.domainScores["recoveryCapacity"] ?: 50
        val adherence = snapshot.domainScores["adherenceReadiness"] ?: 100

        if (sleep >= 60 || (sleep >= 50 && stress >= 55)) keys += "sleep_high_arousal"
        if (stress >= 60 || (snapshot.domainScores["anxietyRisk"] ?: 0) >= 60) keys += "stress_relief"
        if (fatigue >= 60 || recovery <= 40) keys += "fatigue_recovery"
        if (adherence <= 45) keys += "adherence"
        if (snapshot.redFlags.isNotEmpty() || (snapshot.domainScores["depressiveRisk"] ?: 0) >= 75) keys += "doctor_priority"
        if (snapshot.evidenceFacts.values.flatten().any { it.contains("血压") }) keys += "blood_pressure"

        if (keys.isEmpty()) {
            keys += "stress_relief"
            keys += "adherence"
        }

        return snippets
            .filter { it.key in keys }
            .joinToString("\n") { "- ${it.title}：${it.content}" }
    }
}
