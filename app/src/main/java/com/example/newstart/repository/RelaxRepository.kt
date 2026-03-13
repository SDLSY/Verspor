package com.example.newstart.repository

import com.example.newstart.database.dao.HealthMetricsDao
import com.example.newstart.database.dao.RelaxRecoveryControlSummary
import com.example.newstart.database.dao.RelaxRecoveryLinkSummary
import com.example.newstart.database.dao.RelaxDailyEffectTrend
import com.example.newstart.database.dao.RelaxDailySummary
import com.example.newstart.database.dao.RelaxProtocolStat
import com.example.newstart.database.dao.RelaxSessionDao
import com.example.newstart.database.dao.RelaxTopProtocol
import com.example.newstart.database.entity.HealthMetricsEntity
import com.example.newstart.database.entity.RelaxSessionEntity
import kotlinx.coroutines.flow.Flow

class RelaxRepository(
    private val relaxSessionDao: RelaxSessionDao,
    private val healthMetricsDao: HealthMetricsDao
) {

    fun getLatestMetricsFlow(): Flow<HealthMetricsEntity?> = healthMetricsDao.getLatest()

    suspend fun getLatestMetricsOnce(): HealthMetricsEntity? = healthMetricsDao.getLatestOnce()

    suspend fun saveSession(session: RelaxSessionEntity): Long = relaxSessionDao.insert(session)

    fun getTodaySummary(startTime: Long, endTime: Long): Flow<RelaxDailySummary> {
        return relaxSessionDao.getDailySummary(startTime, endTime)
    }

    fun getTopProtocol(startTime: Long, endTime: Long): Flow<RelaxTopProtocol?> {
        return relaxSessionDao.getTopProtocol(startTime, endTime)
    }

    fun getDailyEffectTrend(startTime: Long, endTime: Long): Flow<List<RelaxDailyEffectTrend>> {
        return relaxSessionDao.getDailyEffectTrend(startTime, endTime)
    }

    fun getProtocolStats(startTime: Long, endTime: Long): Flow<List<RelaxProtocolStat>> {
        return relaxSessionDao.getProtocolStats(startTime, endTime)
    }

    fun getRecoveryLinkSummary(startTime: Long, endTime: Long): Flow<RelaxRecoveryLinkSummary> {
        return relaxSessionDao.getRecoveryLinkSummary(startTime, endTime)
    }

    fun getRecoveryControlSummary(startTime: Long, endTime: Long): Flow<RelaxRecoveryControlSummary> {
        return relaxSessionDao.getRecoveryControlSummary(startTime, endTime)
    }
}
