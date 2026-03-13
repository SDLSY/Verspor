package com.example.newstart.ui.doctor

import java.util.Locale
import kotlin.math.abs

data class DoctorMetricSnapshot(
    val recoveryScore: Int,
    val sleepMinutes: Int,
    val sleepEfficiency: Float,
    val awakeCount: Int,
    val heartRate: Int,
    val spo2Min: Int,
    val hrvCurrent: Int,
    val hrvBaseline: Int
)

enum class DoctorRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class DoctorRiskSummary(
    val level: DoctorRiskLevel,
    val score: Int,
    val confidence: Int
)

enum class DoctorRecommendationTemplate {
    STABILIZE,
    BALANCE,
    SLEEP_PREP
}

data class DoctorRecommendationDraft(
    val protocolType: String,
    val durationSec: Int,
    val confidence: Int,
    val template: DoctorRecommendationTemplate
)

data class DoctorActionDraft(
    val protocolType: String,
    val durationSec: Int
)

object DoctorDecisionEngine {

    private val redFlagRules = listOf(
        "胸痛" to "主诉包含胸痛，需尽快线下排除急性心肺问题。",
        "胸闷" to "出现明显胸闷，建议尽快线下评估呼吸与循环风险。",
        "晕厥" to "出现晕厥或接近晕厥，需要线下急诊评估。",
        "昏迷" to "意识异常属于红旗症状。",
        "意识模糊" to "意识模糊属于红旗症状。",
        "呼吸困难" to "严重呼吸困难属于红旗症状。",
        "喘不上气" to "明显呼吸受限属于红旗症状。",
        "高热" to "持续高热需要线下排查感染或炎症。",
        "抽搐" to "抽搐属于急性神经系统红旗症状。",
        "偏瘫" to "急性肢体无力或偏瘫属于神经系统红旗症状。",
        "言语不清" to "言语不清需要尽快线下评估。",
        "黑便" to "可能存在消化道出血风险，需要及时线下处理。"
    )

    private val symptomRuleBook = listOf(
        SymptomRule(
            name = "睡眠节律紊乱",
            keywords = listOf("失眠", "入睡困难", "睡不着", "早醒", "睡得浅", "作息乱"),
            rationale = "主诉以入睡困难、夜间易醒或睡眠时相紊乱为主，符合睡眠节律相关问题特征。",
            department = "睡眠医学门诊"
        ),
        SymptomRule(
            name = "压力相关失眠",
            keywords = listOf("压力大", "焦虑", "紧张", "烦躁", "心慌", "脑子停不下来"),
            rationale = "主诉与精神紧张、交感兴奋和入睡困难相关，倾向于压力相关睡眠问题。",
            department = "心身医学科"
        ),
        SymptomRule(
            name = "恢复不足",
            keywords = listOf("疲劳", "乏力", "恢复差", "没精神", "白天困", "犯困"),
            rationale = "主诉集中在恢复感下降与持续疲劳，结合恢复分偏低，优先考虑恢复不足。",
            department = "全科 / 运动恢复门诊"
        ),
        SymptomRule(
            name = "睡眠呼吸异常风险",
            keywords = listOf("打鼾", "憋气", "呼吸不畅", "缺氧", "血氧低", "夜里喘"),
            rationale = "主诉涉及夜间呼吸或血氧异常表现，需要关注睡眠呼吸问题。",
            department = "呼吸与睡眠门诊"
        ),
        SymptomRule(
            name = "训练恢复失衡",
            keywords = listOf("训练", "跑步", "运动后", "心率高", "练完更累", "负荷"),
            rationale = "主诉与训练后恢复异常、负荷不匹配相关，符合训练恢复失衡特征。",
            department = "运动医学科"
        )
    )

    fun evaluateRisk(snapshot: DoctorMetricSnapshot): DoctorRiskSummary {
        var score = 0

        if (snapshot.recoveryScore in 1..59) score += 35
        if (snapshot.sleepMinutes in 1..389) score += 25
        if (snapshot.sleepEfficiency in 1f..84.9f) score += 12
        if (snapshot.awakeCount >= 3) score += 8
        if (snapshot.spo2Min in 1..93) score += 12
        if (snapshot.heartRate >= 95) score += 10
        if (snapshot.hrvCurrent > 0 && snapshot.hrvBaseline > 0 && snapshot.hrvCurrent < snapshot.hrvBaseline) {
            score += 8
        }

        val boundedScore = score.coerceIn(0, 100)
        val level = when {
            boundedScore >= 65 -> DoctorRiskLevel.HIGH
            boundedScore >= 35 -> DoctorRiskLevel.MEDIUM
            else -> DoctorRiskLevel.LOW
        }
        val confidence = (55 + abs(snapshot.recoveryScore - snapshot.sleepMinutes / 10))
            .coerceIn(55, 95)
        return DoctorRiskSummary(level = level, score = boundedScore, confidence = confidence)
    }

