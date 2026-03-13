package com.example.newstart.repository

import com.example.newstart.data.*
import com.example.newstart.database.dao.HealthMetricsDao
import com.example.newstart.database.dao.PpgSampleDao
import com.example.newstart.util.HRVAnalyzer
import com.example.newstart.database.dao.RecoveryScoreDao
import com.example.newstart.database.dao.SleepDataDao
import com.example.newstart.database.entity.HealthMetricsEntity
import com.example.newstart.database.entity.PpgSampleEntity
import com.example.newstart.database.entity.RecoveryScoreEntity
import com.example.newstart.database.entity.SleepDataEntity
import com.example.newstart.ui.trend.SleepStatistics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

/**
 * 睡眠数据仓库
 * 负责协调数据库、网络和蓝牙数据源
 */
class SleepRepository(
    private val sleepDataDao: SleepDataDao,
    private val healthMetricsDao: HealthMetricsDao,
    private val recoveryScoreDao: RecoveryScoreDao,
    private val ppgSampleDao: PpgSampleDao
) {

    fun getHealthMetricsSamplesByTimeRange(startTime: Long, endTime: Long): Flow<List<HealthMetricsEntity>> {
        return healthMetricsDao.getByTimeRange(startTime, endTime)
    }

    fun getPpgSamplesByTimeRange(startTime: Long, endTime: Long): Flow<List<PpgSampleEntity>> {
        return ppgSampleDao.getByTimeRange(startTime, endTime)
    }

    suspend fun savePpgSample(sleepRecordId: String, timestamp: Long, ppgValue: Float): Long {
        return ppgSampleDao.insert(
            PpgSampleEntity(
                sleepRecordId = sleepRecordId,
                timestamp = timestamp,
                ppgValue = ppgValue
            )
        )
    }
    
    /**
     * 获取最新的睡眠记录
     */
    fun getLatestSleepData(): Flow<SleepData?> {
        return sleepDataDao.getLatest().map { entity ->
            entity?.toSleepData()
        }
    }
    
    /**
     * 获取最近N天的睡眠记录
     */
    fun getLastNDaysSleep(days: Int): Flow<List<SleepData>> {
        return sleepDataDao.getLastNDays(days).map { entities ->
            entities.map { it.toSleepData() }
        }
    }
    
    /**
     * 保存睡眠记录
     */
    suspend fun saveSleepData(sleepData: SleepData): Long {
        val entity = sleepData.toEntity()
        return sleepDataDao.insert(entity)
    }
    
    /**
     * 获取最新的健康指标
     */
    fun getLatestHealthMetrics(): Flow<HealthMetrics?> {
        return healthMetricsDao.getLatest().map { entity ->
            entity?.toHealthMetrics()
        }
    }
    
    /**
     * 保存健康指标
     */
    suspend fun saveHealthMetrics(
        sleepRecordId: String,
        metrics: HealthMetrics,
        stepsSample: Int = 0,
        accMagnitudeSample: Float = 0f
    ): Long {
        val entity = metrics.toEntity(
            sleepRecordId = sleepRecordId,
            stepsSample = stepsSample,
            accMagnitudeSample = accMagnitudeSample
        )
        return healthMetricsDao.insert(entity)
    }
    
    /**
     * 获取最新的恢复指数
     */
    fun getLatestRecoveryScore(): Flow<RecoveryScore?> {
        return recoveryScoreDao.getLatest().map { entity ->
            entity?.toRecoveryScore()
        }
    }
    
    /**
     * 获取最近N天的恢复指数
     */
    fun getLastNDaysRecoveryScore(days: Int): Flow<List<RecoveryScore>> {
        return recoveryScoreDao.getLastNDays(days).map { entities ->
            entities.map { it.toRecoveryScore() }
        }
    }
    
    /**
     * 保存恢复指数
     */
    suspend fun saveRecoveryScore(sleepRecordId: String, score: RecoveryScore): Long {
        val entity = score.toEntity(sleepRecordId)
        return recoveryScoreDao.insert(entity)
    }
    
    /**
     * 获取睡眠统计数据
     */
    suspend fun getSleepStatistics(days: Int): SleepStatistics {
        val avgDurationMinutes = sleepDataDao.getAverageSleepDuration(days)
        val avgDeepSleepPercentage = sleepDataDao.getAverageDeepSleepPercentage(days)
        val avgEfficiency = sleepDataDao.getAverageSleepEfficiency(days)
        val bestSleepDateMillis = sleepDataDao.getBestSleepDate(days)

        val avgDurationText = if (avgDurationMinutes != null) {
            val duration = avgDurationMinutes.coerceAtLeast(0f)
            val hours = (duration / 60).toInt()
            val minutes = (duration % 60).toInt()
            "${hours}小时${minutes}分钟"
        } else {
            "--"
        }

        val avgDeepSleepText = avgDeepSleepPercentage?.let {
            String.format(Locale.getDefault(), "%.1f%%", it)
        } ?: "--"

        val avgEfficiencyText = avgEfficiency?.let {
            String.format(Locale.getDefault(), "%.1f%%", it)
        } ?: "--"

        val bestDayText = bestSleepDateMillis?.let {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
        } ?: "--"

        return SleepStatistics(
            avgDuration = avgDurationText,
            avgDeepSleepPercentage = avgDeepSleepText,
            avgEfficiency = avgEfficiencyText,
            bestDay = bestDayText
        )
    }
    
    /**
     * 清空所有数据（慎用）
     */
    suspend fun clearAllData() {
        sleepDataDao.deleteAll()
        healthMetricsDao.deleteAll()
        recoveryScoreDao.deleteAll()
        ppgSampleDao.deleteAll()
    }
}

