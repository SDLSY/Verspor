package com.example.newstart.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.newstart.database.entity.RelaxSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RelaxSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: RelaxSessionEntity): Long

    @Query("SELECT * FROM relax_sessions ORDER BY endTime DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<RelaxSessionEntity>

    @Query("SELECT * FROM relax_sessions WHERE startTime BETWEEN :startTime AND :endTime ORDER BY startTime DESC")
    fun getByTimeRange(startTime: Long, endTime: Long): Flow<List<RelaxSessionEntity>>

    @Query(
        """
        SELECT
            COUNT(*) AS sessionCount,
            CAST(COALESCE(SUM(durationSec), 0) / 60 AS INTEGER) AS totalMinutes,
            CAST(COALESCE(AVG(effectScore), 0) AS REAL) AS avgEffectScore
        FROM relax_sessions
        WHERE startTime BETWEEN :startTime AND :endTime
        """
    )
    fun getDailySummary(startTime: Long, endTime: Long): Flow<RelaxDailySummary>

    @Query(
        """
        SELECT protocolType AS protocolType,
               COUNT(*) AS sessions,
               CAST(COALESCE(AVG(effectScore), 0) AS REAL) AS avgEffectScore
        FROM relax_sessions
        WHERE startTime BETWEEN :startTime AND :endTime
        GROUP BY protocolType
        ORDER BY avgEffectScore DESC, sessions DESC
        LIMIT 1
        """
    )
    fun getTopProtocol(startTime: Long, endTime: Long): Flow<RelaxTopProtocol?>

    @Query(
        """
        SELECT
            strftime('%Y-%m-%d', startTime / 1000, 'unixepoch', 'localtime') AS day,
            COUNT(*) AS sessions,
            CAST(COALESCE(AVG(effectScore), 0) AS REAL) AS avgEffectScore,
            CAST(COALESCE(AVG(preStress - postStress), 0) AS REAL) AS avgStressDrop
        FROM relax_sessions
        WHERE startTime BETWEEN :startTime AND :endTime
        GROUP BY day
        ORDER BY day ASC
        """
    )
    fun getDailyEffectTrend(startTime: Long, endTime: Long): Flow<List<RelaxDailyEffectTrend>>

    @Query(
        """
        SELECT
            protocolType AS protocolType,
            COUNT(*) AS sessions,
            CAST(COALESCE(AVG(effectScore), 0) AS REAL) AS avgEffectScore,
            CAST(COALESCE(AVG(preStress - postStress), 0) AS REAL) AS avgStressDrop
        FROM relax_sessions
        WHERE startTime BETWEEN :startTime AND :endTime
        GROUP BY protocolType
        ORDER BY avgEffectScore DESC, sessions DESC
        """
    )
    fun getProtocolStats(startTime: Long, endTime: Long): Flow<List<RelaxProtocolStat>>

    @Query(
        """
        WITH daily_relax AS (
            SELECT
                strftime('%Y-%m-%d', startTime / 1000, 'unixepoch', 'localtime') AS day
            FROM relax_sessions
            WHERE startTime BETWEEN :startTime AND :endTime
            GROUP BY day
        ),
        daily_recovery AS (
            SELECT
                strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') AS day,
                CAST(AVG(score) AS REAL) AS avgScore
            FROM recovery_scores
            WHERE date BETWEEN :startTime AND (:endTime + 86400000)
            GROUP BY day
        )
        SELECT
            COUNT(*) AS linkedDays,
            CAST(COALESCE(AVG(sameDay.avgScore), 0) AS REAL) AS avgSameDayRecovery,
            CAST(COALESCE(AVG(nextDay.avgScore), 0) AS REAL) AS avgNextDayRecovery,
            CAST(COALESCE(AVG(nextDay.avgScore - sameDay.avgScore), 0) AS REAL) AS avgRecoveryDelta
        FROM daily_relax relaxDay
        JOIN daily_recovery sameDay ON sameDay.day = relaxDay.day
        JOIN daily_recovery nextDay ON nextDay.day = date(relaxDay.day, '+1 day')
        """
    )
    fun getRecoveryLinkSummary(startTime: Long, endTime: Long): Flow<RelaxRecoveryLinkSummary>

    @Query(
        """
        WITH daily_relax AS (
            SELECT
                strftime('%Y-%m-%d', startTime / 1000, 'unixepoch', 'localtime') AS day
            FROM relax_sessions
            WHERE startTime BETWEEN :startTime AND :endTime
            GROUP BY day
        ),
        daily_recovery AS (
            SELECT
                strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') AS day,
                CAST(AVG(score) AS REAL) AS avgScore
            FROM recovery_scores
            WHERE date BETWEEN :startTime AND (:endTime + 86400000)
            GROUP BY day
        ),
        non_relax_days AS (
            SELECT day
            FROM daily_recovery
            WHERE day NOT IN (SELECT day FROM daily_relax)
        )
        SELECT
            COUNT(*) AS controlDays,
            CAST(COALESCE(AVG(nextDay.avgScore - sameDay.avgScore), 0) AS REAL) AS avgRecoveryDelta
        FROM non_relax_days d
        JOIN daily_recovery sameDay ON sameDay.day = d.day
        JOIN daily_recovery nextDay ON nextDay.day = date(d.day, '+1 day')
        """
    )
    fun getRecoveryControlSummary(startTime: Long, endTime: Long): Flow<RelaxRecoveryControlSummary>
}

data class RelaxDailySummary(
    val sessionCount: Int = 0,
    val totalMinutes: Int = 0,
    val avgEffectScore: Float = 0f
)

data class RelaxTopProtocol(
    val protocolType: String,
    val sessions: Int,
    val avgEffectScore: Float
)

data class RelaxDailyEffectTrend(
    val day: String,
    val sessions: Int,
    val avgEffectScore: Float,
    val avgStressDrop: Float
)

data class RelaxProtocolStat(
    val protocolType: String,
    val sessions: Int,
    val avgEffectScore: Float,
    val avgStressDrop: Float
)

data class RelaxRecoveryLinkSummary(
    val linkedDays: Int = 0,
    val avgSameDayRecovery: Float = 0f,
    val avgNextDayRecovery: Float = 0f,
    val avgRecoveryDelta: Float = 0f
)

data class RelaxRecoveryControlSummary(
    val controlDays: Int = 0,
    val avgRecoveryDelta: Float = 0f
)
