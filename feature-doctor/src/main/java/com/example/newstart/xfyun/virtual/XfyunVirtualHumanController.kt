package com.example.newstart.xfyun.virtual

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.example.newstart.xfyun.XfyunConfig
import com.iflytek.aiui.AIUIAgent
import com.iflytek.aiui.AIUIConstant
import com.iflytek.aiui.AIUIEvent
import com.iflytek.aiui.AIUIListener
import com.iflytek.aiui.AIUIMessage
import com.iflytek.aiui.AIUISetting
import com.iflytek.aiui.demo.chat.ui.RoundedVideoView
import com.iflytek.aiui.demo.chat.vms.VMSParams
import com.iflytek.aiui.demo.chat.vms.VideoFillMode
import com.iflytek.aiui.demo.chat.vms.VideoMirrorType
import com.iflytek.aiui.demo.chat.vms.VideoRotation
import com.iflytek.aiui.demo.chat.vms.VideoStreamType
import com.iflytek.aiui.demo.chat.vms.VmsHolder
import com.iflytek.aiui.demo.chat.vms.VolumeType
import com.iflytek.xrtcsdk.conference.IXRTCCloudDef
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class XfyunVirtualHumanController(
    private val host: AppCompatActivity,
    private val container: ViewGroup,
    private val onTranscript: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {

    companion object {
        private const val TAG = "XfyunVirtualHuman"
        private const val CFG_PATH = "cfg/aiui_phone.cfg"
        private const val AIUI_VER = "3"
        private val HANDLE_GENERATOR = AtomicInteger(1000)
    }

    private var agent: AIUIAgent? = null
    private var roundedVideoView: RoundedVideoView? = null
    private var vmsHandleId: Int = 0
    private var speechWakeupEnabled: Boolean = false
    private val iatBuilder = StringBuilder(256)
    private val nlpBuilder = StringBuilder(512)

    fun isConfigured(): Boolean {
        return XfyunConfig.aiuiCredentials.isReady && XfyunConfig.defaultAvatarId.isNotBlank()
    }

    fun createIfNeeded() {
        if (agent != null) return
        require(isConfigured()) { "讯飞数字人凭据或 avatar_id 未配置" }

        Log.i(TAG, "createIfNeeded: scene=${XfyunConfig.aiuiScene}, avatar=${XfyunConfig.defaultAvatarId}")
        AIUISetting.setNetLogLevel(AIUISetting.LogLevel.debug)
        AIUISetting.setSystemInfo(AIUIConstant.KEY_SERIAL_NUM, buildDeviceId())

        initVms()
        VmsHolder.getInst().setXrtcListener(xrtcListener)

        agent = AIUIAgent.createAgent(host, buildAiuiParams(), listener)
        require(agent != null) { "创建讯飞数字人 Agent 失败" }
        postStatus("数字人已初始化")
    }

    fun startInteraction() {
        createIfNeeded()
        if (!speechWakeupEnabled) {
            agent?.sendMessage(AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null))
        }
        agent?.sendMessage(AIUIMessage(AIUIConstant.CMD_START_RECORD, 0, 0, "data_type=audio", null))
        postStatus("数字人正在聆听")
    }

    fun sendTextQuery(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) return false
        createIfNeeded()
        if (!speechWakeupEnabled) {
            agent?.sendMessage(AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null))
        }
        val params = "data_type=text,tag=text-query"
        val payload = normalized.toByteArray(StandardCharsets.UTF_8)
        val message = AIUIMessage(AIUIConstant.CMD_WRITE, 0, 0, params, payload)
        val sent = runCatching {
            agent?.sendMessage(message)
            true
        }.getOrElse {
            Log.e(TAG, "sendTextQuery failed", it)
            false
        }
        if (sent) {
            postTranscript("用户：$normalized")
            postStatus("数字人正在处理文本问题")
        } else {
            postStatus("文本问题发送失败")
        }
        return sent
    }

    fun stopInteraction() {
        agent?.sendMessage(AIUIMessage(AIUIConstant.CMD_STOP_RECORD, 0, 0, "data_type=audio", null))
        agent?.sendMessage(AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.PAUSE, 0, null, null))
        agent?.sendMessage(AIUIMessage(AIUIConstant.CMD_RESET_WAKEUP, 0, 0, null, null))
        agent?.sendMessage(AIUIMessage(AIUIConstant.CMD_DISCONNECT_SERVER, 0, 0, null, null))
        runCatching { VmsHolder.getInst().stop() }
        postStatus("数字人互动已停止")
    }

    fun destroy() {
        runCatching { stopInteraction() }
        if (vmsHandleId != 0) {
            runCatching { VmsHolder.getInst().destroyView(vmsHandleId) }
            vmsHandleId = 0
        }
        runCatching { VmsHolder.getInst().unInit() }
        agent?.destroy()
        agent = null
        roundedVideoView = null
        iatBuilder.setLength(0)
        nlpBuilder.setLength(0)
    }

    private fun initVms() {
        if (roundedVideoView == null) {
            roundedVideoView = RoundedVideoView(host)
        }
        if (container.id == View.NO_ID) {
            container.id = View.generateViewId()
        }
        val params = VMSParams.builder()
            .videoFillMode(VideoFillMode.VIDEO_RENDER_MODE_FIT)
            .videoRotation(VideoRotation.VIDEO_ROTATION_0)
            .videoMirrorType(VideoMirrorType.VIDEO_MIRROR_TYPE_DISABLE)
            .videoStreamType(VideoStreamType.VIDEO_STREAM_TYPE_BIG)
            .volumeType(VolumeType.SystemVolumeTypeVOIP)
            .vmsContainer(container)
            .customVideoView(roundedVideoView)
            .build()
        VmsHolder.getInst().init(host, params)
    }

    private fun buildAiuiParams(): String {
        val raw = host.assets.open(CFG_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(raw)
        json.getOrCreateObject("login").apply {
            put("appid", XfyunConfig.aiuiCredentials.appId)
            put("key", XfyunConfig.aiuiCredentials.apiKey)
            put("api_secret", XfyunConfig.aiuiCredentials.apiSecret)
        }
        json.getOrCreateObject("global").apply {
            put("scene", XfyunConfig.aiuiScene)
            put("aiui_ver", AIUI_VER)
        }
        val speech = json.getOrCreateObject("speech")
        speechWakeupEnabled = speech.optString("wakeup_mode").lowercase() != "off"
        speech.put("interact_mode", "continuous")

        json.getOrCreateObject("tts").apply {
            put("voice_name", XfyunConfig.defaultVoiceName)
            put("audio_encoding", "raw")
            put("data_encoding", "raw")
            put("buffer_time", 0)
            put("sample_rate", "16000")
            put("play_mode", "sdk")
        }
        json.getOrCreateObject("iat").put("data_encoding", "raw")
        json.getOrCreateObject("interact").put("interact_timeout", "-1")
        json.getOrCreateObject("log").put("debug_log", "1")

        val cbmparams = json.getOrCreateObject("cbmparams")
        cbmparams.getOrCreateObject("nlp").put("prompt", XfyunConfig.aiuiDoctorPrompt)
        cbmparams.put(
            "avatar",
            JSONObject().apply {
                put("width", 512)
                put("height", 512)
                put("avatar_id", XfyunConfig.defaultAvatarId)
            }
        )

        Log.d(TAG, "buildAiuiParams: doctor prompt injected, length=${XfyunConfig.aiuiDoctorPrompt.length}")

        json.getOrCreateObject("vad").apply {
            put("vad_enable", "0")
            remove("cloud_vad_eos")
            remove("cloud_vad_gap")
        }
        return json.toString()
    }

    private fun buildDeviceId(): String {
        return listOfNotNull(Build.BRAND, Build.MODEL, Build.DEVICE)
            .joinToString(separator = "-")
            .ifBlank { "vespero" }
    }

    private val listener = AIUIListener { event ->
        when (event.eventType) {
            AIUIConstant.EVENT_CONNECTED_TO_SERVER -> {
                postStatus("数字人已连接讯飞服务")
                if (vmsHandleId != 0) {
                    runCatching { VmsHolder.getInst().destroyView(vmsHandleId) }
                }
                vmsHandleId = HANDLE_GENERATOR.incrementAndGet()
            }

            AIUIConstant.EVENT_SERVER_DISCONNECTED -> postStatus("数字人与服务端连接已断开")
            AIUIConstant.EVENT_START_RECORD -> postStatus("数字人正在聆听")
            AIUIConstant.EVENT_STOP_RECORD -> postStatus("数字人已停止收音")
            AIUIConstant.EVENT_WAKEUP -> postStatus("数字人已唤醒")
            AIUIConstant.EVENT_SLEEP -> postStatus("数字人已休眠")
            AIUIConstant.EVENT_ERROR -> {
                Log.e(TAG, "AIUI event error: ${event.info}")
                postStatus("数字人运行异常：${event.info}")
            }

            AIUIConstant.EVENT_RESULT -> handleResult(event)
        }
    }

    private fun handleResult(event: AIUIEvent) {
        runCatching {
            val info = JSONObject(event.info)
            val dataArray = info.optJSONArray("data") ?: return
            for (index in 0 until dataArray.length()) {
                val data = dataArray.optJSONObject(index) ?: continue
                val params = data.optJSONObject("params") ?: continue
                val sub = params.optString("sub")
                val contentArray = data.optJSONArray("content") ?: continue
                processContent(sub, contentArray, event)
            }
        }.onFailure {
            Log.e(TAG, "handleResult failed", it)
            postStatus("数字人结果解析失败：${it.message}")
        }
    }

    private fun processContent(sub: String, contentArray: JSONArray, event: AIUIEvent) {
        for (index in 0 until contentArray.length()) {
            val content = contentArray.optJSONObject(index) ?: continue
            if (sub == "tts") {
                postStatus("数字人正在回答")
                continue
            }
            val raw = extractContent(content, event) ?: continue
            when (sub) {
                "iat" -> handleIat(raw)
                "nlp" -> handleNlp(raw)
                "event" -> handleEvent(raw)
                "cbm_vms", "sos_vms" -> handleVmsInfo(raw)
            }
        }
    }

    private fun extractContent(content: JSONObject, event: AIUIEvent): String? {
        val cntId = content.optString("cnt_id")
        if (cntId.isBlank()) {
            return content.optString("text").takeIf { it.isNotBlank() }
        }
        val rawBytes = event.data?.getByteArray(cntId) ?: return null
        return String(rawBytes, Charsets.UTF_8)
    }

    private fun handleIat(raw: String) {
        val root = JSONObject(raw)
        val text = root.optJSONObject("text") ?: return
        val wsArray = text.optJSONArray("ws") ?: return
        for (i in 0 until wsArray.length()) {
            val ws = wsArray.optJSONObject(i) ?: continue
            val cw = ws.optJSONArray("cw") ?: continue
            if (cw.length() > 0) {
                iatBuilder.append(cw.optJSONObject(0)?.optString("w").orEmpty())
            }
        }
        if (text.optBoolean("ls", false)) {
            val transcript = iatBuilder.toString().trim()
            iatBuilder.setLength(0)
            if (transcript.isNotBlank()) {
                postTranscript("用户：$transcript")
                postStatus("数字人正在思考")
            }
        }
    }

    private fun handleNlp(raw: String) {
        val root = JSONObject(raw)
        val nlp = root.optJSONObject("nlp") ?: return
        nlpBuilder.append(nlp.optString("text"))
        if (nlp.optInt("status", 0) == 2) {
            val reply = nlpBuilder.toString().trim()
            nlpBuilder.setLength(0)
            if (reply.isNotBlank()) {
                postTranscript("数字人医生：$reply")
                postStatus("数字人正在回答")
            }
        }
    }

    private fun handleEvent(raw: String) {
        val wrapper = JSONObject(raw)
        val eventObj = wrapper.optJSONObject("event") ?: return
        val textJsonStr = eventObj.optString("text")
        if (textJsonStr.isBlank()) return
        val payload = JSONObject(textJsonStr)
        when (payload.optString("type").lowercase()) {
            "vad" -> {
                when (payload.optString("key").lowercase()) {
                    "bos" -> postStatus("数字人正在聆听")
                    "eos" -> postStatus("数字人已收到你的语音")
                }
            }

            "stream_info", "driver_status", "stop", "pong" -> handleVmsInfo(payload.toString())
        }
    }

    private fun handleVmsInfo(raw: String) {
        val root = JSONObject(raw)
        val payload = extractVmsPayload(root) ?: return
        when (payload.optString("event_type").ifBlank { payload.optString("type") }) {
            "pong" -> Log.d(TAG, "vms pong")
            "stop" -> {
                runCatching { VmsHolder.getInst().stop() }
                postStatus("数字人视频流已停止")
            }

            "stream_info" -> {
                val streamUrl = payload.optString("stream_url")
                val extend = payload.optJSONObject("stream_extend")
                val appId = extend?.optString("appid").orEmpty().ifBlank { "1000000001" }
                val userSign = extend?.optString("user_sign").orEmpty()
                if (streamUrl.isBlank() || userSign.isBlank()) {
                    Log.w(TAG, "invalid vms stream info: $raw")
                    postStatus("数字人视频流地址无效")
                    return
                }
                if (vmsHandleId == 0) {
                    vmsHandleId = HANDLE_GENERATOR.incrementAndGet()
                }
                runCatching { VmsHolder.getInst().destroyView(vmsHandleId) }
                VmsHolder.getInst().start(streamUrl, appId, userSign, vmsHandleId)
                postStatus("数字人视频流已开始播放")
            }

            "driver_status" -> {
                when (payload.optInt("vmr_status", -1)) {
                    0 -> postStatus("数字人视频流准备中")
                    2 -> postStatus("数字人视频流播放中")
                }
            }
        }
    }

    private fun extractVmsPayload(root: JSONObject): JSONObject? {
        if (root.has("event_type") || root.has("type")) {
            return root
        }
        listOf("cbm_vms", "sos_vms").forEach { key ->
            val wrapper = root.optJSONObject(key) ?: return@forEach
            val nestedText = wrapper.optString("text")
            if (nestedText.isNotBlank()) {
                return JSONObject(nestedText)
            }
        }
        return null
    }

    private val xrtcListener = object : VmsHolder.XrtcListener {
        override fun onUserVideoAvailable(userId: String?, available: Boolean) {
            Log.i(TAG, "XRTC onUserVideoAvailable user=$userId available=$available")
            if (available) {
                postStatus("数字人视频帧已就绪")
            }
        }

        override fun onError(errCode: Int, errMsg: String?, extraInfo: Bundle?) {
            Log.e(TAG, "XRTC error code=$errCode msg=$errMsg")
            postStatus("数字人视频错误：$errCode ${errMsg.orEmpty()}")
        }

        override fun onFirstVideoFrame(userId: String?, streamType: Int, width: Int, height: Int) {
            Log.i(TAG, "XRTC first frame user=$userId type=$streamType size=${width}x$height")
            postStatus("数字人画面已显示")
        }

        override fun onNetworkQuality(
            localQuality: IXRTCCloudDef.IXRTCQuality?,
            remoteQuality: ArrayList<IXRTCCloudDef.IXRTCQuality>?
        ) {
            Log.d(TAG, "XRTC network local=${localQuality?.quality} remote=${remoteQuality?.firstOrNull()?.quality}")
        }

        override fun onConnectionLost() {
            postStatus("数字人视频连接丢失")
        }

        override fun onTryToReconnect() {
            postStatus("数字人视频正在重连")
        }

        override fun onConnectionRecovery() {
            postStatus("数字人视频连接已恢复")
        }

        override fun onEnterRoom(code: Int) {
            Log.i(TAG, "XRTC enter room code=$code")
        }

        override fun onExitRoom(code: Int) {
            Log.i(TAG, "XRTC exit room code=$code")
        }

        override fun onRenderVideoFrame(
            userId: String?,
            streamType: Int,
            frame: IXRTCCloudDef.IXRTCVideoFrame?
        ) = Unit
    }

    private fun postTranscript(text: String) {
        host.runOnUiThread {
            onTranscript(text)
        }
    }

    private fun postStatus(text: String) {
        host.runOnUiThread {
            onStatus(text)
        }
    }
}

private fun JSONObject.getOrCreateObject(key: String): JSONObject {
    return optJSONObject(key) ?: JSONObject().also { put(key, it) }
}
