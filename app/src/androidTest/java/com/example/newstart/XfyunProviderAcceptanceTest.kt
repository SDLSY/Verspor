package com.example.newstart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.newstart.xfyun.XfyunConfig
import com.example.newstart.xfyun.asr.XfyunRaasrClient
import com.example.newstart.xfyun.asr.XfyunRtasrWsClient
import com.example.newstart.xfyun.ocr.XfyunOcrClient
import com.example.newstart.xfyun.spark.SparkChatMessage
import com.example.newstart.xfyun.spark.XfyunSparkLiteWsClient
import com.example.newstart.xfyun.spark.XfyunSparkXWsClient
import com.example.newstart.xfyun.speech.XfyunIatWsClient
import com.example.newstart.xfyun.speech.XfyunTtsWsClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class XfyunProviderAcceptanceTest {

    @Test
    fun tts_returns_audio_data_url() = runBlocking {
        assumeTrue(XfyunConfig.ttsCredentials.isReady)
        val result = XfyunTtsWsClient().synthesize("你好，我是 VesperO 桌面助手。")
        assertTrue(result.startsWith("data:audio/mpeg;base64,"))
        assertTrue(result.length > 512)
    }

    @Test
    fun spark_lite_returns_expected_probe_text() = runBlocking {
        assumeTrue(XfyunConfig.sparkLiteEndpoint.isReady)
        val reply = XfyunSparkLiteWsClient(XfyunConfig.sparkLiteEndpoint).chat(
            messages = listOf(
                SparkChatMessage("system", "你是联调助手。只回复已连接四个字，不要输出其他内容。"),
                SparkChatMessage("user", "请开始联调应答")
            )
        )
        assertTrue(reply.contains("已连接"))
    }

    @Test
    fun spark_x_returns_expected_probe_text() = runBlocking {
        assumeTrue(XfyunConfig.sparkXEndpoint.isReady)
        val reply = XfyunSparkXWsClient(XfyunConfig.sparkXEndpoint).chat(
            messages = listOf(
                SparkChatMessage("system", "你是联调助手。只回复长上下文已连接，不要输出其他内容。"),
                SparkChatMessage("user", "请开始联调应答")
            )
        )
        assertTrue(reply.contains("已连接"))
    }

    @Test
    fun ocr_recognizes_generated_medical_bitmap() {
        assumeTrue(XfyunConfig.ocrCredentials.isReady)
        val bitmap = createMedicalBitmap()
        val text = XfyunOcrClient().recognize(bitmap)
        assertTrue(
            text.contains("血氧") ||
                text.contains("96") ||
                text.contains("心率") ||
                text.contains("72")
        )
    }

    @Test
    fun iat_recognizes_tts_generated_pcm() = runBlocking {
        assumeTrue(XfyunConfig.iatCredentials.isReady && XfyunConfig.ttsCredentials.isReady)
        val pcm = XfyunTtsWsClient().synthesizePcm("今天睡眠一般，白天有点疲劳。")
        val text = XfyunIatWsClient().recognizePcm(
            pcm,
            hotWords = listOf("睡眠", "疲劳", "白天")
        )
        assertTrue(text.contains("睡眠") || text.contains("疲劳") || text.contains("白天"))
    }

    @Test
    fun rtasr_transcribes_tts_generated_pcm() = runBlocking {
        assumeTrue(XfyunConfig.rtasrCredentials.isReady && XfyunConfig.ttsCredentials.isReady)
        val pcm = XfyunTtsWsClient().synthesizePcm("这里是实时转写测试。")
        val text = XfyunRtasrWsClient().transcribePcm(pcm)
        assertTrue(text.contains("实时") || text.contains("转写") || text.contains("测试"))
    }

    @Test
    fun raasr_transcribes_uploaded_tts_audio_file() = runBlocking {
        assumeTrue(XfyunConfig.raasrCredentials.isReady && XfyunConfig.ttsCredentials.isReady)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataUrl = XfyunTtsWsClient().synthesize("这里是录音文件转写测试。")
        val mp3Bytes = Base64.decode(dataUrl.removePrefix("data:audio/mpeg;base64,"), Base64.DEFAULT)
        val file = File(context.cacheDir, "xfyun-raasr-acceptance.mp3")
        file.writeBytes(mp3Bytes)
        val client = XfyunRaasrClient()
        val orderId = client.upload(file)
        assertTrue(orderId.isNotBlank())
        val result = pollRaasrResult(client, orderId)
        assertTrue(result.contains("录音") || result.contains("文件") || result.contains("测试"))
    }

    private fun createMedicalBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(1600, 960, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 68f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 54f
        }
        var y = 120f
        canvas.drawText("体检报告", 80f, y, titlePaint)
        y += 110f
        canvas.drawText("姓名 张三", 80f, y, bodyPaint)
        y += 85f
        canvas.drawText("项目 血氧 96%", 80f, y, bodyPaint)
        y += 85f
        canvas.drawText("项目 心率 72 bpm", 80f, y, bodyPaint)
        y += 85f
        canvas.drawText("结论 睡眠恢复一般", 80f, y, bodyPaint)
        return bitmap
    }

    private fun pollRaasrResult(client: XfyunRaasrClient, orderId: String): String {
        repeat(20) {
            val raw = client.getResult(orderId)
            val root = JSONObject(raw)
            val content = root.optJSONObject("content")
            val result = content?.optString("orderResult").orEmpty()
            if (result.isNotBlank()) {
                return result
            }
            Thread.sleep(3000)
        }
        throw AssertionError("RAASR result was not ready in time")
    }
}
