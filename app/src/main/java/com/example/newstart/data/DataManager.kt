package com.example.newstart.data

import android.content.Context
import com.example.newstart.database.AppDatabase
import com.example.newstart.repository.SleepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * 数据管理器
 * 负责数据初始化、缓存管理、数据同步
 */
class DataManager(context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val repository = SleepRepository(
        database.sleepDataDao(),
        database.healthMetricsDao(),
        database.recoveryScoreDao(),
        database.ppgSampleDao()
    )
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * 初始化数据（首次使用时）
     */
    fun initializeMockData() {
        scope.launch {
            val count = database.sleepDataDao().getCount()
            if (count == 0) {
                // 数据库为空，插入7天模拟数据
                generateMockDataForDays(7)
            }
        }
    }
    
    /**
     * 生成N天的模拟数据
     */
    private suspend fun generateMockDataForDays(days: Int) {
        val calendar = Calendar.getInstance()
        
        for (i in 0 until days) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            calendar.set(Calendar.HOUR_OF_DAY, 7)  // 每天7点起床
            val wakeTime = calendar.time
            
            calendar.add(Calendar.HOUR_OF_DAY, -8)  // 8小时前睡觉
            val bedTime = calendar.time
            
            // 创建睡眠数据
            val sleepData = SleepData(
                id = UUID.randomUUID().toString(),
                date = Date(calendar.timeInMillis),
                bedTime = bedTime,
                wakeTime = wakeTime,
                totalSleepMinutes = (420 + (kotlin.random.Random.nextFloat() * 60)).toInt(),
                deepSleepMinutes = (120 + (kotlin.random.Random.nextFloat() * 40)).toInt(),
                lightSleepMinutes = (200 + (kotlin.random.Random.nextFloat() * 40)).toInt(),
                remSleepMinutes = (100 + (kotlin.random.Random.nextFloat() * 30)).toInt(),
                awakeMinutes = (kotlin.random.Random.nextInt(0, 10)),
                sleepEfficiency = 80f + (kotlin.random.Random.nextFloat() * 15),
                fallAsleepMinutes = kotlin.random.Random.nextInt(5, 20),
                awakeCount = kotlin.random.Random.nextInt(0, 4)
            )
            
            repository.saveSleepData(sleepData)
            
            // 创建健康指标
            val metrics = HealthMetrics(
                heartRate = HeartRateData(
                    current = kotlin.random.Random.nextInt(55, 65),
                    avg = 60,
                    min = 52,
                    max = 68,
                    trend = Trend.values().random()
                ),
                bloodOxygen = BloodOxygenData(
                    current = kotlin.random.Random.nextInt(95, 99),
                    avg = 97,
                    min = 95,
                    stability = "稳定"
                ),
                temperature = TemperatureData(
                    current = 36.3f + (kotlin.random.Random.nextFloat() * 0.3f),
                    avg = 36.5f,
                    status = "正常"
                ),
                hrv = HRVData(
                    current = kotlin.random.Random.nextInt(55, 75),
                    baseline = 60,
                    recoveryRate = -5f + (kotlin.random.Random.nextFloat() * 15),
                    trend = Trend.values().random(),
                    stressLevel = StressLevel.values().random()
                )
            )
            
            repository.saveHealthMetrics(sleepData.id, metrics)
            
            // 创建恢复指数
            val recoveryScore = RecoveryScore.calculate(
                sleepEfficiency = sleepData.sleepEfficiency,
                hrvRecoveryRate = metrics.hrv.recoveryRate,
                deepSleepPercentage = sleepData.getDeepSleepPercentage(),
                temperatureRhythm = 85f,
                oxygenStability = 95f
            )
            
            repository.saveRecoveryScore(sleepData.id, recoveryScore)
        }
    }
    
    /**
     * 清空所有数据
     */
    fun clearAllData() {
        scope.launch {
            repository.clearAllData()
        }
    }
}
