package com.example.newstart.service.ai

import com.example.newstart.database.entity.MedicalMetricEntity
import com.example.newstart.network.models.ReportUnderstandingRequest
import com.example.newstart.network.models.ReportUnderstandingData
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.util.MedicalReportParser
import com.example.newstart.util.ParsedMedicalMetric

object MedicalReportAiService {

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
        return networkRepository.understandMedicalReport(
            ReportUnderstandingRequest(
                reportType = reportType,
                ocrText = ocrText,
                ocrMarkdown = ocrMarkdown.ifBlank { null }
            )
        ).getOrNull()
    }
}
