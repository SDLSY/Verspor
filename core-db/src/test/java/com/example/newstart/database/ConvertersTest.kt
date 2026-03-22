package com.example.newstart.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun timestamp_round_trip_preserves_date_value() {
        val original = Date(1_710_000_000_000L)

        val timestamp = converters.dateToTimestamp(original)
        val restored = converters.fromTimestamp(timestamp)

        assertEquals(original, restored)
    }

    @Test
    fun string_list_round_trip_preserves_values() {
        val original = listOf("hrv", "spo2", "temperature")

        val encoded = converters.stringListToString(original)
        val restored = converters.fromStringList(encoded)

        assertEquals(original, restored)
    }

    @Test
    fun null_and_empty_inputs_are_handled_safely() {
        assertNull(converters.fromTimestamp(null))
        assertNull(converters.fromStringList(null))
        assertEquals("[]", converters.stringListToString(emptyList()))
        assertEquals("null", converters.stringListToString(null))
    }
}