    fun buildRecommendations(
        summary: DoctorRiskSummary,
        snapshot: DoctorMetricSnapshot
    ): List<DoctorRecommendationDraft> {
        val high = listOf(
            DoctorRecommendationDraft(
                protocolType = "BREATH_4_6",
                durationSec = 180,
                confidence = (summary.confidence + 2).coerceAtMost(98),
                template = DoctorRecommendationTemplate.STABILIZE
            ),
            DoctorRecommendationDraft(
                protocolType = "BOX",
                durationSec = 300,
                confidence = (summary.confidence - 4).coerceAtLeast(60),
                template = DoctorRecommendationTemplate.SLEEP_PREP
            )
        )
        val medium = listOf(
            DoctorRecommendationDraft(
                protocolType = "BREATH_4_7_8",
                durationSec = if (snapshot.heartRate >= 90) 180 else 300,
                confidence = summary.confidence,
                template = DoctorRecommendationTemplate.BALANCE
            ),
            DoctorRecommendationDraft(
                protocolType = "BREATH_4_6",
                durationSec = 180,
                confidence = (summary.confidence - 5).coerceAtLeast(58),
                template = DoctorRecommendationTemplate.STABILIZE
            )
        )
        val low = listOf(
            DoctorRecommendationDraft(
                protocolType = "BREATH_4_6",
                durationSec = 60,
                confidence = summary.confidence,
                template = DoctorRecommendationTemplate.BALANCE
            ),
            DoctorRecommendationDraft(
                protocolType = "BOX",
                durationSec = 180,
                confidence = (summary.confidence - 8).coerceAtLeast(55),
                template = DoctorRecommendationTemplate.SLEEP_PREP
            )
        )
        return when (summary.level) {
            DoctorRiskLevel.HIGH -> high
            DoctorRiskLevel.MEDIUM -> medium
            DoctorRiskLevel.LOW -> low
        }
    }

    fun detectActionFromReply(reply: String): DoctorActionDraft? {
        val normalized = reply.lowercase(Locale.ROOT)
        val protocol = when {
            normalized.contains("4-7-8") || normalized.contains("478") -> "BREATH_4_7_8"
            normalized.contains("box") || normalized.contains("方块") -> "BOX"
            normalized.contains("4-6") || normalized.contains("46") -> "BREATH_4_6"
            else -> null
        } ?: return null

        val duration = when {
            normalized.contains("5 min") || normalized.contains("5-minute") ||
                normalized.contains("5分钟") || normalized.contains("5 分钟") -> 300
            normalized.contains("3 min") || normalized.contains("3-minute") ||
                normalized.contains("3分钟") || normalized.contains("3 分钟") -> 180
            else -> when (protocol) {
                "BREATH_4_7_8" -> 180
                "BOX" -> 300
                else -> 60
            }
        }
        return DoctorActionDraft(protocolType = protocol, durationSec = duration)
    }

    fun isLowValueReply(reply: String): Boolean {
        val normalized = reply.lowercase(Locale.ROOT)
        val canned = listOf(
            "do not have enough information",
            "not have enough information",
            "insufficient information",
            "cannot provide medical advice",
            "consult a healthcare professional for diagnosis"
        )
        return canned.any { normalized.contains(it) }
    }

    fun detectRedFlags(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return redFlagRules.mapNotNull { (keyword, tip) ->
            if (text.contains(keyword, ignoreCase = true)) tip else null
        }.distinct()
    }

    fun inferChiefComplaint(text: String): String {
        if (text.isBlank()) return "未提供明确主诉"
        return text
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(48)
    }

