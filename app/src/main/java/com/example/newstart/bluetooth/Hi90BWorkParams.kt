package com.example.newstart.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 0x30/0x31 工作参数结构（最多8组）。
 */
data class Hi90BWorkParam(
    val item: Int,
    val sampleChannel: Int,
    val sampleRate: Int,
    val startHour: Int,
    val startMinute: Int,
    val testTimeLengthMinutes: Long,
    val periodMs: Long,
    val durationMs: Long
)

object Hi90BWorkParamsCodec {

    /**
     * 编码为0x30 data区域（不含帧头/Len/CMD/CHK）
     */
    fun encode(params: List<Hi90BWorkParam>): ByteArray {
        val capped = params.take(8)
        val unitSize = 20
        val buf = ByteBuffer.allocate(capped.size * unitSize).order(ByteOrder.BIG_ENDIAN)
        for (p in capped) {
            buf.putShort(p.item.toShort())
            buf.putShort(p.sampleChannel.toShort())
            buf.putShort(p.sampleRate.toShort())
            buf.put(p.startHour.toByte())
            buf.put(p.startMinute.toByte())
            buf.putInt(p.testTimeLengthMinutes.toInt())
            buf.putInt(p.periodMs.toInt())
            buf.putInt(p.durationMs.toInt())
        }
        return buf.array()
    }

    /**
     * 解码0x31 data区域
     */
    fun decode(data: ByteArray): List<Hi90BWorkParam> {
        if (data.isEmpty()) return emptyList()
        val unitSize = 20
        val count = data.size / unitSize
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val out = ArrayList<Hi90BWorkParam>(count)
        for (i in 0 until count) {
            if (buf.remaining() < unitSize) break
            val item = buf.short.toInt() and 0xFFFF
            val channel = buf.short.toInt() and 0xFFFF
            val rate = buf.short.toInt() and 0xFFFF
            val hour = buf.get().toInt() and 0xFF
            val minute = buf.get().toInt() and 0xFF
            val testLen = buf.int.toLong() and 0xFFFFFFFFL
            val period = buf.int.toLong() and 0xFFFFFFFFL
            val duration = buf.int.toLong() and 0xFFFFFFFFL
            out.add(
                Hi90BWorkParam(
                    item = item,
                    sampleChannel = channel,
                    sampleRate = rate,
                    startHour = hour,
                    startMinute = minute,
                    testTimeLengthMinutes = testLen,
                    periodMs = period,
                    durationMs = duration
                )
            )
        }
        return out
    }

    fun debugString(params: List<Hi90BWorkParam>): String {
        if (params.isEmpty()) return "(empty)"
        return params.joinToString("\n") { p ->
            "item=${p.item}, channel=0x${p.sampleChannel.toString(16)}, rate=${p.sampleRate}, start=${p.startHour}:${p.startMinute}, testLenMin=${p.testTimeLengthMinutes}, periodMs=${p.periodMs}, durationMs=${p.durationMs}"
        }
    }
}
