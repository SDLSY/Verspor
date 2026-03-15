package com.example.newstart.util

import java.util.Locale

object MedicalReportDraftFormatter {

    private val multiSpaceRegex = Regex("[\\t\\u00A0 ]+")
    private val markdownTableSeparatorRegex = Regex("^\\s*\\|?(?:\\s*:?-{3,}:?\\s*\\|)+\\s*$")
    private val markdownHeadingRegex = Regex("^#{1,6}\\s*")
    private val htmlBreakRegex = Regex("(?i)<\\s*br\\s*/?\\s*>")
    private val htmlClosingBlockRegex = Regex("(?i)</\\s*(p|div|li|tr|td|th|h[1-6]|ul|ol|table|section|article)\\s*>")
    private val htmlOpeningListItemRegex = Regex("(?i)<\\s*li\\b[^>]*>")
    private val htmlTagRegex = Regex("(?is)<[^>]+>")
    private val decimalHtmlEntityRegex = Regex("&#(\\d+);")
    private val hexHtmlEntityRegex = Regex("&#x([0-9a-fA-F]+);")

    fun normalizeRawText(rawText: String): String {
        return rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.replace(multiSpaceRegex, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "\n")
            .trim()
    }

    fun markdownToPlainText(markdown: String): String {
        if (markdown.isBlank()) return ""
        return cleanOcrMarkup(markdown)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .mapNotNull { rawLine ->
                val line = rawLine.trim()
                when {
                    line.isBlank() -> null
                    markdownTableSeparatorRegex.matches(line) -> null
                    else -> line
                        .replace(markdownHeadingRegex, "")
                        .replace("|", " ")
                        .replace("**", "")
                        .replace("*", "")
                        .replace("`", "")
                        .replace(multiSpaceRegex, " ")
                        .trim()
                        .takeIf { it.isNotBlank() }
                }
            }
            .joinToString(separator = "\n")
            .let(::normalizeRawText)
    }

