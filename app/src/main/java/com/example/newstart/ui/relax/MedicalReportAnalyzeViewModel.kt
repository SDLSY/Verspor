package com.example.newstart.ui.relax

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.R
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.InterventionTaskEntity
import com.example.newstart.database.entity.MedicalReportEntity
import com.example.newstart.network.models.InterventionTaskUpsertRequest
import com.example.newstart.network.models.MedicalMetricUpsertItem
import com.example.newstart.network.models.MedicalMetricUpsertRequest
import com.example.newstart.network.models.ReportUnderstandingData
import com.example.newstart.repository.InterventionRepository
import com.example.newstart.repository.InterventionTaskSourceType
import com.example.newstart.repository.InterventionTaskStatus
import com.example.newstart.repository.MedicalReportRepository
import com.example.newstart.repository.NetworkRepository
import com.example.newstart.util.MedicalReportDraftFormatter
import com.example.newstart.util.ParsedMedicalMetric
import com.example.newstart.util.PerformanceTelemetry
import com.example.newstart.service.ai.MedicalReportAiService
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class MedicalReportAnalyzeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val db = AppDatabase.getDatabase(application)
    private val medicalReportRepository = MedicalReportRepository(
        reportDao = db.medicalReportDao(),
        metricDao = db.medicalMetricDao()
    )
    private val interventionRepository = InterventionRepository(
        taskDao = db.interventionTaskDao(),
        executionDao = db.interventionExecutionDao()
    )
    private val networkRepository = NetworkRepository()

    private val _uiState = MutableLiveData(
        MedicalReportAnalyzeUiState(
            statusText = app.getString(R.string.medical_report_subtitle),
            metricsText = app.getString(R.string.medical_report_metric_empty),
            readableReportText = app.getString(R.string.medical_report_readable_empty),
            rawOcrText = "",
            editableOcrText = ""
        )
    )
    val uiState: LiveData<MedicalReportAnalyzeUiState> = _uiState

    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    private var draftImageTag: String = ""
    private var draftRawOcrText: String = ""
    private var draftMetrics: List<ParsedMedicalMetric> = emptyList()
    private var draftRiskLevel: String = "LOW"
    private var draftReportType: String = "PHOTO"

    fun consumeToast() {
        _toastEvent.value = null
    }

    fun onCaptureStarted() {
        _uiState.value = (_uiState.value ?: MedicalReportAnalyzeUiState()).copy(
            isAnalyzing = true,
            statusText = app.getString(R.string.medical_report_analyzing)
        )
    }

    fun onImportStarted(statusText: String) {
        _uiState.value = (_uiState.value ?: MedicalReportAnalyzeUiState()).copy(
            isAnalyzing = true,
            statusText = statusText
        )
    }

    fun onCaptureFailed(message: String) {
        _uiState.value = (_uiState.value ?: MedicalReportAnalyzeUiState()).copy(
            isAnalyzing = false,
            statusText = message
        )
    }

    fun onOcrTextReady(ocrText: String, imageTag: String, reportType: String = "PHOTO") {
        if (ocrText.isBlank()) {
            _uiState.value = (_uiState.value ?: MedicalReportAnalyzeUiState()).copy(
                isAnalyzing = false,
                statusText = app.getString(R.string.medical_report_ocr_empty),
                canConfirm = false
            )
            return
        }
        draftImageTag = imageTag
        draftReportType = reportType
        draftRawOcrText = MedicalReportDraftFormatter.normalizeRawText(ocrText)
        parseDraft(draftRawOcrText, analyzing = false)
    }

    fun reparseDraft(editedText: String) {
        if (editedText.isBlank()) {
            _toastEvent.value = app.getString(R.string.medical_report_ocr_empty)
            return
        }
        parseDraft(
            textForParsing = MedicalReportDraftFormatter.toParsingText(editedText, draftRawOcrText),
            analyzing = false,
            editableDraftText = editedText
        )
    }

    fun confirmDraft(editedText: String) {
        if (editedText.isBlank()) {
            _toastEvent.value = app.getString(R.string.medical_report_confirm_required)
            return
        }

        viewModelScope.launch {
            val parseStart = PerformanceTelemetry.nowElapsedMs()
            val parsingText = MedicalReportDraftFormatter.toParsingText(editedText, draftRawOcrText)
            val parsedMetrics = MedicalReportAiService.parse(parsingText)
            PerformanceTelemetry.recordDuration(
                metric = "ocr_parse_latency",
                startElapsedMs = parseStart,
                attributes = mapOf("phase" to "confirm")
            )
            val cloudEnhanced = MedicalReportAiService.enhanceIfAvailable(
                networkRepository = networkRepository,
                ocrText = parsingText,
                reportType = draftReportType
            )
            val finalMetrics = cloudEnhanced?.metrics
                ?.map { item -> item.toParsedMedicalMetric() }
                ?.takeIf { it.isNotEmpty() }
                ?: parsedMetrics
            draftMetrics = finalMetrics
            draftRiskLevel = cloudEnhanced?.riskLevel ?: computeRiskLevel(finalMetrics)

            val now = System.currentTimeMillis()
            val report = MedicalReportEntity(
                reportDate = now,
                reportType = draftReportType,
                imageUri = draftImageTag.ifBlank { "camera_preview_$now" },
                ocrTextDigest = parsingText.take(4000),
                parseStatus = "PARSED",
                riskLevel = draftRiskLevel,
                createdAt = now
            )
            medicalReportRepository.saveReport(report)

            val metricEntities = finalMetrics.map { MedicalReportAiService.mapToEntity(report.id, it) }
            if (metricEntities.isNotEmpty()) {
                medicalReportRepository.saveMetrics(metricEntities)
            }

            val tasks = mapMetricsToTasks(finalMetrics, now)
            if (tasks.isNotEmpty()) {
                interventionRepository.upsertTasks(tasks)
            }

            syncToCloudIfAvailable(report, finalMetrics, tasks)

            _uiState.postValue(
                (_uiState.value ?: MedicalReportAnalyzeUiState()).copy(
                    isAnalyzing = false,
                    statusText = buildStatusText(draftRiskLevel, finalMetrics.count { it.isAbnormal }, tasks.size),
                    metricsText = buildMetricsText(finalMetrics),
                    readableReportText = buildReadableReportText(cloudEnhanced, finalMetrics),
                    rawOcrText = draftRawOcrText,
                    editableOcrText = editedText,
                    canConfirm = true
                )
            )
            _toastEvent.postValue(app.getString(R.string.medical_report_commit_done))
        }
    }

    private fun parseDraft(
        textForParsing: String,
        analyzing: Boolean,
        editableDraftText: String? = null
    ) {
        viewModelScope.launch {
            val parseStart = PerformanceTelemetry.nowElapsedMs()
            val parsedMetrics = MedicalReportAiService.parse(textForParsing)
            val cloudEnhanced = MedicalReportAiService.enhanceIfAvailable(
                networkRepository = networkRepository,
                ocrText = textForParsing,
                reportType = draftReportType
            )
            PerformanceTelemetry.recordDuration(
                metric = "ocr_parse_latency",
                startElapsedMs = parseStart,
                attributes = mapOf("phase" to "draft")
            )
            val finalMetrics = cloudEnhanced?.metrics
                ?.map { item -> item.toParsedMedicalMetric() }
                ?.takeIf { it.isNotEmpty() }
                ?: parsedMetrics
            draftMetrics = finalMetrics
            draftRiskLevel = cloudEnhanced?.riskLevel ?: computeRiskLevel(finalMetrics)
            val abnormalCount = finalMetrics.count { it.isAbnormal }

            _uiState.postValue(
                MedicalReportAnalyzeUiState(
                    isAnalyzing = analyzing,
                    statusText = buildStatusText(draftRiskLevel, abnormalCount, 0),
                    metricsText = buildMetricsText(finalMetrics),
                    readableReportText = buildReadableReportText(cloudEnhanced, finalMetrics),
                    rawOcrText = draftRawOcrText,
                    editableOcrText = editableDraftText
                        ?: MedicalReportDraftFormatter.buildEditableDraft(textForParsing, finalMetrics),
                    canConfirm = finalMetrics.isNotEmpty()
                )
            )
        }
    }

    private fun buildStatusText(riskLevel: String, abnormalCount: Int, taskCount: Int): String {
        val line1 = app.getString(R.string.medical_report_result_prefix, riskLevel, abnormalCount)
        val line2 = app.getString(R.string.medical_report_task_created_prefix, taskCount)
        return "$line1\n$line2"
    }

    private fun buildMetricsText(metrics: List<ParsedMedicalMetric>): String {
        if (metrics.isEmpty()) {
            return app.getString(R.string.medical_report_metric_empty)
        }
        return metrics.joinToString(separator = "\n") { metric ->
            val abnormalFlag = if (metric.isAbnormal) " [ABNORMAL]" else ""
            val range = when {
                metric.refLow != null && metric.refHigh != null ->
                    "(${metric.refLow}-${metric.refHigh} ${metric.unit})"
                metric.refHigh != null -> "(<=${metric.refHigh} ${metric.unit})"
                metric.refLow != null -> "(>=${metric.refLow} ${metric.unit})"
                else -> ""
            }
            String.format(
                Locale.getDefault(),
                "%s: %.2f %s %s%s",
                metric.metricName,
                metric.value,
                metric.unit,
                range,
                abnormalFlag
            ).trim()
        }
    }

    private fun buildReadableReportText(
        cloudEnhanced: ReportUnderstandingData?,
        metrics: List<ParsedMedicalMetric>
    ): String {
        val cloudReadable = cloudEnhanced?.readableReport?.trim().orEmpty()
        if (cloudReadable.isNotBlank()) {
            return cloudReadable
        }
        return MedicalReportDraftFormatter.buildReadableReport(
            riskLevel = draftRiskLevel,
            metrics = metrics,
            cloudSummary = cloudEnhanced?.summary,
            rawOcrText = draftRawOcrText
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

    private fun mapMetricsToTasks(metrics: List<ParsedMedicalMetric>, now: Long): List<InterventionTaskEntity> {
        val abnormalMetrics = metrics.filter { it.isAbnormal }
        if (abnormalMetrics.isEmpty()) {
            return emptyList()
        }

        return abnormalMetrics.map { metric ->
            val mapping = when (metric.metricCode) {
                "GLU", "HBA1C" -> TaskMapping("ABDOMEN", "LOW_ACTIVITY", 900)
                "TC", "LDL", "TG" -> TaskMapping("CHEST", "LOW_ACTIVITY", 900)
                "UA" -> TaskMapping("LIMB", "LOW_ACTIVITY", 900)
                "SBP", "DBP" -> TaskMapping("HEAD", "BREATH_4_6", 300)
                else -> TaskMapping("LIMB", "LOW_ACTIVITY", 600)
            }
            InterventionTaskEntity(
                date = startOfDay(now),
                sourceType = InterventionTaskSourceType.MEDICAL_REPORT.name,
                triggerReason = "Medical metric abnormal: ${metric.metricName}",
                bodyZone = mapping.bodyZone,
                protocolType = mapping.protocolType,
                durationSec = mapping.durationSec,
                plannedAt = now,
                status = InterventionTaskStatus.PENDING.name,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    private suspend fun syncToCloudIfAvailable(
        report: MedicalReportEntity,
        metrics: List<ParsedMedicalMetric>,
        tasks: List<InterventionTaskEntity>
    ) {
        if (networkRepository.getCurrentSession() == null) {
            return
        }
        networkRepository.upsertReportMetrics(
            MedicalMetricUpsertRequest(
                reportId = report.id,
                reportDate = report.reportDate,
                reportType = report.reportType,
                riskLevel = report.riskLevel,
                metrics = metrics.map { metric ->
                    MedicalMetricUpsertItem(
                        metricCode = metric.metricCode,
                        metricName = metric.metricName,
                        metricValue = metric.value,
                        unit = metric.unit,
                        refLow = metric.refLow,
                        refHigh = metric.refHigh,
                        isAbnormal = metric.isAbnormal,
                        confidence = metric.confidence
                    )
                }
            )
        )
        tasks.forEach { task ->
            networkRepository.upsertInterventionTask(
                InterventionTaskUpsertRequest(
                    taskId = task.id,
                    date = task.date,
                    sourceType = task.sourceType,
                    triggerReason = task.triggerReason,
                    bodyZone = task.bodyZone,
                    protocolType = task.protocolType,
                    durationSec = task.durationSec,
                    plannedAt = task.plannedAt,
                    status = task.status
                )
            )
        }
    }

    private fun startOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

private fun MedicalMetricUpsertItem.toParsedMedicalMetric(): ParsedMedicalMetric {
    return ParsedMedicalMetric(
        metricCode = metricCode,
        metricName = metricName,
        value = metricValue,
        unit = unit,
        refLow = refLow,
        refHigh = refHigh,
        isAbnormal = isAbnormal,
        confidence = confidence
    )
}

private data class TaskMapping(
    val bodyZone: String,
    val protocolType: String,
    val durationSec: Int
)

data class MedicalReportAnalyzeUiState(
    val isAnalyzing: Boolean = false,
    val statusText: String = "",
    val metricsText: String = "",
    val readableReportText: String = "",
    val rawOcrText: String = "",
    val editableOcrText: String = "",
    val canConfirm: Boolean = false
)
