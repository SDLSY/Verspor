package com.example.newstart.bluetooth

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

/**
 * Clevering/Hi90B 命令发送器（按协议V1.02）
 * 
 * 帧格式：
 * [0-1] 0xAA 0x55
 * [2-3] LenH LenL （高字节在前），Len = CMD(1)+DATA(n)+CHK(1) 的总字节数
 * [4]   CMD
 * [5..] DATA
 * [last] CHK = LenH ^ LenL ^ CMD ^ DATA...
 */
class CleveringCommandSender(
    private val writer: (ByteArray) -> Boolean
) {

    companion object {
        private const val TAG = "CleveringCmd"
        private const val HEADER_0: Byte = 0xAA.toByte()
        private const val HEADER_1: Byte = 0x55.toByte()

        // CMD
        const val CMD_QUERY_FIRMWARE: Byte = 0x20
        const val CMD_SET_CLOCK: Byte = 0x21
        const val CMD_QUERY_BATTERY: Byte = 0x22
        const val CMD_SET_WORK_PARAMS: Byte = 0x30
        const val CMD_READ_WORK_PARAMS: Byte = 0x31
        const val CMD_SET_INSTANT_MODE: Byte = 0x32
        const val CMD_QUERY_HISTORY_SIZE: Byte = 0x33
        const val CMD_START_STOP_TRANSFER: Byte = 0x34

        // Upload
        const val CMD_UPLOAD_REGULAR: Byte = 0x50
        const val CMD_UPLOAD_INSTANT: Byte = 0x52
    }

    data class WorkParamItem(
        val item: Int,
        val sampleChannel: Int,
        val sampleRate: Int,
        val startHour: Int,
        val startMinute: Int,
        val testTimeLengthMinutes: Long,
        val periodMs: Long,
        val durationMs: Long
    )

    private fun buildFrame(cmd: Byte, data: ByteArray = byteArrayOf()): ByteArray {
        val len = 1 + data.size + 1 // CMD + DATA + CHK
        val lenH = ((len ushr 8) and 0xFF).toByte()
        val lenL = (len and 0xFF).toByte()

        var chk = (lenH.toInt() and 0xFF) xor (lenL.toInt() and 0xFF) xor (cmd.toInt() and 0xFF)
        data.forEach { b -> chk = chk xor (b.toInt() and 0xFF) }

        val frame = ByteArray(2 + 2 + 1 + data.size + 1)
        var idx = 0
        frame[idx++] = HEADER_0
        frame[idx++] = HEADER_1
        frame[idx++] = lenH
        frame[idx++] = lenL
        frame[idx++] = cmd
        System.arraycopy(data, 0, frame, idx, data.size)
        idx += data.size
        frame[idx] = (chk and 0xFF).toByte()
        return frame
    }

    private fun send(cmd: Byte, data: ByteArray = byteArrayOf()): Boolean {
        val frame = buildFrame(cmd, data)
        Log.d(TAG, "send cmd=0x${String.format("%02X", cmd)} len=${frame.size}")
        return writer(frame)
    }

    fun queryFirmware(): Boolean = send(CMD_QUERY_FIRMWARE)

    fun queryBattery(): Boolean = send(CMD_QUERY_BATTERY)

    fun readWorkParams(): Boolean = send(CMD_READ_WORK_PARAMS)

    fun queryHistorySize(): Boolean = send(CMD_QUERY_HISTORY_SIZE)

    /**
     * 设置时钟(0x21)
     * data: 7字节：年(两位,如2026->0x1A?) 协议写“16进制数据”，常见做法为 year-2000
     */
    fun setClock(calendar: Calendar = Calendar.getInstance()): Boolean {
        val year = (calendar.get(Calendar.YEAR) - 2000).coerceIn(0, 255)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val week = ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1 // 1-7，周一=1
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
        return send(CMD_SET_CLOCK, data)
    }

    /**
     * 启动/停止数据传输(0x34)
     * 1=启动,0=停止
     */
    fun startStopTransfer(start: Boolean): Boolean {
        val data = byteArrayOf(if (start) 0x01 else 0x00)
        return send(CMD_START_STOP_TRANSFER, data)
    }

    /**
     * 设置内部采集工作参数(0x30)
     * 支持最多8组，这里传入若干组
     * 结构体字段：
     * uint16 item
     * uint16 sample_channel
     * uint16 sample_rate
     * uint8  start_time_hour
     * uint8  start_time_minute
     * uint32 test_time_length (minutes)
     * uint32 period (ms)
     * uint32 duration (ms)
     * 
     * 字节序：高字节在前（big-endian）
     */
    fun setWorkParams(items: List<WorkParamItem>): Boolean {
        val safeItems = items.take(8)
        val buf = ByteBuffer.allocate(safeItems.size * (2 + 2 + 2 + 1 + 1 + 4 + 4 + 4))
        buf.order(ByteOrder.BIG_ENDIAN)

        safeItems.forEach { itItem ->
            buf.putShort(itItem.item.toShort())
            buf.putShort(itItem.sampleChannel.toShort())
            buf.putShort(itItem.sampleRate.toShort())
            buf.put(itItem.startHour.toByte())
            buf.put(itItem.startMinute.toByte())
            buf.putInt(itItem.testTimeLengthMinutes.toInt())
            buf.putInt(itItem.periodMs.toInt())
            buf.putInt(itItem.durationMs.toInt())
        }

        return send(CMD_SET_WORK_PARAMS, buf.array())
    }

    /**
     * 即时采集模式(0x32)
     * data: item(2) + sample_channel(2) + sample_rate(2) + duration(4)
     */
    fun setInstantMode(item: Int, sampleChannel: Int, sampleRate: Int, durationMs: Long): Boolean {
        val buf = ByteBuffer.allocate(2 + 2 + 2 + 4)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.putShort(item.toShort())
        buf.putShort(sampleChannel.toShort())
        buf.putShort(sampleRate.toShort())
        buf.putInt(durationMs.toInt())
        return send(CMD_SET_INSTANT_MODE, buf.array())
    }

    /**
     * 一键按“睡眠监测推荐策略”下发参数
     */
    fun setDefaultSleepMonitoringParams(): Boolean {
        val list = listOf(
            // 心率+血氧：每5分钟采集30秒
            WorkParamItem(
                item = 3,
                sampleChannel = 3,
                sampleRate = 5,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24 * 60L,
                periodMs = 300_000L,
                durationMs = 30_000L
            ),
            // 体温：每10分钟采集一次（体温忽略channel/rate/duration）
            WorkParamItem(
                item = 5,
                sampleChannel = 0,
                sampleRate = 0,
                startHour = 0,
                startMinute = 0,
                testTimeLengthMinutes = 24 * 60L,
                periodMs = 600_000L,
                durationMs = 0L
            ),
            // PPG：23:00-07:00（480分钟）
            WorkParamItem(
                item = 1,
                sampleChannel = 3,
                sampleRate = 50,
                startHour = 23,
                startMinute = 0,
                testTimeLengthMinutes = 480L,
                periodMs = 0L,
                durationMs = 0L
            )
        )
        return setWorkParams(list)
    }
}
