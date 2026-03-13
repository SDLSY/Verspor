package com.example.newstart.demo

import android.content.Context
import android.util.Log
import com.example.newstart.database.AppDatabase
import com.example.newstart.repository.SleepRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 演示模式数据预加载器
 * 在应用启动时自动加载历史数据
 */
class DemoDataPreloader(private val context: Context) {
    
    private val TAG = "DemoDataPreloader"
    
    private val repository: SleepRepository
    
    init {
        val database = AppDatabase.getDatabase(context)
        repository = SleepRepository(
            database.sleepDataDao(),
            database.healthMetricsDao(),
            database.recoveryScoreDao(),
            database.ppgSampleDao()
        )
    }
    
    /**
     * 预加载演示数据
     * 如果数据库已有数据则跳过
     */
    suspend fun preloadDemoData() = withContext(Dispatchers.IO) {
        if (!DemoConfig.isDemoMode) {
            Log.d(TAG, "演示模式未开启，跳过预加载")
            return@withContext
        }
        
        try {
            // 检查数据库是否已有数据
            val database = AppDatabase.getDatabase(context)
            val existingCount = database.sleepDataDao().getCount()
            
            if (existingCount > 0) {
                Log.d(TAG, "数据库已有 $existingCount 条记录，跳过预加载")
                return@withContext
            }
            
            Log.d(TAG, "开始预加载 ${DemoConfig.PRELOAD_DAYS} 天演示数据...")
            
            // 生成并保存历史数据
            val weeklyData = DemoDataGenerator.generateWeeklySleepData(DemoConfig.PRELOAD_DAYS)
            
            weeklyData.forEachIndexed { index, sleepData ->
                // 保存睡眠数据
                repository.saveSleepData(sleepData)
                
                // 保存对应的健康指标
                val metrics = DemoDataGenerator.generateHealthMetrics()
                repository.saveHealthMetrics(sleepData.id, metrics)
                
                // 保存恢复评分
                val score = DemoDataGenerator.generateRecoveryScore()
                repository.saveRecoveryScore(sleepData.id, score)
                
                Log.d(TAG, "已加载第 ${index + 1}/${weeklyData.size} 天数据")
            }
            
            Log.d(TAG, "✅ 演示数据预加载完成！共 ${weeklyData.size} 天数据")
            
        } catch (e: Exception) {
            Log.e(TAG, "预加载数据失败", e)
        }
    }
    
    /**
     * 清除所有演示数据
     */
    suspend fun clearDemoData() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "清除演示数据...")
            repository.clearAllData()
            DemoDataGenerator.resetCounters()
            Log.d(TAG, "✅ 演示数据已清除")
        } catch (e: Exception) {
            Log.e(TAG, "清除数据失败", e)
        }
    }
}
