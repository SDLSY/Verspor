package com.example.newstart.ui.avatar

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

class AvatarSpeechPlaybackController(
    private val context: Context
) {

    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null
    private var deleteOnCleanup: Boolean = false

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun play(
        text: String,
        audioDataUrl: String,
        onStart: () -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        stop()

        try {
            val file = resolvePlaybackFile(audioDataUrl)
            tempFile = file

            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    onStart()
                    it.start()
                }
                setOnCompletionListener {
                    cleanupPlayer()
                    onComplete()
                }
                setOnErrorListener { _, _, _ ->
                    cleanupPlayer()
                    onError(IllegalStateException("avatar speech playback failed for text=${text.take(32)}"))
                    true
                }
                prepareAsync()
            }
        } catch (t: Throwable) {
            cleanupPlayer()
            onError(t)
        }
    }

    fun stop() {
        cleanupPlayer()
    }

    private fun cleanupPlayer() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
        if (deleteOnCleanup) {
            tempFile?.runCatching { delete() }
        }
        tempFile = null
        deleteOnCleanup = false
    }

    private fun resolvePlaybackFile(audioSource: String): File {
        if (audioSource.startsWith("file://")) {
            deleteOnCleanup = false
            return File(audioSource.removePrefix("file://"))
        }

        val bytes = decodeDataUrl(audioSource)
        val file = File.createTempFile("avatar_tts_", ".mp3", context.cacheDir)
        FileOutputStream(file).use { it.write(bytes) }
        deleteOnCleanup = true
        return file
    }

    private fun decodeDataUrl(value: String): ByteArray {
        val prefix = "base64,"
        val base64 = value.substringAfter(prefix, "")
        require(base64.isNotBlank()) { "invalid audio data url" }
        return Base64.decode(base64, Base64.DEFAULT)
    }
}
