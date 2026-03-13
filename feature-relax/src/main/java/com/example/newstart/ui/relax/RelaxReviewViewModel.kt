package com.example.newstart.ui.relax

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.newstart.core.common.R
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.dao.RelaxDailyEffectTrend
import com.example.newstart.database.dao.RelaxRecoveryControlSummary
import com.example.newstart.database.dao.RelaxRecoveryLinkSummary
import com.example.newstart.database.dao.RelaxDailySummary
import com.example.newstart.database.dao.RelaxProtocolStat
import com.example.newstart.database.dao.RelaxTopProtocol
import com.example.newstart.repository.RelaxRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class RelaxReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val db = AppDatabase.getDatabase(application)
    private val repository = RelaxRepository(
        relaxSessionDao = db.relaxSessionDao(),
        healthMetricsDao = db.healthMetricsDao()
    )

    private val _uiState = MutableLiveData(RelaxReviewUiState())
    val uiState: LiveData<RelaxReviewUiState> = _uiState

    private var currentRange = RelaxReviewRange.LAST_7_DAYS

    private var summaryJob: Job? = null
    private var topProtocolJob: Job? = null
    private var trendJob: Job? = null
    private var rankJob: Job? = null
    private var recoveryLinkJob: Job? = null
    private var recoveryControlJob: Job? = null

    private var latestSummary = RelaxDailySummary()
    private var latestTop: RelaxTopProtocol? = null
    private var latestTrend: List<RelaxDailyEffectTrend> = emptyList()
    private var latestRank: List<RelaxProtocolStat> = emptyList()
    private var latestRecoveryLink = RelaxRecoveryLinkSummary()
    private var latestRecoveryControl = RelaxRecoveryControlSummary()

    init {
        observeRange(currentRange)
    }

    fun setRange(range: RelaxReviewRange) {
        if (range == currentRange) return
        currentRange = range
        observeRange(currentRange)
    }

    private fun observeRange(range: RelaxReviewRange) {
        val (startTime, endTime) = range.toTimeRange()

        summaryJob?.cancel()
        topProtocolJob?.cancel()
        trendJob?.cancel()
        rankJob?.cancel()
        recoveryLinkJob?.cancel()
        recoveryControlJob?.cancel()

        summaryJob = viewModelScope.launch {
            repository.getTodaySummary(startTime, endTime).collectLatest { summary ->
                latestSummary = summary
                rebuildUiState()
            }
        }

        topProtocolJob = viewModelScope.launch {
            repository.getTopProtocol(startTime, endTime).collectLatest { top ->
                latestTop = top
                rebuildUiState()
            }
        }

        trendJob = viewModelScope.launch {
            repository.getDailyEffectTrend(startTime, endTime).collectLatest { trend ->
                latestTrend = trend
                rebuildUiState()
            }
        }

        rankJob = viewModelScope.launch {
            repository.getProtocolStats(startTime, endTime).collectLatest { rank ->
                latestRank = rank
                rebuildUiState()
            }
        }

        recoveryLinkJob = viewModelScope.launch {
            repository.getRecoveryLinkSummary(startTime, endTime).collectLatest { summary ->
                latestRecoveryLink = summary
                rebuildUiState()
            }
        }

        recoveryControlJob = viewModelScope.launch {
            repository.getRecoveryControlSummary(startTime, endTime).collectLatest { control ->
                latestRecoveryControl = control
                rebuildUiState()
            }
        }
    }

    private fun rebuildUiState() {
        val topName = latestTop?.protocolType?.toProtocolName()
            ?: app.getString(R.string.relax_review_top_protocol_none)
        val topDetail = latestTop?.let {
            app.getString(
                R.string.relax_review_protocol_rank_item_detail,
                it.sessions,
                it.avgEffectScore.roundToInt().coerceIn(0, 100)
            )
        } ?: app.getString(R.string.relax_review_top_protocol_none)

        val trendLabels = latestTrend.map { it.day.toDisplayDay() }
        val trendValues = latestTrend.map { it.avgEffectScore.coerceIn(0f, 100f) }

        val protocolRows = latestRank.take(5).map {
            RelaxProtocolRankRowUi(
                protocolName = it.protocolType.toProtocolName(),
                detail = app.getString(
                    R.string.relax_review_protocol_rank_item_detail,
                    it.sessions,
                    it.avgEffectScore.roundToInt().coerceIn(0, 100)
                ),
                effectScore = it.avgEffectScore.roundToInt().coerceIn(0, 100),
                progress = it.avgEffectScore.roundToInt().coerceIn(0, 100)
            )
        }

        val hasData = latestSummary.sessionCount > 0 || latestTrend.isNotEmpty() || latestRank.isNotEmpty()
        val gainVsControl = latestRecoveryLink.avgRecoveryDelta - latestRecoveryControl.avgRecoveryDelta

        _uiState.postValue(
            RelaxReviewUiState(
                range = currentRange,
                totalSessions = latestSummary.sessionCount,
                totalMinutes = latestSummary.totalMinutes,
                avgEffectScore = latestSummary.avgEffectScore.roundToInt().coerceIn(0, 100),
                avgStressDrop = latestTrend.map { it.avgStressDrop }.average().toFloat(),
                topProtocolName = topName,
                topProtocolDetail = topDetail,
                trendLabels = trendLabels,
                trendValues = trendValues,
                protocolRows = protocolRows,
                recoveryLinkedDays = latestRecoveryLink.linkedDays,
                recoveryControlDays = latestRecoveryControl.controlDays,
                recoverySameDayAvg = latestRecoveryLink.avgSameDayRecovery,
                recoveryNextDayAvg = latestRecoveryLink.avgNextDayRecovery,
                recoveryDelta = latestRecoveryLink.avgRecoveryDelta,
                recoveryControlDelta = latestRecoveryControl.avgRecoveryDelta,
                recoveryGainVsControl = gainVsControl,
                hasData = hasData
            )
        )
    }

    private fun RelaxReviewRange.toTimeRange(): Pair<Long, Long> {
        val days = when (this) {
            RelaxReviewRange.LAST_7_DAYS -> 7
            RelaxReviewRange.LAST_30_DAYS -> 30
        }

        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
        val start = calendar.timeInMillis
        return start to end
    }

    private fun String?.toProtocolName(): String {
        return when (this?.uppercase(Locale.ROOT)) {
            "BREATH_4_6" -> app.getString(R.string.relax_protocol_name_46)
            "BREATH_4_7_8" -> app.getString(R.string.relax_protocol_name_478)
            "BOX" -> app.getString(R.string.relax_protocol_name_box)
            else -> app.getString(R.string.relax_protocol_name_unknown)
        }
    }

    private fun String.toDisplayDay(): String {
        return if (length >= 10 && this[4] == '-' && this[7] == '-') {
            substring(5, 10)
        } else {
            this
        }
    }
}

data class RelaxReviewUiState(
    val range: RelaxReviewRange = RelaxReviewRange.LAST_7_DAYS,
    val totalSessions: Int = 0,
    val totalMinutes: Int = 0,
    val avgEffectScore: Int = 0,
    val avgStressDrop: Float = 0f,
    val topProtocolName: String = "",
    val topProtocolDetail: String = "",
    val trendLabels: List<String> = emptyList(),
    val trendValues: List<Float> = emptyList(),
    val protocolRows: List<RelaxProtocolRankRowUi> = emptyList(),
    val recoveryLinkedDays: Int = 0,
    val recoveryControlDays: Int = 0,
    val recoverySameDayAvg: Float = 0f,
    val recoveryNextDayAvg: Float = 0f,
    val recoveryDelta: Float = 0f,
    val recoveryControlDelta: Float = 0f,
    val recoveryGainVsControl: Float = 0f,
    val hasData: Boolean = false
)

data class RelaxProtocolRankRowUi(
    val protocolName: String,
    val detail: String,
    val effectScore: Int,
    val progress: Int
)

enum class RelaxReviewRange {
    LAST_7_DAYS,
    LAST_30_DAYS
}

