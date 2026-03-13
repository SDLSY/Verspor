package com.example.newstart.repository

import com.example.newstart.database.dao.MedicalMetricDao
import com.example.newstart.database.dao.MedicalReportDao
import com.example.newstart.database.entity.MedicalMetricEntity
import com.example.newstart.database.entity.MedicalReportEntity
import com.example.newstart.util.MedicalReportParser
import com.example.newstart.util.ParsedMedicalMetric
import kotlinx.coroutines.flow.Flow

class MedicalReportRepository(
    private val reportDao: MedicalReportDao,
    private val metricDao: MedicalMetricDao
) {

    fun getLatestReportFlow(): Flow<MedicalReportEntity?> = reportDao.getLatestFlow()

    suspend fun getLatestReport(): MedicalReportEntity? = reportDao.getLatest()

    suspend fun saveReport(report: MedicalReportEntity) {
        reportDao.upsert(report)
    }

    suspend fun saveMetrics(metrics: List<MedicalMetricEntity>) {
        metricDao.upsertAll(metrics)
    }

    suspend fun getMetricsByReport(reportId: String): List<MedicalMetricEntity> {
        return metricDao.getByReportOnce(reportId)
    }

    suspend fun getAbnormalMetrics(): List<MedicalMetricEntity> = metricDao.getAbnormalMetrics()

    suspend fun parseAndStore(
        report: MedicalReportEntity,
        ocrText: String
    ): ParseReportResult {
        saveReport(report)

        val parsed = MedicalReportParser.parse(ocrText)
        val entities = parsed.map { MedicalReportParser.mapToEntity(report.id, it) }
        if (entities.isNotEmpty()) {
            saveMetrics(entities)
        }

        return ParseReportResult(
            parsedMetrics = parsed,
            abnormalCount = parsed.count { it.isAbnormal },
            riskLevel = computeRiskLevel(parsed)
        )
    }

    private fun computeRiskLevel(metrics: List<ParsedMedicalMetric>): String {
        val abnormal = metrics.count { it.isAbnormal }
        return when {
            abnormal >= 3 -> "HIGH"
            abnormal >= 1 -> "MEDIUM"
            else -> "LOW"
        }
    }
}

data class ParseReportResult(
    val parsedMetrics: List<ParsedMedicalMetric>,
    val abnormalCount: Int,
    val riskLevel: String
)

