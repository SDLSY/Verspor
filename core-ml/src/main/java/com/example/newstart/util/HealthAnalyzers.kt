package com.example.newstart.util

import com.example.newstart.data.ActivityLevel
import com.example.newstart.data.StressLevel
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * HRV 分析工具
 */
object HRVAnalyzer {
    
    /**
     * 计算压力等级
     * @param hrv 当前 HRV 值 (ms)
     * @param baseline 基线 HRV 值 (ms)
     * @return 压力等级
     */
    fun calculateStressLevel(hrv: Int, baseline: Int): StressLevel {
        if (baseline <= 0) return StressLevel.MODERATE
        
        val ratio = hrv.toFloat() / baseline
        return when {
            ratio >= 0.9f -> StressLevel.LOW       // >= 90% 基线：放松
            ratio >= 0.7f -> StressLevel.MODERATE  // 70-90%：正常
            ratio >= 0.5f -> StressLevel.HIGH      // 50-70%：偏高
            else -> StressLevel.VERY_HIGH          // < 50%：很高
        }
    }
    
    /**
     * 计算恢复率
     * @param currentHrv 当前 HRV
     * @param baselineHrv 基线 HRV
     * @return 恢复率 (0-100%)
     */
    fun getRecoveryRate(currentHrv: Int, baselineHrv: Int): Float {
        if (baselineHrv <= 0) return 0f
        return (currentHrv.toFloat() / baselineHrv * 100).coerceIn(0f, 150f)
    }
    
    /**
     * 计算 HRV 基线（基于历史数据的 30 天平均）
     * @param historicalValues 历史 HRV 值列表
     * @return 基线值
     */
    fun calculateBaseline(historicalValues: List<Int>): Int {
        if (historicalValues.isEmpty()) return 50 // 默认基线
        
        return historicalValues
            .filter { it in 20..200 } // 过滤异常值
            .average()
            .toInt()
    }
}

/**
 * 活动分析工具
 */
object ActivityAnalyzer {
    
    /**
     * 根据加速度计数据分类活动强度
     * @param accel 加速度三轴数据 (x, y, z)
     * @return 活动强度等级
     */
    fun classifyActivity(accel: Triple<Float, Float, Float>): ActivityLevel {
        val magnitude = calculateMagnitude(accel)
        
        return when {
            magnitude < 0.1f -> ActivityLevel.STILL     // 静止
            magnitude < 0.5f -> ActivityLevel.LIGHT     // 轻度活动
            magnitude < 1.5f -> ActivityLevel.MODERATE  // 中度活动
            else -> ActivityLevel.VIGOROUS              // 剧烈活动
        }
    }
    
    /**
     * 计算加速度向量的模
     */
    fun calculateMagnitude(accel: Triple<Float, Float, Float>): Float {
        return sqrt(
            accel.first.pow(2) + 
            accel.second.pow(2) + 
            accel.third.pow(2)
        )
    }
    
    /**
     * 检测是否在运动（非静止状态）
     */
    fun isMoving(accel: Triple<Float, Float, Float>): Boolean {
        return calculateMagnitude(accel) >= 0.1f
    }
    
    /**
     * 估算消耗的卡路里
     * @param activityMinutes 各强度活动时长（轻度、中度、剧烈）
     * @param weightKg 体重（kg）
     * @return 消耗卡路里
     */
    fun estimateCalories(
        lightMinutes: Int,
        moderateMinutes: Int,
        vigorousMinutes: Int,
        weightKg: Float = 70f
    ): Int {
        // MET (代谢当量) 值
        val lightMET = 2.5f    // 轻度活动
        val moderateMET = 4.0f // 中度活动
        val vigorousMET = 7.0f // 剧烈活动
        
        val calories = (
            lightMinutes * lightMET +
            moderateMinutes * moderateMET +
            vigorousMinutes * vigorousMET
        ) * weightKg / 60f
        
        return calories.toInt()
    }
}

/**
 * 步数分析工具
 */
object StepsAnalyzer {
    
    /**
     * 估算步数对应的卡路里消耗
     * @param steps 步数
     * @param weightKg 体重（kg）
     * @param heightCm 身高（cm）
     * @return 消耗卡路里
     */
    fun estimateCaloriesFromSteps(
        steps: Int,
        weightKg: Float = 70f,
        heightCm: Float = 170f
    ): Int {
        // 步长估算（身高的 0.43 倍）
        val strideLength = heightCm * 0.43f / 100f // 米
        val distanceKm = steps * strideLength / 1000f
        
        // 每公里消耗卡路里约为体重（kg）× 1.036
        val calories = distanceKm * weightKg * 1.036f
        
        return calories.toInt()
    }
    
    /**
     * 估算步行距离（公里）
     */
    fun estimateDistance(steps: Int, heightCm: Float = 170f): Float {
        val strideLength = heightCm * 0.43f / 100f
        return steps * strideLength / 1000f
    }
    
    /**
     * 计算进度百分比
     */
    fun getProgress(currentSteps: Int, targetSteps: Int): Float {
        if (targetSteps <= 0) return 0f
        return (currentSteps.toFloat() / targetSteps * 100).coerceIn(0f, 100f)
    }
    
    /**
     * 判断是否达标
     */
    fun isTargetReached(currentSteps: Int, targetSteps: Int): Boolean {
        return currentSteps >= targetSteps
    }
}
