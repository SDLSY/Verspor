package com.example.newstart.demo

import com.example.newstart.data.*
import java.util.*
import kotlin.math.sin
import kotlin.random.Random

/**
 * 演示模式数据生成器
 * 生成真实感的模拟数据，带有自然波动
 */
object DemoDataGenerator {
    
    private val random = Random.Default
    private var startTime = System.currentTimeMillis()
    
    // ========== 传感器数据生成 ==========
    
    /**
     * 生成实时传感器数据（带有自然波动）
     */
    fun generateSensorData(): SensorData {
        return SensorData(
            timestamp = System.currentTimeMillis(),
            heartRate = generateHeartRate(),
            bloodOxygen = generateSpO2(),
            temperature = generateTemperature(),
            accelerometer = Triple(generateAccel(), generateAccel(), generateAccel()),
            gyroscope = Triple(generateAccel(), generateAccel(), generateAccel()),
            ppgValue = random.nextFloat() * 1000f,
            hrv = generateHRV(),
            steps = generateSteps()
        )
    }
    
    /**
     * 生成心率数据（55-75 bpm，带正弦波动）
     */
    private fun generateHeartRate(): Int {
        val baseHR = 62
        val timeSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        val variation = sin(timeSeconds / 10.0) * 8  // 正弦波动 ±8 bpm
        val noise = random.nextInt(-3, 3)  // 随机噪声
        return (baseHR + variation + noise).toInt().coerceIn(DemoConfig.HEART_RATE_RANGE)
    }
    
    /**
     * 生成血氧数据（95-99%）
     */
    private fun generateSpO2(): Int {
        val baseSpo2 = 97
        val variation = random.nextInt(-2, 2)
        return (baseSpo2 + variation).coerceIn(DemoConfig.SPO2_RANGE)
    }
    
    /**
     * 生成体温数据（36.0-37.0°C）
     */
    private fun generateTemperature(): Float {
        val baseTemp = 36.5f
        val timeSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        val variation = (sin(timeSeconds / 20.0) * 0.3).toFloat()  // 缓慢变化
        val noise = random.nextFloat() * 0.1f - 0.05f
        return (baseTemp + variation + noise).coerceIn(DemoConfig.TEMP_RANGE)
    }
    
    /**
     * 生成 HRV 数据（40-80 ms）
     */
    private fun generateHRV(): Int {
        val baseHRV = 60
        val variation = random.nextInt(-10, 10)
        return (baseHRV + variation).coerceIn(DemoConfig.HRV_RANGE)
    }
    
    /**
     * 生成步数（累加）
     */
    private var currentSteps = 0
    private var lastStepTime = System.currentTimeMillis()
    
    private fun generateSteps(): Int {
        val now = System.currentTimeMillis()
        if (now - lastStepTime > 5000) {  // 每5秒增加一些步数
            currentSteps += random.nextInt(5, 20)
            lastStepTime = now
        }
        return currentSteps.coerceIn(0, 15000)
    }
    
    /**
     * 生成加速度数据（-2.0 ~ 2.0g）
     */
    private fun generateAccel(): Float {
        return random.nextFloat() * 4f - 2f
    }
    
    // ========== 睡眠数据生成 ==========
    
    /**
     * 生成指定天数的睡眠数据
     */
    fun generateWeeklySleepData(days: Int = DemoConfig.PRELOAD_DAYS): List<SleepData> {
        return (0 until days).map { dayOffset ->
            generateDaySleepData(dayOffset)
        }
    }
    