    fun buildFollowUpPayload(historyText: String, followUpCount: Int): DoctorFollowUpPayload {
        val missing = extractMissingInfo(historyText)
        val question = when (missing.firstOrNull()) {
            "持续时间" -> "这些问题持续多久了？是最近几天突然出现，还是已经反复一段时间了？"
            "昼夜规律" -> "这些不适主要出现在白天、夜间，还是临近睡前最明显？"
            "诱因" -> "最近有没有明显诱因，比如熬夜、训练负荷增加、情绪压力、饮酒或咖啡因摄入？"
            "伴随表现" -> "除了主要不适外，还有没有打鼾、心慌、胸闷、头痛、白天犯困或注意力下降？"
            else -> if (followUpCount >= 3) {
                "如果还有你觉得最影响判断的信息，请一次性补充给我，我会直接整理成结构化问诊单。"
            } else {
                "请再补充一个最关键的信息：主要不适、持续时间、诱因，以及是否伴随白天疲劳或夜间呼吸问题。"
            }
        }
        return DoctorFollowUpPayload(
            question = question,
            missingInfo = missing,
            stage = DoctorInquiryStage.CLARIFYING
        )
    }

    fun buildFallbackAssessment(
        historyText: String,
        snapshot: DoctorMetricSnapshot,
        riskSummary: DoctorRiskSummary,
        redFlags: List<String> = emptyList()
    ): DoctorAssessmentPayload {
        val symptomFacts = extractSymptomFacts(historyText, snapshot)
        val missingInfo = extractMissingInfo(historyText)
        val suspectedIssues = buildSuspectedIssues(historyText, snapshot, riskSummary)
        val recommendedDepartment = suspectedIssues.firstOrNull()?.let { issue ->
            symptomRuleBook.firstOrNull { it.name == issue.name }?.department
        }.orEmpty().ifBlank {
            when (riskSummary.level) {
                DoctorRiskLevel.HIGH -> "全科 / 呼吸与睡眠门诊"
                DoctorRiskLevel.MEDIUM -> "睡眠医学门诊"
                DoctorRiskLevel.LOW -> "全科"
            }
        }

        val doctorSummary = when {
            redFlags.isNotEmpty() -> "当前存在红旗症状，需要优先线下就医，不建议继续仅靠自我放松处理。"
            suspectedIssues.isNotEmpty() -> "根据当前主诉与指标，优先考虑 ${suspectedIssues.first().name}，建议按结构化问诊单继续线下评估。"
            else -> "当前信息更像恢复不足或睡眠质量波动，建议先补充关键信息并结合后续监测继续评估。"
        }

        return DoctorAssessmentPayload(
            chiefComplaint = inferChiefComplaint(historyText),
            symptomFacts = symptomFacts,
            missingInfo = missingInfo,
            suspectedIssues = suspectedIssues,
            riskLevel = riskSummary.level.name,
            redFlags = redFlags,
            recommendedDepartment = recommendedDepartment,
            nextStepAdvice = buildNextStepAdvice(riskSummary, redFlags, suspectedIssues, missingInfo),
            doctorSummary = doctorSummary,
            disclaimer = "本结果仅用于健康初筛与问诊整理，不能替代医生面诊、检查和正式诊断。"
        )
    }

    private fun extractMissingInfo(historyText: String): List<String> {
        val missing = mutableListOf<String>()
        if (!containsAny(historyText, listOf("天", "周", "月", "年", "最近", "持续", "反复", "一直"))) {
            missing += "持续时间"
        }
        if (!containsAny(historyText, listOf("白天", "夜里", "夜间", "睡前", "晨起", "早上", "下午"))) {
            missing += "昼夜规律"
        }
        if (!containsAny(historyText, listOf("压力", "熬夜", "训练", "工作", "考试", "加班", "咖啡", "酒", "诱因", "加重"))) {
            missing += "诱因"
        }
        if (!containsAny(historyText, listOf("打鼾", "心慌", "胸闷", "头痛", "犯困", "憋醒", "呼吸", "乏力", "焦虑"))) {
            missing += "伴随表现"
        }
        return missing
    }

    private fun extractSymptomFacts(
        historyText: String,
        snapshot: DoctorMetricSnapshot
    ): List<String> {
        val facts = mutableListOf<String>()
        if (historyText.isNotBlank()) {
            facts += "主诉要点：${inferChiefComplaint(historyText)}"
        }
        if (snapshot.sleepMinutes > 0) {
            facts += "近一次睡眠时长约 ${snapshot.sleepMinutes} 分钟"
        }
        if (snapshot.sleepEfficiency > 0f) {
            facts += "睡眠效率 ${"%.0f".format(Locale.US, snapshot.sleepEfficiency)}%"
        }
        if (snapshot.awakeCount > 0) {
            facts += "夜间觉醒 ${snapshot.awakeCount} 次"
        }
        if (snapshot.recoveryScore > 0) {
            facts += "恢复分 ${snapshot.recoveryScore}"
        }
        if (snapshot.heartRate > 0) {
            facts += "静息心率 ${snapshot.heartRate} bpm"
        }
        if (snapshot.spo2Min > 0) {
            facts += "最低血氧 ${snapshot.spo2Min}%"
        }
        if (snapshot.hrvCurrent > 0 && snapshot.hrvBaseline > 0) {
            facts += "当前 HRV ${snapshot.hrvCurrent} ms，基线 ${snapshot.hrvBaseline} ms"
        }
        return facts.ifEmpty { listOf("当前缺少足够的客观指标，请结合量表与主诉继续补充信息。") }
    }

