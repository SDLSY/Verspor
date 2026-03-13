package com.example.newstart.util

import com.example.newstart.database.entity.MedicalMetricEntity
import java.util.Locale

data class ParsedMedicalMetric(
    val metricCode: String,
    val metricName: String,
    val value: Float,
    val unit: String,
    val refLow: Float?,
    val refHigh: Float?,
    val isAbnormal: Boolean,
    val confidence: Float
)

object MedicalReportParser {

    private val numberPattern = Regex("""([0-9]+(?:\.[0-9]+)?)""")
    private val bpPattern = Regex("""([0-9]{2,3})\s*/\s*([0-9]{2,3})""")

    private data class MetricRule(
        val code: String,
        val name: String,
        val keywords: List<String>,
        val unit: String,
        val low: Float?,
        val high: Float?,
        val confidence: Float = 0.82f
    )

    private const val CN_FASTING_GLUCOSE = "\u7A7A\u8179\u8840\u7CD6"
    private const val CN_HBA1C = "\u7CD6\u5316\u8840\u7EA2\u86CB\u767D"
    private const val CN_TOTAL_CHOLESTEROL = "\u603B\u80C6\u56FA\u9187"
    private const val CN_LDL = "\u4F4E\u5BC6\u5EA6\u8102\u86CB\u767D"
    private const val CN_TRIGLYCERIDE = "\u7518\u6CB9\u4E09\u916F"
    private const val CN_URIC_ACID = "\u5C3F\u9178"
    private const val CN_BLOOD_PRESSURE = "\u8840\u538B"
    private const val CN_SYSTOLIC = "\u6536\u7F29\u538B"
    private const val CN_DIASTOLIC = "\u8212\u5F20\u538B"

    private val metricRules = listOf(
        MetricRule(
            code = "GLU",
            name = "Fasting glucose",
            keywords = listOf(CN_FASTING_GLUCOSE, "glucose", "fpg"),
            unit = "mmol/L",
            low = 3.9f,
            high = 6.1f
        ),
        MetricRule(
            code = "HBA1C",
            name = "HbA1c",
            keywords = listOf(CN_HBA1C, "hba1c"),
            unit = "%",
            low = 4.0f,
            high = 6.0f
        ),
        MetricRule(
            code = "TC",
            name = "Total cholesterol",
            keywords = listOf(CN_TOTAL_CHOLESTEROL, "tcho", "tc"),
            unit = "mmol/L",
            low = null,
            high = 5.2f
        ),
        MetricRule(
            code = "LDL",
            name = "LDL-C",
            keywords = listOf(CN_LDL, "ldl", "ldl-c"),
            unit = "mmol/L",
            low = null,
            high = 3.4f
        ),
        MetricRule(
            code = "TG",
            name = "Triglyceride",
            keywords = listOf(CN_TRIGLYCERIDE, "tg", "triglyceride"),
            unit = "mmol/L",
            low = null,
            high = 1.7f
        ),
        MetricRule(
            code = "UA",
            name = "Uric acid",
            keywords = listOf(CN_URIC_ACID, "ua", "uric"),
            unit = "umol/L",
            low = 210f,
            high = 420f
        )
    )

    fun parse(rawText: String): List<ParsedMedicalMetric> {
        val lines = rawText
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val parsed = mutableListOf<ParsedMedicalMetric>()
        parsed += parseBloodPressure(lines)

        metricRules.forEach { rule ->
            val line = lines.firstOrNull { line ->
                val lower = line.lowercase(Locale.ROOT)
                rule.keywords.any { keyword -> lower.contains(keyword.lowercase(Locale.ROOT)) }
            } ?: return@forEach

            val value = numberPattern.find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: return@forEach
            val isAbnormal = when {
                rule.low != null && value < rule.low -> true
                rule.high != null && value > rule.high -> true
                else -> false
            }
            parsed += ParsedMedicalMetric(
                metricCode = rule.code,
                metricName = rule.name,
                value = value,
                unit = rule.unit,
                refLow = rule.low,
                refHigh = rule.high,
                isAbnormal = isAbnormal,
                confidence = rule.confidence
            )
        }

        return parsed.distinctBy { it.metricCode }
    }

    private fun parseBloodPressure(lines: List<String>): List<ParsedMedicalMetric> {
        val bpLine = lines.firstOrNull { line ->
            val lower = line.lowercase(Locale.ROOT)
            lower.contains(CN_BLOOD_PRESSURE.lowercase(Locale.ROOT)) ||
                lower.contains(CN_SYSTOLIC.lowercase(Locale.ROOT)) ||
                lower.contains(CN_DIASTOLIC.lowercase(Locale.ROOT)) ||
                lower.contains("blood pressure") ||
                lower.contains("bp")
        } ?: lines.firstOrNull { bpPattern.containsMatchIn(it) } ?: return emptyList()

        val match = bpPattern.find(bpLine) ?: return emptyList()
        val sbp = match.groupValues[1].toFloatOrNull() ?: return emptyList()
        val dbp = match.groupValues[2].toFloatOrNull() ?: return emptyList()

        return listOf(
            ParsedMedicalMetric(
                metricCode = "SBP",
                metricName = "Systolic pressure",
                value = sbp,
                unit = "mmHg",
                refLow = 90f,
                refHigh = 140f,
                isAbnormal = sbp < 90f || sbp > 140f,
                confidence = 0.9f
            ),
            ParsedMedicalMetric(
                metricCode = "DBP",
                metricName = "Diastolic pressure",
                value = dbp,
                unit = "mmHg",
                refLow = 60f,
                refHigh = 90f,
                isAbnormal = dbp < 60f || dbp > 90f,
                confidence = 0.9f
            )
        )
    }

    fun mapToEntity(reportId: String, parsed: ParsedMedicalMetric): MedicalMetricEntity {
        return MedicalMetricEntity(
            reportId = reportId,
            metricCode = parsed.metricCode,
            metricName = parsed.metricName,
            metricValue = parsed.value,
            unit = parsed.unit,
            refLow = parsed.refLow,
            refHigh = parsed.refHigh,
            isAbnormal = parsed.isAbnormal,
            confidence = parsed.confidence
        )
    }
}
