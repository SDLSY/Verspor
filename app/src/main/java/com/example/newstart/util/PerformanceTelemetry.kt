package com.example.newstart.util

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object PerformanceTelemetry {

    private const val TAG = "PerformanceTelemetry"
    private val samples = CopyOnWriteArrayList<PerfSample>()

    data class PerfSample(
        val timestampMs: Long,
        val metric: String,
        val value: Double,
        val unit: String,
        val attributes: Map<String, String>
    )

    fun nowElapsedMs(): Long = SystemClock.elapsedRealtime()

    fun record(metric: String, value: Double, unit: String, attributes: Map<String, String> = emptyMap()) {
        val sample = PerfSample(
            timestampMs = System.currentTimeMillis(),
            metric = metric,
            value = value,
            unit = unit,
            attributes = attributes
        )
        samples += sample
        Log.d(TAG, "$metric=${String.format(Locale.US, "%.3f", value)} $unit $attributes")
    }

    fun recordDuration(metric: String, startElapsedMs: Long, attributes: Map<String, String> = emptyMap()) {
        val duration = (nowElapsedMs() - startElapsedMs).coerceAtLeast(0L).toDouble()
        record(metric = metric, value = duration, unit = "ms", attributes = attributes)
    }

    fun exportCsv(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "performance-reports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "perf_report_$ts.csv")

        val header = "timestamp_ms,metric,value,unit,attributes\n"
        val rows = buildString {
            append(header)
            samples.forEach { sample ->
                append(sample.timestampMs)
                append(',')
                append(csvEscape(sample.metric))
                append(',')
                append(String.format(Locale.US, "%.4f", sample.value))
                append(',')
                append(csvEscape(sample.unit))
                append(',')
                append(csvEscape(sample.attributes.entries.joinToString(";") { "${it.key}=${it.value}" }))
                append('\n')
            }
        }
        file.writeText(rows, Charsets.UTF_8)
        Log.i(TAG, "performance report exported: ${file.absolutePath}, count=${samples.size}")
        return file
    }

    private fun csvEscape(text: String): String {
        val safe = text.replace("\"", "\"\"")
        return "\"$safe\""
    }
}