    /**
     * 生成单天的睡眠数据
     */
    private fun generateDaySleepData(daysAgo: Int): SleepData {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        
        // 起床时间：早上 6:30 - 8:00
        calendar.set(Calendar.HOUR_OF_DAY, 7)
        calendar.set(Calendar.MINUTE, random.nextInt(0, 60))
        val wakeTime = Date(calendar.timeInMillis)
        
        // 上床时间：晚上 10:30 - 12:00（前一天）
        calendar.add(Calendar.HOUR_OF_DAY, -8)  // 假设睡 8 小时左右
        calendar.set(Calendar.MINUTE, random.nextInt(0, 60))
        val bedTime = Date(calendar.timeInMillis)
        
        // 睡眠时长：7-9 小时
        val totalMinutes = 420 + random.nextInt(-60, 60)
        
        // 深睡：25-35%
        val deepMinutes = (totalMinutes * (0.25f + random.nextFloat() * 0.1f)).toInt()
        
        // 浅睡：45-55%
        val lightMinutes = (totalMinutes * (0.45f + random.nextFloat() * 0.1f)).toInt()
        
        // REM：15-25%
        val remMinutes = (totalMinutes * (0.15f + random.nextFloat() * 0.1f)).toInt()
        
        // 清醒：剩余时间
        val awakeMinutes = totalMinutes - deepMinutes - lightMinutes - remMinutes
        
        return SleepData(
            id = UUID.randomUUID().toString(),
            date = Date(calendar.timeInMillis),
            bedTime = bedTime,
            wakeTime = wakeTime,
            totalSleepMinutes = totalMinutes,
            deepSleepMinutes = deepMinutes,
            lightSleepMinutes = lightMinutes,
            remSleepMinutes = remMinutes,
            awakeMinutes = awakeMinutes.coerceAtLeast(0),
            sleepEfficiency = 78f + random.nextFloat() * 18f,  // 78-96%
            fallAsleepMinutes = random.nextInt(5, 25),
            awakeCount = random.nextInt(0, 5)
        )
    }
    
    // ========== 健康指标生成 ==========
    
    /**
     * 生成健康指标数据
     */
    fun generateHealthMetrics(): HealthMetrics {
        val currentHR = random.nextInt(55, 65)
        val currentSpO2 = random.nextInt(95, 99)
        val currentTemp = 36.2f + random.nextFloat() * 0.6f
        val currentHRV = random.nextInt(50, 75)
        val baselineHRV = 60
        
        return HealthMetrics(
            heartRate = HeartRateData(
                current = currentHR,
                avg = currentHR + random.nextInt(-2, 2),
                min = currentHR - random.nextInt(3, 8),
                max = currentHR + random.nextInt(5, 12),
                trend = Trend.values().random(),
                isAbnormal = false
            ),
            bloodOxygen = BloodOxygenData(
                current = currentSpO2,
                avg = currentSpO2,
                min = currentSpO2 - random.nextInt(0, 3),
                stability = if (random.nextBoolean()) "稳定" else "良好",
                isAbnormal = false
            ),
            temperature = TemperatureData(
                current = currentTemp,
                avg = currentTemp,
                status = "正常",
                isAbnormal = false
            ),
            hrv = HRVData(
                current = currentHRV,
                baseline = baselineHRV,
                recoveryRate = -5f + random.nextFloat() * 20f,  // -5% ~ 15%
                trend = Trend.values().random(),
                stressLevel = StressLevel.values().random()
            )
        )
    }
    
    // ========== 恢复评分生成 ==========
    
    /**
     * 生成恢复评分
     */
    fun generateRecoveryScore(): RecoveryScore {
        val baseScore = 70 + random.nextInt(0, 25)  // 70-95
        
        return RecoveryScore(
            score = baseScore,
            sleepEfficiencyScore = 80f + random.nextFloat() * 15f,
            hrvRecoveryScore = 70f + random.nextFloat() * 20f,
            deepSleepScore = 75f + random.nextFloat() * 20f,
            temperatureRhythmScore = 80f + random.nextFloat() * 15f,
            oxygenStabilityScore = 90f + random.nextFloat() * 8f,
            level = when {
                baseScore >= 85 -> RecoveryLevel.EXCELLENT
                baseScore >= 75 -> RecoveryLevel.GOOD
                baseScore >= 60 -> RecoveryLevel.FAIR
                else -> RecoveryLevel.POOR
            }
        )
    }
    
    /**
     * 重置计数器（用于新的一天）
     */
    fun resetCounters() {
        currentSteps = 0
        startTime = System.currentTimeMillis()
        lastStepTime = startTime
    }
}
