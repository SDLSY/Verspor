package com.example.newstart.ui.avatar

interface AvatarAudioHost {
    fun speakAvatarAudio(text: String, audioDataUrl: String)

    fun stopAvatarAudio()
}
