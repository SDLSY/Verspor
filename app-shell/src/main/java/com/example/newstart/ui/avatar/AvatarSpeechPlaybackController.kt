package com.example.newstart.ui.avatar

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

class AvatarSpeechPlaybackController(
    private val context: Context
) {

    private sealed class PlaybackSource {
        data class FileSource(val file: File, val deleteOnCleanup: Boolean) : PlaybackSource()
        data class UriSource(val uri: Uri) : PlaybackSource()
    }

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
            val source = resolvePlaybackSource(audioDataUrl)
            mediaPlayer = MediaPlayer().apply {
                when (source) {
                    is PlaybackSource.FileSource -> {
                        tempFile = source.file
                        deleteOnCleanup = source.deleteOnCleanup
                        setDataSource(source.file.absolutePath)
                    }

                    is PlaybackSource.UriSource -> {
                        tempFile = null
                        deleteOnCleanup = false
                        setDataSource(context, source.uri)
                    }
                }

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

    private fun resolvePlaybackSource(audioSource: String): PlaybackSource {
        if (audioSource.startsWith("android.resource://")) {
            return PlaybackSource.UriSource(Uri.parse(audioSource))
        }
        if (audioSource.startsWith("file://")) {
            return PlaybackSource.FileSource(
                file = File(audioSource.removePrefix("file://")),
                deleteOnCleanup = false
            )
        }

        val bytes = decodeDataUrl(audioSource)
        val file = File.createTempFile("avatar_tts_", ".mp3", context.cacheDir)
        FileOutputStream(file).use { it.write(bytes) }
        return PlaybackSource.FileSource(file = file, deleteOnCleanup = true)
    }

    private fun decodeDataUrl(value: String): ByteArray {
        val prefix = "base64,"
        val base64 = value.substringAfter(prefix, "")
        require(base64.isNotBlank()) { "invalid audio data url" }
        return Base64.decode(base64, Base64.DEFAULT)
    }
}
