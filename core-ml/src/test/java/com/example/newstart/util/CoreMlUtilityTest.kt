package com.example.newstart.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreMlUtilityTest {

    @Test
    fun medicalReportParser_extracts_glucose_and_blood_pressure() {
        val text = """
            空腹血糖 5.6 mmol/L
            血压 128/82 mmHg
        """.trimIndent()

        val parsed = MedicalReportParser.parse(text).associateBy { it.metricCode }

        assertEquals(3, parsed.size)
        assertEquals(5.6f, parsed.getValue("GLU").value)
        assertEquals(false, parsed.getValue("GLU").isAbnormal)
        assertEquals(128f, parsed.getValue("SBP").value)
        assertEquals(82f, parsed.getValue("DBP").value)
    }

    @Test
    fun medicalReportParser_marks_out_of_range_metrics_as_abnormal() {
        val text = """
            FPG 7.2 mmol/L
            BP 145/92
        """.trimIndent()

        val parsed = MedicalReportParser.parse(text).associateBy { it.metricCode }

        assertTrue(parsed.getValue("GLU").isAbnormal)
        assertTrue(parsed.getValue("SBP").isAbnormal)
        assertTrue(parsed.getValue("DBP").isAbnormal)
    }

    @Test
    fun medicalReportParser_returns_empty_for_unrecognized_text() {
        val parsed = MedicalReportParser.parse("未见可识别结构化指标，仅含自由描述。")

        assertTrue(parsed.isEmpty())
    }

    @Test
    fun temperatureMapper_returns_zero_for_invalid_input_and_smooths_cold_values() {
        assertEquals(0f, TemperatureMapper.mapOuterToBody(Float.NaN), 0.0001f)

        val mapped = TemperatureMapper.mapOuterToBody(
            outerTemp = 26f,
            lastMapped = 36.6f
        )

        assertTrue(mapped in 36.1f..36.3f)
    }
}