    private fun buildSuspectedIssues(
        historyText: String,
        snapshot: DoctorMetricSnapshot,
        riskSummary: DoctorRiskSummary
    ): List<DoctorSuspectedIssue> {
        val matched = symptomRuleBook.mapNotNull { rule ->
            val hitCount = rule.keywords.count { keyword -> historyText.contains(keyword, ignoreCase = true) }
            if (hitCount == 0) return@mapNotNull null
            val riskBoost = when (riskSummary.level) {
                DoctorRiskLevel.HIGH -> 8
                DoctorRiskLevel.MEDIUM -> 4
                DoctorRiskLevel.LOW -> 0
            }
            val sleepBoost = if (rule.name.contains("睡眠") && snapshot.sleepMinutes in 1..389) 6 else 0
            val spo2Boost = if (rule.name.contains("呼吸") && snapshot.spo2Min in 1..93) 8 else 0
            val recoveryBoost = if ((rule.name.contains("恢复") || rule.name.contains("训练")) &&
                snapshot.recoveryScore in 1..59
            ) {
                6
            } else {
                0
            }
            val confidence = (58 + hitCount * 8 + riskBoost + sleepBoost + spo2Boost + recoveryBoost)
                .coerceIn(55, 96)
            DoctorSuspectedIssue(
                name = rule.name,
                rationale = rule.rationale,
                confidence = confidence
            )
        }.sortedByDescending { it.confidence }

        if (matched.isNotEmpty()) {
            return matched.take(3)
        }

        val fallbackIssue = when {
            snapshot.sleepMinutes in 1..389 -> DoctorSuspectedIssue(
                name = "睡眠不足相关风险",
                rationale = "当前睡眠时长偏短，建议优先排查睡眠节律、睡前唤醒水平和夜间觉醒因素。",
                confidence = (riskSummary.confidence - 4).coerceAtLeast(56)
            )
            snapshot.recoveryScore in 1..59 -> DoctorSuspectedIssue(
                name = "恢复不足",
                rationale = "恢复分偏低，提示当前负荷与恢复之间存在不平衡。",
                confidence = (riskSummary.confidence - 2).coerceAtLeast(58)
            )
            else -> DoctorSuspectedIssue(
                name = "压力与恢复波动",
                rationale = "当前信息有限，但主诉与恢复波动相关，建议结合量表和后续监测继续评估。",
                confidence = (riskSummary.confidence - 8).coerceAtLeast(55)
            )
        }
        return listOf(fallbackIssue)
    }

    private fun buildNextStepAdvice(
        riskSummary: DoctorRiskSummary,
        redFlags: List<String>,
        suspectedIssues: List<DoctorSuspectedIssue>,
        missingInfo: List<String>
    ): List<String> {
        if (redFlags.isNotEmpty()) {
            return listOf(
                "优先线下就医，必要时直接前往急诊。",
                "就诊时带上近期睡眠、心率、血氧和医检记录。",
                "在医生明确前，不要只依赖放松训练延误处理。"
            )
        }

        val advice = mutableListOf<String>()
        when (riskSummary.level) {
            DoctorRiskLevel.HIGH -> advice += "今天优先降低负荷，避免继续高强度训练或熬夜。"
            DoctorRiskLevel.MEDIUM -> advice += "建议今天安排一段低刺激放松时段，并观察夜间恢复情况。"
            DoctorRiskLevel.LOW -> advice += "可以先按轻量干预执行，并持续记录睡眠与压力变化。"
        }
        suspectedIssues.firstOrNull()?.let { issue ->
            advice += "建议优先按“${issue.name}”方向补充问诊和量表信息。"
        }
        if (missingInfo.isNotEmpty()) {
            advice += "继续补充：${missingInfo.joinToString("、")}。"
        }
        advice += "若症状持续加重或出现新的红旗表现，请尽快线下就医。"
        return advice.take(4)
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private data class SymptomRule(
        val name: String,
        val keywords: List<String>,
        val rationale: String,
        val department: String
    )
}