    fun cleanOcrMarkup(content: String): String {
        if (content.isBlank()) return ""
        return decodeHtmlEntities(content)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(htmlBreakRegex, "\n")
            .replace(htmlOpeningListItemRegex, "\n- ")
            .replace(htmlClosingBlockRegex, "\n")
            .replace(htmlTagRegex, " ")
            .replace(multiSpaceRegex, " ")
            .lines()
            .map { it.trim() }
            .joinToString(separator = "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun buildEditableDraft(rawText: String, metrics: List<ParsedMedicalMetric>): String {
        val normalizedRawText = normalizeRawText(rawText)
        if (metrics.isEmpty()) {
            return normalizedRawText
        }

        val lines = mutableListOf<String>()
        lines += "请逐行核对以下草稿，直接修改项目名、数值、单位或参考范围。"
        lines += ""
        lines += buildMetricLines(metrics)
        return lines.joinToString(separator = "\n").trim()
    }

    fun buildReadableReport(
        riskLevel: String,
        metrics: List<ParsedMedicalMetric>,
        cloudSummary: String? = null,
        rawOcrText: String = ""
    ): String {
        val lines = mutableListOf<String>()
        val normalizedSummary = cloudSummary.orEmpty().trim()
        if (normalizedSummary.isNotBlank()) {
            lines += normalizedSummary
            lines += ""
        }
        lines += "风险等级：${readableRiskLabel(riskLevel)}"
        lines += "异常项：${metrics.count { it.isAbnormal }} 项"
        if (metrics.isNotEmpty()) {
            lines += ""
            lines += "关键指标"
            lines += buildMetricLines(metrics).mapIndexed { index, line -> "${index + 1}. $line" }
        } else {
            val rawSummary = summarizeRawText(rawOcrText)
            lines += ""
            lines += "当前结论"
            lines += "已识别到报告文本，但暂未稳定提取出结构化指标。"
            if (rawSummary.isNotBlank()) {
                lines += ""
                lines += "OCR 摘要"
                lines += rawSummary
            }
            lines += ""
            lines += "建议下一步"
            lines += "1. 检查下方 OCR 原文是否有错字、漏字或单位缺失。"
            lines += "2. 如有问题，可在订正区直接修改后重新解析。"
            lines += "3. 若报告拍摄不完整，建议重新拍照或导入更清晰的文件。"
        }
        return lines.joinToString(separator = "\n").trim()
    }

    fun toParsingText(editableDraft: String, fallbackRawText: String): String {
        val normalizedDraft = normalizeRawText(editableDraft)
        val cleaned = normalizedDraft
            .lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isBlank() -> null
                    trimmed.startsWith("请逐行核对") -> null
                    else -> trimmed.removePrefix("-").trim()
                }
            }
            .joinToString(separator = "\n")
            .trim()
        return if (cleaned.isNotBlank()) cleaned else normalizeRawText(fallbackRawText)
    }

    private fun buildMetricLines(metrics: List<ParsedMedicalMetric>): List<String> {
        val byCode = metrics.associateBy { it.metricCode }
        val lines = mutableListOf<String>()

        val systolic = byCode["SBP"]
        val diastolic = byCode["DBP"]
        if (systolic != null && diastolic != null) {
            lines += "血压 ${formatNumber(systolic.value)}/${formatNumber(diastolic.value)} mmHg 参考范围 90-140/60-90 mmHg"
        }

        metrics
            .filterNot { it.metricCode == "SBP" || it.metricCode == "DBP" }
            .sortedBy { metricSortOrder(it.metricCode) }
            .forEach { metric ->
                val label = metricLabel(metric)
                val abnormalSuffix = if (metric.isAbnormal) "（异常）" else ""
                val range = when {
                    metric.refLow != null && metric.refHigh != null ->
                        "参考范围 ${formatNumber(metric.refLow)}-${formatNumber(metric.refHigh)} ${metric.unit}"
                    metric.refHigh != null ->
                        "参考范围 <=${formatNumber(metric.refHigh)} ${metric.unit}"
                    metric.refLow != null ->
                        "参考范围 >=${formatNumber(metric.refLow)} ${metric.unit}"
                    else -> ""
                }
                lines += listOfNotNull(
                    "$label: ${formatNumber(metric.value)} ${metric.unit}$abnormalSuffix".trim(),
                    range.takeIf { it.isNotBlank() }
                ).joinToString(separator = " ")
            }
        return lines
    }

    private fun metricLabel(metric: ParsedMedicalMetric): String {
        return when (metric.metricCode) {
            "GLU" -> "空腹血糖"
            "HBA1C" -> "糖化血红蛋白"
            "TC" -> "总胆固醇"
            "LDL" -> "低密度脂蛋白"
            "TG" -> "甘油三酯"
            "UA" -> "尿酸"
            "SBP", "DBP" -> "血压"
            else -> metric.metricName
        }
    }

    private fun metricSortOrder(metricCode: String): Int {
        return when (metricCode) {
            "GLU" -> 0
            "HBA1C" -> 1
            "TC" -> 2
            "LDL" -> 3
            "TG" -> 4
            "UA" -> 5
            else -> 99
        }
    }

    private fun readableRiskLabel(riskLevel: String): String {
        return when (riskLevel.uppercase(Locale.ROOT)) {
            "HIGH" -> "高"
            "MEDIUM" -> "中"
            "LOW" -> "低"
            else -> riskLevel
        }
    }

    private fun summarizeRawText(rawOcrText: String): String {
        return normalizeRawText(rawOcrText)
            .lines()
            .filter { it.isNotBlank() }
            .take(4)
            .joinToString(separator = "\n")
            .take(220)
            .trim()
    }

    private fun formatNumber(value: Float): String {
        return if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }

    private fun decodeHtmlEntities(input: String): String {
        val namedDecoded = input
            .replace("&nbsp;", " ")
            .replace("&ensp;", " ")
            .replace("&emsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        val decimalDecoded = decimalHtmlEntityRegex.replace(namedDecoded) { matchResult ->
            matchResult.groupValues[1].toIntOrNull()?.let { codePoint ->
                codePointToStringOrNull(codePoint)
            } ?: matchResult.value
        }
        return hexHtmlEntityRegex.replace(decimalDecoded) { matchResult ->
            matchResult.groupValues[1].toIntOrNull(16)?.let { codePoint ->
                codePointToStringOrNull(codePoint)
            } ?: matchResult.value
        }
    }

    private fun codePointToStringOrNull(codePoint: Int): String? {
        return runCatching { String(Character.toChars(codePoint)) }.getOrNull()
    }
}
