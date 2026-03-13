package com.example.newstart.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalReportDraftFormatterTest {

    @Test
    fun buildEditableDraft_formatsRecognizedMetrics_forManualCorrection() {
        val rawText = """
            空腹血糖 6.8 mmol/L
            糖化血红蛋白 5.7 %
            血压 125/82 mmHg
        """.trimIndent()

        val metrics = MedicalReportParser.parse(rawText)
        val draft = MedicalReportDraftFormatter.buildEditableDraft(rawText, metrics)

        assertTrue(draft.contains("空腹血糖: 6.8 mmol/L"))
        assertTrue(draft.contains("糖化血红蛋白: 5.7 %"))
        assertTrue(draft.contains("血压 125/82 mmHg"))
    }

    @Test
    fun buildReadableReport_prefersCloudSummary_andKeepsMetricsReadable() {
        val rawText = """
            空腹血糖 6.8 mmol/L
            糖化血红蛋白 5.7 %
            血压 125/82 mmHg
        """.trimIndent()

        val metrics = MedicalReportParser.parse(rawText)
        val report = MedicalReportDraftFormatter.buildReadableReport(
            riskLevel = "MEDIUM",
            metrics = metrics,
            cloudSummary = "本次报告提示血糖相关指标需要重点关注。"
        )

        assertTrue(report.contains("本次报告提示血糖相关指标需要重点关注。"))
        assertTrue(report.contains("风险等级：中"))
        assertTrue(report.contains("关键指标"))
    }

    @Test
    fun toParsingText_keepsMetricLines_parseable() {
        val editableDraft = """
            请逐行核对以下草稿，直接修改项目名、数值、单位或参考范围。
            空腹血糖: 6.8 mmol/L 参考范围 3.9-6.1 mmol/L
            糖化血红蛋白: 5.7 % 参考范围 4-6 %
            血压 125/82 mmHg 参考范围 90-140/60-90 mmHg
        """.trimIndent()

        val parsingText = MedicalReportDraftFormatter.toParsingText(editableDraft, "")
        val metrics = MedicalReportParser.parse(parsingText)

        assertEquals(4, metrics.size)
        assertTrue(parsingText.contains("空腹血糖"))
        assertTrue(parsingText.contains("血压"))
    }
}
