package com.example.newstart.service.ai

import com.example.newstart.database.entity.MedicalMetricEntity
import com.example.newstart.network.models.ReportUnderstandingRequest
import com.example.newstart.network.models.ReportUnderstandingData
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.util.MedicalReportDraftFormatter
import com.example.newstart.util.MedicalReportParser
import com.example.newstart.util.ParsedMedicalMetric

object MedicalReportAiService {

    private const val MAX_CLOUD_OCR_TEXT_LENGTH = 10_000
    private const val MAX_CLOUD_OCR_MARKDOWN_LENGTH = 32_000

    fun parse(rawText: String): List<ParsedMedicalMetric> {
        return MedicalReportParser.parse(rawText)
    }

    fun mapToEntity(reportId: String, parsed: ParsedMedicalMetric): MedicalMetricEntity {
        return MedicalReportParser.mapToEntity(reportId, parsed)
    }

    suspend fun enhanceIfAvailable(
        networkRepository: NetworkRepository,
        ocrText: String,
        reportType: String,
        ocrMarkdown: String = ""
    ): ReportUnderstandingData? {
        if (networkRepository.getCurrentSession() == null) {
            return null
        }
        val compactText = buildCompactCloudText(ocrText, ocrMarkdown)
        val compactMarkdown = buildCompactCloudMarkdown(ocrMarkdown)
        if (compactText.isBlank()) {
            return null
        }
        return networkRepository.understandMedicalReport(
            ReportUnderstandingRequest(
                reportType = reportType,
                ocrText = compactText,
                ocrMarkdown = compactMarkdown
            )
        ).getOrNull()
    }

    private fun buildCompactCloudText(ocrText: String, ocrMarkdown: String): String {
        val normalizedText = MedicalReportDraftFormatter.normalizeRawText(ocrText)
        if (normalizedText.length <= MAX_CLOUD_OCR_TEXT_LENGTH) {
            return normalizedText
        }
        val markdownText = MedicalReportDraftFormatter.markdownToPlainText(ocrMarkdown)
        val fallback = markdownText.ifBlank { normalizedText }
        return fallback.take(MAX_CLOUD_OCR_TEXT_LENGTH).trim()
    }

    private fun buildCompactCloudMarkdown(ocrMarkdown: String): String? {
        val normalizedMarkdown = ocrMarkdown
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
            .take(MAX_CLOUD_OCR_MARKDOWN_LENGTH)
            .trim()
        return normalizedMarkdown.ifBlank { null }
    }
}
