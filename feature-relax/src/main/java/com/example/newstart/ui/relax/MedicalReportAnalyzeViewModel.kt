package com.example.newstart.ui.relax

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.core.common.ui.cards.ActionGroupCardModel
import com.example.newstart.core.common.ui.cards.CardTone
import com.example.newstart.core.common.ui.cards.EvidenceCardModel
import com.example.newstart.core.common.ui.cards.MetricRangeCardModel
import com.example.newstart.core.common.ui.cards.RiskSummaryCardModel
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
import kotlin.math.roundToInt

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
            editableOcrText = "",
            summaryCards = emptyList(),
            metricCards = emptyList(),
            riskSummaryCard = null,
            actionCards = emptyList()
        )
    )
    val uiState: LiveData<MedicalReportAnalyzeUiState> = _uiState

    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    private var draftImageTag: String = ""
    private var draftRawOcrText: String = ""
    private var draftOcrMarkdown: String = ""
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

    fun onOcrTextReady(
        ocrText: String,
        imageTag: String,
        reportType: String = "PHOTO",
        ocrMarkdown: String = ""
    ) {
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
        draftOcrMarkdown = ocrMarkdown.trim()
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
            val hadCloudSession = networkRepository.getCurrentSession() != null
            PerformanceTelemetry.recordDuration(
                metric = "ocr_parse_latency",
                startElapsedMs = parseStart,
                attributes = mapOf("phase" to "confirm")
            )
            val cloudEnhanced = MedicalReportAiService.enhanceIfAvailable(
                networkRepository = networkRepository,
                ocrText = parsingText,
                reportType = draftReportType,
                ocrMarkdown = draftOcrMarkdown
            )
            notifyCloudLoginExpiredIfNeeded(hadCloudSession, cloudEnhanced)
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
                buildAnalyzeUiState(
                    isAnalyzing = false,
                    metrics = finalMetrics,
                    cloudEnhanced = cloudEnhanced,
                    editableText = editedText,
                    taskCount = tasks.size,
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
            val hadCloudSession = networkRepository.getCurrentSession() != null
            val cloudEnhanced = MedicalReportAiService.enhanceIfAvailable(
                networkRepository = networkRepository,
                ocrText = textForParsing,
                reportType = draftReportType,
                ocrMarkdown = draftOcrMarkdown
            )
            notifyCloudLoginExpiredIfNeeded(hadCloudSession, cloudEnhanced)
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
            _uiState.postValue(
                buildAnalyzeUiState(
                    isAnalyzing = analyzing,
                    metrics = finalMetrics,
                    cloudEnhanced = cloudEnhanced,
                    editableText = editableDraftText
                        ?: MedicalReportDraftFormatter.buildEditableDraft(textForParsing, finalMetrics),
                    taskCount = 0,
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

    private fun notifyCloudLoginExpiredIfNeeded(
        hadCloudSession: Boolean,
        cloudEnhanced: ReportUnderstandingData?
    ) {
        if (!hadCloudSession || cloudEnhanced != null) {
            return
        }
        if (networkRepository.getCurrentSession() == null) {
            _toastEvent.postValue("云端登录已失效，请重新登录后再生成可读报告")
        }
    }

    private fun buildAnalyzeUiState(
        isAnalyzing: Boolean,
        metrics: List<ParsedMedicalMetric>,
        cloudEnhanced: ReportUnderstandingData?,
        editableText: String,
        taskCount: Int,
        canConfirm: Boolean
    ): MedicalReportAnalyzeUiState {
        return MedicalReportAnalyzeUiState(
            isAnalyzing = isAnalyzing,
            statusText = buildStatusText(draftRiskLevel, metrics.count { it.isAbnormal }, taskCount),
            metricsText = buildMetricsText(metrics),
            readableReportText = buildReadableReportText(cloudEnhanced, metrics),
            rawOcrText = draftRawOcrText,
            editableOcrText = editableText,
            canConfirm = canConfirm,
            summaryCards = buildSummaryEvidenceCards(metrics, cloudEnhanced),
            metricCards = buildMetricCards(metrics),
            riskSummaryCard = buildRiskSummaryCard(metrics, cloudEnhanced),
            actionCards = buildActionCards(metrics)
        )
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

    private fun buildSummaryEvidenceCards(
        metrics: List<ParsedMedicalMetric>,
        cloudEnhanced: ReportUnderstandingData?
    ): List<EvidenceCardModel> {
        val abnormalCount = metrics.count { it.isAbnormal }
        val sourceTone = if (cloudEnhanced != null) CardTone.INFO else CardTone.WARNING
        return buildList {
            add(
                EvidenceCardModel(
                    title = "报告来源",
                    value = when (draftReportType) {
                        "PDF" -> "PDF 导入"
                        "IMAGE_FILE" -> "图片导入"
                        else -> "拍照识别"
                    },
                    note = if (draftOcrMarkdown.isNotBlank()) "已保留结构化版式信息" else "当前主要使用 OCR 文本整理",
                    badgeText = if (draftOcrMarkdown.isNotBlank()) "Markdown" else "文本模式",
                    tone = CardTone.INFO
                )
            )
            add(
                EvidenceCardModel(
                    title = "结构化指标",
                    value = "${metrics.size} 项",
                    note = if (metrics.isEmpty()) "尚未形成稳定指标，请优先校对 OCR 文本。" else "已识别异常 ${abnormalCount} 项，可直接进入解释卡阅读。",
                    badgeText = "异常 $abnormalCount",
                    tone = if (abnormalCount > 0) CardTone.WARNING else CardTone.POSITIVE
                )
            )
            add(
                EvidenceCardModel(
                    title = "整理方式",
                    value = if (cloudEnhanced != null) "云端结构化整理" else "本地规则整理",
                    note = cloudEnhanced?.summary?.takeIf { it.isNotBlank() }
                        ?: "未命中云端增强时，会退回本地模板与规则解释。",
                    badgeText = if (cloudEnhanced != null) "已增强" else "本地回退",
                    tone = sourceTone
                )
            )
        }
    }

    private fun buildMetricCards(metrics: List<ParsedMedicalMetric>): List<MetricRangeCardModel> {
        return metrics.map { metric ->
            val rangeText = metricRangeText(metric)
            val statusText = metricStatusText(metric)
            MetricRangeCardModel(
                metricName = metric.metricName,
                valueText = String.format(Locale.getDefault(), "%.2f %s", metric.value, metric.unit).trim(),
                rangeText = rangeText,
                statusText = statusText,
                helperText = "置信度 ${(metric.confidence * 100f).roundToInt().coerceIn(0, 100)}%",
                progressPercent = metricProgress(metric),
                tone = metricTone(metric)
            )
        }
    }

    private fun buildRiskSummaryCard(
        metrics: List<ParsedMedicalMetric>,
        cloudEnhanced: ReportUnderstandingData?
    ): RiskSummaryCardModel {
        val abnormalCount = metrics.count { it.isAbnormal }
        val title = when (draftRiskLevel) {
            "HIGH" -> "当前报告提示需要尽快线下评估"
            "MEDIUM" -> "有少量指标需要重点关注"
            else -> "暂未见明显高风险提示"
        }
        val summary = cloudEnhanced?.summary?.takeIf { it.isNotBlank() }
            ?: if (abnormalCount > 0) {
                "本次识别到 $abnormalCount 项异常或偏离指标，建议结合原始报告和后续复查结果理解。"
            } else {
                "当前已识别指标未见明显异常，仍建议结合原始报告全文和医生意见判断。"
            }
        return RiskSummaryCardModel(
            badgeText = when (draftRiskLevel) {
                "HIGH" -> "高风险"
                "MEDIUM" -> "中风险"
                else -> "低风险"
            },
            title = title,
            summary = summary,
            supportingText = "该结果基于 OCR 提取和自动整理，仅供辅助理解。",
            bullets = buildList {
                if (cloudEnhanced?.readableReport?.isNotBlank() == true) add("已生成可读摘要，可先阅读摘要再查看明细指标。")
                if (draftOcrMarkdown.isBlank()) add("当前导入内容缺少完整版式结构，复杂表格可能仍需手动校对。")
                if (metrics.isEmpty()) add("结构化指标不足，建议在下方编辑区修正 OCR 文本后重新整理。")
            },
            tone = when (draftRiskLevel) {
                "HIGH" -> CardTone.NEGATIVE
                "MEDIUM" -> CardTone.WARNING
                else -> CardTone.POSITIVE
            }
        )
    }

    private fun buildActionCards(metrics: List<ParsedMedicalMetric>): List<ActionGroupCardModel> {
        val abnormalCount = metrics.count { it.isAbnormal }
        val primaryAction = when (draftRiskLevel) {
            "HIGH" -> ActionGroupCardModel(
                category = "下一步建议",
                headline = "优先线下就医或进一步检查",
                supportingText = "高风险报告不建议只依赖端侧解释，需要尽快把原始报告带给医生判断。",
                detailLines = listOf("可先继续 AI 医生问诊补全病史。", "如已有纸质/电子原报告，可一并上传或展示。"),
                tone = CardTone.NEGATIVE
            )
            "MEDIUM" -> ActionGroupCardModel(
                category = "下一步建议",
                headline = "近期复查并记录变化",
                supportingText = "建议结合生活方式、近期症状和既往记录，优先关注偏离指标。",
                detailLines = listOf("可继续 AI 问诊补全主诉。", "如有连续报告，可后续做趋势比对。"),
                tone = CardTone.WARNING
            )
            else -> ActionGroupCardModel(
                category = "下一步建议",
                headline = "保留本次结果，继续观察",
                supportingText = "本次未见明显高风险提示，但不代表替代医生结论。",
                detailLines = listOf("如后续出现症状变化，可转到症状自查继续评估。"),
                tone = CardTone.POSITIVE
            )
        }

        return buildList {
            add(primaryAction)
            if (abnormalCount > 0) {
                add(
                    ActionGroupCardModel(
                        category = "数据校对",
                        headline = "必要时修正 OCR 后重新整理",
                        supportingText = "当前异常项和参考范围来自 OCR 文本提取，复杂表格仍可能存在识别偏差。",
                        detailLines = listOf("优先检查指标名、数值和参考范围是否对齐。"),
                        tone = CardTone.INFO
                    )
                )
            }
        }
    }

    private fun metricRangeText(metric: ParsedMedicalMetric): String {
        return when {
            metric.refLow != null && metric.refHigh != null ->
                "参考范围 ${metric.refLow}-${metric.refHigh} ${metric.unit}".trim()
            metric.refHigh != null ->
                "参考范围 ≤${metric.refHigh} ${metric.unit}".trim()
            metric.refLow != null ->
                "参考范围 ≥${metric.refLow} ${metric.unit}".trim()
            else -> "参考范围待补充"
        }
    }

    private fun metricStatusText(metric: ParsedMedicalMetric): String {
        val refHigh = metric.refHigh
        val refLow = metric.refLow
        if (!metric.isAbnormal) return "处于参考区间"
        return when {
            refHigh != null && metric.value > refHigh -> "偏高"
            refLow != null && metric.value < refLow -> "偏低"
            else -> "存在偏离"
        }
    }

    private fun metricProgress(metric: ParsedMedicalMetric): Int {
        val refLow = metric.refLow
        val refHigh = metric.refHigh
        val progress = when {
            refLow != null && refHigh != null && refHigh > refLow -> {
                ((metric.value - refLow) / (refHigh - refLow) * 100f)
            }
            refHigh != null && refHigh > 0f -> (metric.value / refHigh * 100f)
            refLow != null && refLow > 0f -> (metric.value / refLow * 100f)
            else -> 50f
        }
        return progress.roundToInt().coerceIn(0, 100)
    }

    private fun metricTone(metric: ParsedMedicalMetric): CardTone {
        val refHigh = metric.refHigh
        val refLow = metric.refLow
        return if (metric.isAbnormal) {
            when {
                refHigh != null && metric.value > refHigh -> CardTone.WARNING
                refLow != null && metric.value < refLow -> CardTone.WARNING
                else -> CardTone.WARNING
            }
        } else {
            CardTone.POSITIVE
        }
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
    val canConfirm: Boolean = false,
    val summaryCards: List<EvidenceCardModel> = emptyList(),
    val metricCards: List<MetricRangeCardModel> = emptyList(),
    val riskSummaryCard: RiskSummaryCardModel? = null,
    val actionCards: List<ActionGroupCardModel> = emptyList()
)

