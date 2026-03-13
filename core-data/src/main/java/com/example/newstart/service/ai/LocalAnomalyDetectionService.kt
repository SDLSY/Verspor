package com.example.newstart.service.ai

import android.content.Context
import com.example.newstart.ml.PredictionResult
import com.example.newstart.ml.SensorDataInput
import com.example.newstart.ml.SleepAnomalyDetector
import java.io.Closeable

class LocalAnomalyDetectionService(
    context: Context
) : Closeable {

    private val detector = SleepAnomalyDetector(context)

    fun predict(input: SensorDataInput): PredictionResult {
        return detector.predict(input)
    }

    override fun close() {
        detector.close()
    }
}
