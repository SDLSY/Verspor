package com.example.newstart.ai

import com.example.newstart.data.ActionAdvice
import com.example.newstart.data.AdviceType
import com.example.newstart.data.HealthMetrics
import com.example.newstart.data.RecoveryScore
import com.example.newstart.data.SleepData

private data class RagKnowledge(
    val tags: Set<String>,
    val title: String,
    val content: String,
)

private data class RetrievalScore(
    val knowledge: RagKnowledge,
    val score: Float,
)

object EdgeLlmRagAdvisor {

    private val knowledgeBase = listOf(
        RagKnowledge(
            tags = setOf("sleep_debt", "low_recovery"),
            title = "补觉优先策略",
            content = "恢复分偏低时优先保证睡眠总时长，建议今夜提前45-90分钟入睡。"
        ),
        RagKnowledge(
            tags = setOf("stress", "hrv_low"),
            title = "减压干预策略",
            content = "HRV偏低时建议采用4-7-8呼吸法或10分钟正念放松，避免晚间高刺激活动。"
        ),
        RagKnowledge(
            tags = setOf("exercise_high"),
            title = "高状态训练窗口",
            content = "恢复状态高时可安排中高强度训练，优先在上午或下午早段完成。"
        ),
        RagKnowledge(
            tags = setOf("spo2_watch"),
            title = "血氧稳定性关注",
            content = "若最低血氧偏低，应关注夜间通气环境并降低当天训练负荷。"
        ),
        RagKnowledge(
            tags = setOf("wake_fragment", "sleep_quality"),
            title = "减少夜间碎片化",
            content = "夜间觉醒次数偏多时，建议固定入睡时间并减少睡前屏幕暴露。"
        ),
    )

    fun generateAdvice(
        recoveryScore: RecoveryScore,
        sleepData: SleepData,
        healthMetrics: HealthMetrics,
    ): List<ActionAdvice> {
        val tags = inferTags(recoveryScore, sleepData, healthMetrics)
        val topicWeights = EdgeLlmOnDeviceModel.predictTopicWeights(recoveryScore, sleepData, healthMetrics)
        val snippets = retrieve(tags, topicWeights)

        val summary = buildSummary(recoveryScore, sleepData, healthMetrics, topicWeights != null)
        val advice = mutableListOf<ActionAdvice>()

        advice += ActionAdvice(
            type = AdviceType.FOCUS,
            title = "端侧AI总结",
            description = "$summary（RAG命中${snippets.size}条）"
        )

        snippets.take(3).forEach { snippet ->
            advice += ActionAdvice(
                type = mapType(snippet.tags),
                title = snippet.title,
                description = snippet.content
            )
        }

        return advice
    }

    private fun inferTags(
        recoveryScore: RecoveryScore,
        sleepData: SleepData,
        metrics: HealthMetrics,
    ): Set<String> {
        val tags = mutableSetOf<String>()

        if (recoveryScore.score < 60) {
            tags += "low_recovery"
        }
        if (sleepData.totalSleepMinutes < 420) {
            tags += "sleep_debt"
        }
        if (sleepData.awakeCount >= 3) {
            tags += "wake_fragment"
            tags += "sleep_quality"
        }
        if (metrics.hrv.current < metrics.hrv.baseline) {
            tags += "stress"
            tags += "hrv_low"
        }
        if (metrics.bloodOxygen.min < 94) {
            tags += "spo2_watch"
        }
        if (recoveryScore.score >= 75) {
            tags += "exercise_high"
        }

        return tags
    }

    private fun retrieve(tags: Set<String>, topicWeights: FloatArray?): List<RagKnowledge> {
        val primaryTags = listOf("exercise_high", "sleep_debt", "stress", "spo2_watch", "wake_fragment")
        if (tags.isEmpty()) {
            return knowledgeBase.take(2)
        }

        return knowledgeBase
            .map { item ->
                val ragScore = item.tags.intersect(tags).size.toFloat()
                val modelScore = if (topicWeights == null) {
                    0f
                } else {
                    val first = primaryTags.indexOfFirst { it in item.tags }
                    if (first >= 0 && first < topicWeights.size) topicWeights[first] else 0f
                }
                RetrievalScore(item, ragScore + modelScore)
            }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }
            .map { it.knowledge }
    }

    private fun buildSummary(
        recoveryScore: RecoveryScore,
        sleepData: SleepData,
        metrics: HealthMetrics,
        usingModel: Boolean,
    ): String {
        val source = if (usingModel) "端侧模型" else "规则"
        return "恢复分${recoveryScore.score}，睡眠${sleepData.totalSleepMinutes}分钟，最低血氧${metrics.bloodOxygen.min}%（$source）"
    }

    private fun mapType(tags: Set<String>): AdviceType {
        return when {
            "exercise_high" in tags -> AdviceType.EXERCISE
            "stress" in tags || "hrv_low" in tags -> AdviceType.STRESS
            "sleep_debt" in tags || "wake_fragment" in tags -> AdviceType.SLEEP
            else -> AdviceType.FOCUS
        }
    }
}
