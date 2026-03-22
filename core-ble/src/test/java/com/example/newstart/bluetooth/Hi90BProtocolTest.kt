package com.example.newstart.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Hi90BProtocolTest {

    @Test
    fun queryBatteryLevel_builds_expected_frame() {
        val frame = Hi90BCommandBuilder.queryBatteryLevel()

        assertEquals(6, frame.size)
        assertEquals(0xAA, frame[0].toUnsignedInt())
        assertEquals(0x55, frame[1].toUnsignedInt())
        assertEquals(0x00, frame[2].toUnsignedInt())
        assertEquals(0x02, frame[3].toUnsignedInt())
        assertEquals(0x22, frame[4].toUnsignedInt())
        assertEquals(expectedChecksum(frame), frame.last().toUnsignedInt())
    }

    @Test
    fun frameParser_parses_valid_frame_after_garbage_prefix() {
        val parser = Hi90BFrameParser()
        val payload = byteArrayOf(0x12, 0x34, 0x56) + Hi90BCommandBuilder.queryFirmwareVersion()

        val frames = parser.feed(payload)

        assertEquals(1, frames.size)
        assertEquals(0x20, frames.first().cmd)
        assertTrue(frames.first().data.isEmpty())
    }

    @Test
    fun frameParser_waits_until_frame_is_complete() {
        val parser = Hi90BFrameParser()
        val frame = Hi90BCommandBuilder.controlDataTransmission(true)
        val splitIndex = frame.size / 2

        val first = parser.feed(frame.copyOfRange(0, splitIndex))
        val second = parser.feed(frame.copyOfRange(splitIndex, frame.size))

        assertTrue(first.isEmpty())
        assertEquals(1, second.size)
        assertEquals(0x34, second.first().cmd)
        assertEquals(1, second.first().data.size)
        assertEquals(0x01, second.first().data[0].toUnsignedInt())
    }

    @Test
    fun frameParser_discards_invalid_checksum_frame() {
        val parser = Hi90BFrameParser()
        val frame = Hi90BCommandBuilder.queryFirmwareVersion().copyOf()
        frame[frame.lastIndex] = 0x00

        val frames = parser.feed(frame)

        assertTrue(frames.isEmpty())
    }

    private fun expectedChecksum(frame: ByteArray): Int {
        var checksum = 0
        for (i in 2 until frame.lastIndex) {
            checksum = checksum xor frame[i].toUnsignedInt()
        }
        return checksum and 0xFF
    }

    private fun Byte.toUnsignedInt(): Int = toInt() and 0xFF
}
