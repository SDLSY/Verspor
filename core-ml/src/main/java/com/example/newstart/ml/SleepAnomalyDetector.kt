package com.example.newstart.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.exp

/**
 * Sleep anomaly detector.
 *
 * P1: prefer TensorFlow Lite model inference on device.
 * If model is missing or inference fails, fallback to rule-based scoring.
 */
class SleepAnomalyDetector(
    context: Context,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH
) : Closeable {

    companion object {
        private const val TAG = "SleepAnomalyDetector"
        const val DEFAULT_MODEL_ASSET_PATH = "ml/sleep_model.tflite"

        private const val WEIGHT_HR = 0.3f
        private const val WEIGHT_SPO2 = 0.4f
        private const val WEIGHT_HRV = 0.2f
        private const val WEIGHT_TEMP = 0.1f

        private const val HR_NORMAL_MIN = 50f
        private const val HR_NORMAL_MAX = 100f
        private const val HR_CRITICAL_MIN = 40f
        private const val HR_CRITICAL_MAX = 120f

        private const val SPO2_NORMAL_MIN = 95f
        private const val SPO2_WARNING_MIN = 90f
        private const val SPO2_CRITICAL_MIN = 85f

        private const val HRV_NORMAL_MIN = 20f
        private const val HRV_OPTIMAL_MIN = 50f

        private const val TEMP_NORMAL_MIN = 36.0f
        private const val TEMP_NORMAL_MAX = 37.5f
    }

    private val interpreter: Interpreter? = createInterpreter(context, modelAssetPath)
    private val inferenceTimeHistory = mutableListOf<Float>()

    fun hasLoadedModel(): Boolean = interpreter != null

    fun predict(input: SensorDataInput): PredictionResult {
        val startTime = System.nanoTime()

        return try {
            val componentRisk = calculateComponentRisk(input)
            val fallbackScore = calculateRuleScore(componentRisk)
            val modelScore = runTflite(input)
            val scoreSource = if (modelScore != null) {
                InferenceSource.TFLITE
            } else {
                InferenceSource.RULE_FALLBACK
            }

            val anomalyScore = (modelScore ?: fallbackScore).coerceIn(0f, 1f)
            val (isAbnormal, level, message) = interpretResult(
                anomalyScore = anomalyScore,
                heartRate = input.heartRate,
                spo2 = input.spo2,
                hrvSdnn = input.hrvSdnn,
                temp = input.temp
            )

            val inferenceTime = (System.nanoTime() - startTime) / 1_000_000f
            inferenceTimeHistory.add(inferenceTime)
            val confidence = estimateConfidence(anomalyScore, scoreSource, componentRisk)
            val primaryFactor = detectPrimaryFactor(componentRisk)

            PredictionResult(
                anomalyScore = anomalyScore,
                isAbnormal = isAbnormal,
                level = level,
                message = message,
                inferenceTimeMs = inferenceTime,
                source = scoreSource,
                confidence = confidence,
                primaryFactor = primaryFactor,
                componentRisk = componentRisk,
                details = mapOf(
                    "source_tflite" to if (scoreSource == InferenceSource.TFLITE) 1f else 0f,
                    "confidence" to confidence,
                    "risk_hr" to componentRisk.heartRate,
                    "risk_spo2" to componentRisk.bloodOxygen,
                    "risk_hrv" to componentRisk.hrv,
                    "risk_temp" to componentRisk.temperature
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Predict failed: ${e.message}", e)
            PredictionResult(
                anomalyScore = 0f,
                isAbnormal = false,
                level = AnomalyLevel.ERROR,
                message = "端侧推理异常: ${e.message ?: "unknown"}",
                inferenceTimeMs = 0f,
                source = InferenceSource.ERROR,
                confidence = 0f,
                primaryFactor = AnomalyPrimaryFactor.NONE
            )
        }
    }

    private fun createInterpreter(context: Context, assetPath: String): Interpreter? {
        return try {
            val modelBuffer = loadModelFile(context, assetPath)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            Interpreter(modelBuffer, options).also {
                Log.i(TAG, "TFLite model loaded: $assetPath")
            }
        } catch (e: Exception) {
            Log.w(TAG, "TFLite model unavailable, fallback to rules. reason=${e.message}")
            null
        }
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetPath)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    private fun runTflite(input: SensorDataInput): Float? {
        val localInterpreter = interpreter ?: return null

        if (localInterpreter.inputTensorCount < 3 || localInterpreter.outputTensorCount < 1) {
            Log.w(TAG, "Unexpected model signature. inputCount=${localInterpreter.inputTensorCount}")
            return null
        }

        val hrInput = arrayOf(
            floatArrayOf(
                input.heartRate,
                input.heartRateMin,
                input.heartRateMax,
                input.heartRateStd
            )
        )
        val spo2Input = arrayOf(
            floatArrayOf(
                input.spo2,
                input.spo2Min,
                input.spo2Max
            )
        )
        val hrvInput = arrayOf(
            floatArrayOf(
                input.hrvSdnn,
                input.hrvRmssd,
                input.hrvPnn50
            )
        )

        val output = Array(1) { FloatArray(1) }
        val outputs = hashMapOf<Int, Any>(0 to output)

        val inputs = Array<Any>(localInterpreter.inputTensorCount) { hrInput }
        for (i in 0 until localInterpreter.inputTensorCount) {
            val tensor = localInterpreter.getInputTensor(i)
            val name = tensor.name().lowercase()
            val lastDim = tensor.shape().lastOrNull() ?: -1
            inputs[i] = when {
                "hrv" in name -> hrvInput
                "spo2" in name || "oxygen" in name -> spo2Input
                "hr_" in name || "heart" in name -> hrInput
                lastDim == 4 -> hrInput
                else -> spo2Input
            }
        }

        localInterpreter.runForMultipleInputsOutputs(inputs, outputs)

        val rawScore = output[0][0]
        return if (rawScore in 0f..1f) rawScore else sigmoid(rawScore)
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x)))

    private fun calculateRuleScore(input: SensorDataInput): Float {
        return calculateRuleScore(calculateComponentRisk(input))
    }

    private fun calculateRuleScore(componentRisk: ComponentRiskScores): Float {
        return (
            componentRisk.bloodOxygen * WEIGHT_SPO2 +
                componentRisk.heartRate * WEIGHT_HR +
                componentRisk.hrv * WEIGHT_HRV +
                componentRisk.temperature * WEIGHT_TEMP
            ).coerceIn(0f, 1f)
    }

    private fun calculateComponentRisk(input: SensorDataInput): ComponentRiskScores {
        return ComponentRiskScores(
            heartRate = calculateHeartRateScore(input),
            bloodOxygen = calculateSpO2Score(input),
            hrv = calculateHRVScore(input),
            temperature = calculateTemperatureScore(input)
        )
    }

    private fun calculateHeartRateScore(input: SensorDataInput): Float {
        val hr = input.heartRate
        val hrStd = input.heartRateStd
        var score = 0f

        score += when {
            hr < HR_CRITICAL_MIN -> 1.0f
            hr > HR_CRITICAL_MAX -> 1.0f
            hr < HR_NORMAL_MIN -> (HR_NORMAL_MIN - hr) / (HR_NORMAL_MIN - HR_CRITICAL_MIN)
            hr > HR_NORMAL_MAX -> (hr - HR_NORMAL_MAX) / (HR_CRITICAL_MAX - HR_NORMAL_MAX)
            else -> 0f
        }

        if (hrStd > 15f) {
            score += (hrStd - 15f) / 15f * 0.3f
        }
        return score.coerceIn(0f, 1f)
    }

    private fun calculateSpO2Score(input: SensorDataInput): Float {
        val spo2 = input.spo2
        val spo2Min = input.spo2Min
        var score = 0f

        score += when {
            spo2 < SPO2_CRITICAL_MIN -> 1.0f
            spo2 < SPO2_WARNING_MIN ->
                (SPO2_WARNING_MIN - spo2) / (SPO2_WARNING_MIN - SPO2_CRITICAL_MIN) * 0.7f
            spo2 < SPO2_NORMAL_MIN ->
                (SPO2_NORMAL_MIN - spo2) / (SPO2_NORMAL_MIN - SPO2_WARNING_MIN) * 0.3f
            else -> 0f
        }

        if (spo2Min < spo2) {
            val minScore = when {
                spo2Min < SPO2_CRITICAL_MIN -> 1.0f
                spo2Min < SPO2_WARNING_MIN ->
                    (SPO2_WARNING_MIN - spo2Min) / (SPO2_WARNING_MIN - SPO2_CRITICAL_MIN)
                else -> 0f
            }
            score = maxOf(score, minScore)
        }

        return score.coerceIn(0f, 1f)
    }

    private fun calculateHRVScore(input: SensorDataInput): Float {
        val hrvSdnn = input.hrvSdnn
        return when {
            hrvSdnn < HRV_NORMAL_MIN -> (HRV_NORMAL_MIN - hrvSdnn) / HRV_NORMAL_MIN
            hrvSdnn >= HRV_OPTIMAL_MIN -> 0f
            else -> (HRV_OPTIMAL_MIN - hrvSdnn) / (HRV_OPTIMAL_MIN - HRV_NORMAL_MIN) * 0.3f
        }.coerceIn(0f, 1f)
    }

    private fun calculateTemperatureScore(input: SensorDataInput): Float {
        val temp = input.temp
        return when {
            temp < TEMP_NORMAL_MIN -> (TEMP_NORMAL_MIN - temp) / 2f
            temp > TEMP_NORMAL_MAX -> (temp - TEMP_NORMAL_MAX) / 2f
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    private fun estimateConfidence(
        anomalyScore: Float,
        source: InferenceSource,
        componentRisk: ComponentRiskScores
    ): Float {
        val margin = (abs(anomalyScore - 0.5f) * 2f).coerceIn(0f, 1f)
        val signal = maxOf(
            componentRisk.heartRate,
            componentRisk.bloodOxygen,
            componentRisk.hrv,
            componentRisk.temperature
        ).coerceIn(0f, 1f)

        val raw = if (source == InferenceSource.TFLITE) {
            0.52f + (margin * 0.26f) + (signal * 0.20f)
        } else {
            0.40f + (margin * 0.18f) + (signal * 0.14f)
        }

        val cap = if (source == InferenceSource.TFLITE) 0.94f else 0.76f
        return raw.coerceIn(0.35f, cap)
    }

    private fun detectPrimaryFactor(componentRisk: ComponentRiskScores): AnomalyPrimaryFactor {
        val weighted = linkedMapOf(
            AnomalyPrimaryFactor.BLOOD_OXYGEN to (componentRisk.bloodOxygen * WEIGHT_SPO2),
            AnomalyPrimaryFactor.HEART_RATE to (componentRisk.heartRate * WEIGHT_HR),
            AnomalyPrimaryFactor.HRV to (componentRisk.hrv * WEIGHT_HRV),
            AnomalyPrimaryFactor.TEMPERATURE to (componentRisk.temperature * WEIGHT_TEMP)
        )

        val sorted = weighted.entries.sortedByDescending { it.value }
        if (sorted.isEmpty() || sorted.first().value < 0.05f) {
            return AnomalyPrimaryFactor.NONE
        }

        if (sorted.size > 1 && (sorted[0].value - sorted[1].value) < 0.03f && sorted[1].value > 0.08f) {
            return AnomalyPrimaryFactor.MIXED
        }

        return sorted.first().key
    }

    private fun interpretResult(
        anomalyScore: Float,
        heartRate: Float,
        spo2: Float,
        hrvSdnn: Float,
        temp: Float
    ): Triple<Boolean, AnomalyLevel, String> {
        val issues = mutableListOf<String>()

        if (spo2 < SPO2_CRITICAL_MIN) {
            issues.add("血氧严重偏低(${spo2.toInt()}%)")
        } else if (spo2 < SPO2_WARNING_MIN) {
            issues.add("血氧偏低(${spo2.toInt()}%)")
        }

        if (heartRate < HR_CRITICAL_MIN) {
            issues.add("心动过缓(${heartRate.toInt()} bpm)")
        } else if (heartRate > HR_CRITICAL_MAX) {
            issues.add("心动过速(${heartRate.toInt()} bpm)")
        } else if (heartRate < HR_NORMAL_MIN) {
            issues.add("心率偏低(${heartRate.toInt()} bpm)")
        } else if (heartRate > HR_NORMAL_MAX) {
            issues.add("心率偏高(${heartRate.toInt()} bpm)")
        }

        if (hrvSdnn < HRV_NORMAL_MIN) {
            issues.add("HRV偏低")
        }

        if (temp < TEMP_NORMAL_MIN || temp > TEMP_NORMAL_MAX) {
            issues.add("体温异常(${String.format("%.1f", temp)}℃)")
        }

        return when {
            anomalyScore > 0.7f -> Triple(true, AnomalyLevel.CRITICAL, "严重异常: ${issues.joinToString("、")}")
            anomalyScore > 0.4f -> Triple(true, AnomalyLevel.WARNING, "需要关注: ${issues.joinToString("、")}")
            anomalyScore > 0.2f -> {
                val detail = if (issues.isEmpty()) "睡眠恢复偏弱" else issues.joinToString("、")
                Triple(true, AnomalyLevel.MILD, "轻微异常: $detail")
            }
            else -> Triple(false, AnomalyLevel.NORMAL, "状态正常")
        }
    }

    fun getAverageInferenceTime(): Float {
        return if (inferenceTimeHistory.isEmpty()) 0f else inferenceTimeHistory.average().toFloat()
    }

    fun getPerformanceStats(): Map<String, Float> {
        if (inferenceTimeHistory.isEmpty()) return emptyMap()
        return mapOf(
            "avg" to inferenceTimeHistory.average().toFloat(),
            "min" to (inferenceTimeHistory.minOrNull() ?: 0f),
            "max" to (inferenceTimeHistory.maxOrNull() ?: 0f),
            "count" to inferenceTimeHistory.size.toFloat()
        )
    }

    override fun close() {
        interpreter?.close()
    }
}

data class SensorDataInput(
    val heartRate: Float,
    val heartRateMin: Float = heartRate - 5f,
    val heartRateMax: Float = heartRate + 5f,
    val heartRateStd: Float = 3f,
    val spo2: Float,
    val spo2Min: Float = spo2 - 1f,
    val spo2Max: Float = spo2 + 1f,
    val hrvSdnn: Float,
    val hrvRmssd: Float = hrvSdnn * 0.8f,
    val hrvPnn50: Float = hrvSdnn * 0.5f,
    val temp: Float = 36.5f
)

data class PredictionResult(
    val anomalyScore: Float,
    val isAbnormal: Boolean,
    val level: AnomalyLevel,
    val message: String,
    val inferenceTimeMs: Float,
    val source: InferenceSource = InferenceSource.ERROR,
    val confidence: Float = 0f,
    val primaryFactor: AnomalyPrimaryFactor = AnomalyPrimaryFactor.NONE,
    val componentRisk: ComponentRiskScores = ComponentRiskScores(),
    val details: Map<String, Float> = emptyMap()
)

data class ComponentRiskScores(
    val heartRate: Float = 0f,
    val bloodOxygen: Float = 0f,
    val hrv: Float = 0f,
    val temperature: Float = 0f
)

enum class InferenceSource {
    TFLITE,
    RULE_FALLBACK,
    ERROR
}

enum class AnomalyPrimaryFactor {
    NONE,
    HEART_RATE,
    BLOOD_OXYGEN,
    HRV,
    TEMPERATURE,
    MIXED
}

enum class AnomalyLevel {
    NORMAL,
    MILD,
    WARNING,
    CRITICAL,
    UNKNOWN,
    ERROR
}
