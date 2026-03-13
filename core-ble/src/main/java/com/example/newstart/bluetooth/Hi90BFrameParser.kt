package com.example.newstart.bluetooth

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hi90B 协议帧解析器：按 AA55 + Len(HL) 聚帧，并校验 XOR。
 * Len 为 CMD+DATA+CHK 总字节数。
 */
class Hi90BFrameParser {

    data class Frame(
        val cmd: Int,
        val data: ByteArray,
        val raw: ByteArray
    )

    private val buffer = ByteArrayOutput()

    fun reset() {
        buffer.clear()
    }

    fun feed(input: ByteArray): List<Frame> {
        buffer.append(input)
        val out = mutableListOf<Frame>()

        while (true) {
            val bytes = buffer.bytes()
            if (bytes.size < 5) break

            // 寻找帧头 AA55
            var start = -1
            for (i in 0 until bytes.size - 1) {
                if (bytes[i] == 0xAA.toByte() && bytes[i + 1] == 0x55.toByte()) {
                    start = i
                    break
                }
            }
            if (start < 0) {
                buffer.clear()
                break
            }
            if (start > 0) {
                buffer.discard(start)
            }

            val bytes2 = buffer.bytes()
            if (bytes2.size < 5) break

            val len = ByteBuffer.wrap(bytes2, 2, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            val total = 4 + len // AA55(2) + Len(2) + (CMD+DATA+CHK)
            if (len < 2) {
                // 非法长度，丢弃头两字节继续
                buffer.discard(2)
                continue
            }
            if (bytes2.size < total) break

            val frameRaw = bytes2.copyOfRange(0, total)
            buffer.discard(total)

            if (!validateXor(frameRaw)) {
                Log.w("Hi90BFrameParser", "XOR校验失败，丢弃帧: ${frameRaw.toHex()}")
                continue
            }

            val cmd = frameRaw[4].toInt() and 0xFF
            val dataLen = len - 2 // 去掉 CMD(1)+CHK(1)
            val data = if (dataLen > 0) frameRaw.copyOfRange(5, 5 + dataLen) else byteArrayOf()
            out.add(Frame(cmd = cmd, data = data, raw = frameRaw))
        }

        return out
    }

    private fun validateXor(frame: ByteArray): Boolean {
        // XOR 计算范围：LenH LenL CMD DATA (不含 AA55，不含 CHK)
        if (frame.size < 6) return false
        var xor = 0
        for (i in 2 until frame.size - 1) {
            xor = xor xor (frame[i].toInt() and 0xFF)
        }
        val chk = frame.last().toInt() and 0xFF
        return (xor and 0xFF) == chk
    }

    private class ByteArrayOutput {
        private var buf = ByteArray(0)

        fun append(b: ByteArray) {
            if (b.isEmpty()) return
            buf = buf + b
        }

        fun bytes(): ByteArray = buf

        fun discard(n: Int) {
            if (n <= 0) return
            buf = if (n >= buf.size) ByteArray(0) else buf.copyOfRange(n, buf.size)
        }

        fun clear() {
            buf = ByteArray(0)
        }
    }
}

internal fun ByteArray.toHex(): String = joinToString(" ") { String.format("%02X", it) }
