package com.example.newstart.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

/**
 * Hi90B command builder.
 * Builds command frames according to protocol v1.02.
 */
object Hi90BCommandBuilder {

    private const val HEADER_1 = 0xAA.toByte()
    private const val HEADER_2 = 0x55.toByte()

    /**
     * Build a complete command frame.
     * @param cmd command byte
     * @param data payload bytes
     * @return encoded command frame
     */
    private fun buildCommand(cmd: Byte, data: ByteArray = byteArrayOf()): ByteArray {
        // Len = CMD(1) + DATA(n) + CHK(1)
        val len = 2 + data.size

        // Total = AA55(2) + Len(2) + CMD(1) + DATA(n) + CHK(1)
        val total = 6 + data.size

        val buffer = ByteBuffer.allocate(total).apply {
            order(ByteOrder.BIG_ENDIAN)
            put(HEADER_1)
            put(HEADER_2)
            putShort(len.toShort())
            put(cmd)
            if (data.isNotEmpty()) put(data)
        }

        // XOR checksum over LenH LenL CMD DATA (excluding AA55 header and CHK itself)
        var checksum = 0
        val arr = buffer.array()
        for (i in 2 until 5 + data.size) {
            checksum = checksum xor (arr[i].toInt() and 0xFF)
        }
        buffer.put((checksum and 0xFF).toByte())

        return buffer.array()
    }

    // ========= Commands ========= //

    /** 0x10: enter storage mode. */
    fun enterStorageMode(): ByteArray = buildCommand(0x10)

    /** 0x11: reset device. */
    fun resetDevice(): ByteArray = buildCommand(0x11)

    /** 0x20: query firmware version. */
    fun queryFirmwareVersion(): ByteArray = buildCommand(0x20)

    /** 0x21: set clock. */
    fun setClock(): ByteArray {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR) - 2000
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        var week = calendar.get(Calendar.DAY_OF_WEEK) // SUNDAY=1, SATURDAY=7
        week = if (week == 1) 7 else week - 1 // MONDAY=1, SUNDAY=7
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        val data = byteArrayOf(
            year.toByte(),
            month.toByte(),
            day.toByte(),
            week.toByte(),
            hour.toByte(),
            minute.toByte(),
            second.toByte()
        )
        return buildCommand(0x21, data)
    }

    /** 0x22: query battery level. */
    fun queryBatteryLevel(): ByteArray = buildCommand(0x22)

    /** 0x30: set work parameters for data collection. */
    fun setWorkParams(params: List<Hi90BWorkParam>): ByteArray {
        val data = Hi90BWorkParamsCodec.encode(params)
        return buildCommand(0x30, data)
    }

    /** 0x31: read current work parameters. */
    fun readWorkParams(): ByteArray = buildCommand(0x31)

    /** 0x33: query history data size. */
    fun queryHistorySize(): ByteArray = buildCommand(0x33)

    /**
     * 0x34: control history data transmission.
     * @param start true to start, false to stop
     */
    fun controlDataTransmission(start: Boolean): ByteArray {
        val data = byteArrayOf(if (start) 0x01 else 0x00)
        return buildCommand(0x34, data)
    }

    /**
     * 0x32: configure instant collection mode.
     * @param item data item id
     * @param sampleChannel channel id (same definition as 0x30)
     * @param sampleRate sample rate (same definition as 0x30)
     * @param durationMs collection duration in milliseconds
     */
    fun setInstantCollectionMode(
        item: Int,
        sampleChannel: Int = 0,
        sampleRate: Int = 0,
        durationMs: Int
    ): ByteArray {
        val buffer = ByteBuffer.allocate(10).apply {
            order(ByteOrder.BIG_ENDIAN)
            putShort(item.toShort())
            putShort(sampleChannel.toShort())
            putShort(sampleRate.toShort())
            putInt(durationMs)
        }
        return buildCommand(0x32, buffer.array())
    }
}
