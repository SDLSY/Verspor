package com.example.newstart.data

/**
 * 睡眠分期枚举
 */
enum class SleepStage {
    AWAKE,      // 清醒
    LIGHT,      // ǳ˯ (N1/N2)
    DEEP,       // 深睡 (N3)
    REM;        // REM睡眠
    
    fun getDisplayName(): String {
        return when (this) {
            AWAKE -> "清醒"
            LIGHT -> "ǳ˯"
            DEEP -> "深睡"
            REM -> "REM"
        }
    }
    
    fun getColor(): Int {
        return when (this) {
            AWAKE -> 0xFFE0E0E0.toInt()
            LIGHT -> 0xFF81D4FA.toInt()
            DEEP -> 0xFF1976D2.toInt()
            REM -> 0xFF9C27B0.toInt()
        }
    }
}