// ========== 扩展函数：Entity转Model ==========

private fun SleepDataEntity.toSleepData(): SleepData {
    return SleepData(
        id = this.id,
        date = Date(this.date),
        bedTime = Date(this.bedTime),
        wakeTime = Date(this.wakeTime),
        totalSleepMinutes = this.totalSleepMinutes,
        deepSleepMinutes = this.deepSleepMinutes,
        lightSleepMinutes = this.lightSleepMinutes,
        remSleepMinutes = this.remSleepMinutes,
        awakeMinutes = this.awakeMinutes,
        sleepEfficiency = this.sleepEfficiency,
        fallAsleepMinutes = this.fallAsleepMinutes,
        awakeCount = this.awakeCount
    )
}

private fun SleepData.toEntity(): SleepDataEntity {
    return SleepDataEntity(
        id = this.id,
        date = this.date.time,
        bedTime = this.bedTime.time,
        wakeTime = this.wakeTime.time,
        totalSleepMinutes = this.totalSleepMinutes,
        deepSleepMinutes = this.deepSleepMinutes,
        lightSleepMinutes = this.lightSleepMinutes,
        remSleepMinutes = this.remSleepMinutes,
        awakeMinutes = this.awakeMinutes,
        sleepEfficiency = this.sleepEfficiency,
        fallAsleepMinutes = this.fallAsleepMinutes,
        awakeCount = this.awakeCount
    )
}

private fun HealthMetricsEntity.toHealthMetrics(): HealthMetrics {
    return HealthMetrics(
        heartRate = HeartRateData(
            current = this.heartRateSample,
            avg = this.heartRateAvg,
            min = this.heartRateMin,
            max = this.heartRateMax,
            trend = Trend.valueOf(this.heartRateTrend)
        ),
        bloodOxygen = BloodOxygenData(
            current = this.bloodOxygenSample,
            avg = this.bloodOxygenAvg,
            min = this.bloodOxygenMin,
            stability = this.bloodOxygenStability
        ),
        temperature = TemperatureData(
            current = this.temperatureSample,
            avg = this.temperatureAvg,
            status = this.temperatureStatus
        ),
        hrv = HRVData(
            current = this.hrvCurrent,
            baseline = this.hrvBaseline,
            recoveryRate = this.hrvRecoveryRate,
            trend = Trend.valueOf(this.hrvTrend),
            stressLevel = HRVAnalyzer.calculateStressLevel(this.hrvCurrent, this.hrvBaseline)
        )
    )
}

private fun HealthMetrics.toEntity(
    sleepRecordId: String,
    stepsSample: Int = 0,
    accMagnitudeSample: Float = 0f
): HealthMetricsEntity {
    return HealthMetricsEntity(
        sleepRecordId = sleepRecordId,
        timestamp = System.currentTimeMillis(),
        heartRateSample = this.heartRate.current,
        bloodOxygenSample = this.bloodOxygen.current,
        temperatureSample = this.temperature.current,
        stepsSample = stepsSample,
        accMagnitudeSample = accMagnitudeSample,
        heartRateCurrent = this.heartRate.current,
        heartRateAvg = this.heartRate.avg,
        heartRateMin = this.heartRate.min,
        heartRateMax = this.heartRate.max,
        heartRateTrend = this.heartRate.trend.name,
        bloodOxygenCurrent = this.bloodOxygen.current,
        bloodOxygenAvg = this.bloodOxygen.avg,
        bloodOxygenMin = this.bloodOxygen.min,
        bloodOxygenStability = this.bloodOxygen.stability,
        temperatureCurrent = this.temperature.current,
        temperatureAvg = this.temperature.avg,
        temperatureStatus = this.temperature.status,
        hrvCurrent = this.hrv.current,
        hrvBaseline = this.hrv.baseline,
        hrvRecoveryRate = this.hrv.recoveryRate,
        hrvTrend = this.hrv.trend.name
    )
}

private fun RecoveryScoreEntity.toRecoveryScore(): RecoveryScore {
    return RecoveryScore(
        score = this.score,
        sleepEfficiencyScore = this.sleepEfficiencyScore,
        hrvRecoveryScore = this.hrvRecoveryScore,
        deepSleepScore = this.deepSleepScore,
        temperatureRhythmScore = this.temperatureRhythmScore,
        oxygenStabilityScore = this.oxygenStabilityScore,
        level = RecoveryLevel.valueOf(this.level)
    )
}

private fun RecoveryScore.toEntity(sleepRecordId: String): RecoveryScoreEntity {
    return RecoveryScoreEntity(
        sleepRecordId = sleepRecordId,
        date = System.currentTimeMillis(),
        score = this.score,
        sleepEfficiencyScore = this.sleepEfficiencyScore,
        hrvRecoveryScore = this.hrvRecoveryScore,
        deepSleepScore = this.deepSleepScore,
        temperatureRhythmScore = this.temperatureRhythmScore,
        oxygenStabilityScore = this.oxygenStabilityScore,
        level = this.level.name
    )
}
