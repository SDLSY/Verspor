package com.example.newstart.intervention

import androidx.annotation.RawRes
import com.example.newstart.R

data class InterventionSessionContent(
    @RawRes val audioResId: Int,
    val audioTitle: String,
    val audioSubtitle: String,
    val audioSourceName: String,
    val audioSourceUrl: String,
    val audioLicense: String,
    val methodSourceName: String,
    val methodSourceUrl: String,
    val storyboardSteps: List<String>
)

object InterventionSessionContentCatalog {

    private val contents = mapOf(
        "BODY_SCAN_NSDR_10M" to InterventionSessionContent(
            audioResId = R.raw.body_scan_nsdr_audio,
            audioTitle = "身体扫描环境音",
            audioSubtitle = "低刺激冥想底音，适合平躺闭眼完成 10 分钟 NSDR。",
            audioSourceName = "Orange Free Sounds",
            audioSourceUrl = "https://orangefreesounds.com/free-meditation-music/",
            audioLicense = "CC BY 4.0",
            methodSourceName = "UCLA Health 正念中心",
            methodSourceUrl = "https://www.uclahealth.org/programs/marc/free-guided-meditations/guided-meditations",
            storyboardSteps = listOf(
                "第 1 分钟：平躺或半躺，双手自然打开，先只感受呼气变长。",
                "第 2 到 3 分钟：注意额头、眼睛、下颌和舌根，把紧绷感当成可被观察的信号。",
                "第 4 到 5 分钟：把注意力移到颈肩、上臂和手掌，感受每次呼气后的下沉。",
                "第 6 到 7 分钟：把注意力停在胸口和腹部，只看起伏，不主动控制。",
                "第 8 到 9 分钟：继续向骨盆、大腿、小腿和足底扫描，找到最明显的紧绷点后放过它。",
                "最后 1 分钟：把注意力扩展到全身，保持安静清醒，结束时慢慢坐起。"
            )
        ),
        "PMR_10M" to InterventionSessionContent(
            audioResId = R.raw.pmr_release_audio,
            audioTitle = "肌肉放松环境音",
            audioSubtitle = "平稳冥想氛围音，适合按肌群顺序完成渐进式肌肉放松。",
            audioSourceName = "Orange Free Sounds",
            audioSourceUrl = "https://orangefreesounds.com/free-meditative-music/",
            audioLicense = "CC BY 4.0",
            methodSourceName = "University of Michigan MARI",
            methodSourceUrl = "https://mari.umich.edu/psych-clinic/audio-files",
            storyboardSteps = listOf(
                "第 1 到 2 分钟：先握拳、前臂和上臂用力 5 秒，呼气时完全松开 10 秒。",
                "第 3 到 4 分钟：耸肩到耳边，感受肩颈紧张，再缓慢放掉力量。",
                "第 5 到 6 分钟：轻收腹部和臀部，保持 5 秒后完全回松。",
                "第 7 到 8 分钟：大腿前侧、小腿和脚趾依次收紧，结束后让双腿沉下去。",
                "第 9 分钟：快速回看全身还有没有残余紧绷点，哪里紧就多松一次。",
                "最后 1 分钟：保持自然呼吸，确认肩、下颌和手掌已经松开，再结束。"
            )
        ),
        "SLEEP_WIND_DOWN_15M" to InterventionSessionContent(
            audioResId = R.raw.sleep_wind_down_audio,
            audioTitle = "睡前降刺激音景",
            audioSubtitle = "温暖合成氛围音，适合灯光变暗后的固定睡前流程。",
            audioSourceName = "Orange Free Sounds",
            audioSourceUrl = "https://orangefreesounds.com/warm-ambient-relaxing-synth-pad-music/",
            audioLicense = "CC BY 4.0",
            methodSourceName = "Sleep Foundation",
            methodSourceUrl = "https://www.sleepfoundation.org/sleep-hygiene/bedtime-routine-for-adults",
            storyboardSteps = listOf(
                "第 1 到 3 分钟：把手机放远，关闭强蓝光屏幕，把灯光调到最低可读亮度。",
                "第 4 到 6 分钟：完成洗漱或温水清洁，让身体接收到“准备休息”的信号。",
                "第 7 到 9 分钟：只做低刺激活动，比如静坐、轻阅读或整理明天的一件小事。",
                "第 10 到 12 分钟：若脑内还在转，写下 1 到 3 件待办，把思绪留给明天处理。",
                "第 13 到 15 分钟：躺上床后只保留低音量音景，不再切回信息流和短视频。"
            )
        ),
        "GUIDED_STRETCH_MOBILITY_8M" to InterventionSessionContent(
            audioResId = R.raw.stretch_mobility_audio,
            audioTitle = "拉伸恢复钢琴音",
            audioSubtitle = "轻柔钢琴底音，适合完成一轮低负担的活动度恢复。",
            audioSourceName = "Orange Free Sounds",
            audioSourceUrl = "https://orangefreesounds.com/soft-piano-music-piano-zen/",
            audioLicense = "CC BY 4.0",
            methodSourceName = "UC Berkeley Mindful Stretching Guide",
            methodSourceUrl = "https://uhs.berkeley.edu/sites/default/files/wellness-mindfulstretchingguide.pdf",
            storyboardSteps = listOf(
                "第 1 到 2 分钟：先做肩颈放松，耸肩、后绕肩，再做温和侧屈。",
                "第 3 到 4 分钟：打开胸廓和胸椎，双手扶髋，轻做胸口展开和上背旋转。",
                "第 5 到 6 分钟：完成髋部活动，做小幅度髋屈伸或站姿提膝。",
                "第 7 分钟：把重心转到腿后侧和小腿，拉开腘绳肌与跟腱区域。",
                "最后 1 分钟：站稳做 3 次深呼气，确认身体热起来但没有过度疲劳。"
            )
        ),
        "SOUNDSCAPE_SLEEP_AUDIO_15M" to InterventionSessionContent(
            audioResId = R.raw.sleep_wind_down_audio,
            audioTitle = "助眠环境音",
            audioSubtitle = "稳定柔和的氛围音，适合作为睡前辅助放松入口，降低执行门槛。",
            audioSourceName = "Orange Free Sounds",
            audioSourceUrl = "https://orangefreesounds.com/warm-ambient-relaxing-synth-pad-music/",
            audioLicense = "CC BY 4.0",
            methodSourceName = "Sleep Foundation",
            methodSourceUrl = "https://www.sleepfoundation.org/sleep-hygiene/bedtime-routine-for-adults",
            storyboardSteps = listOf(
                "第 1 到 3 分钟：调低房间亮度，佩戴耳机或外放低音量，身体保持舒适静卧。",
                "第 4 到 6 分钟：只关注环境音里的节奏层次，不主动控制呼吸或刻意放松。",
                "第 7 到 10 分钟：若有杂念出现，只做标记后放回声音，不继续追着想法走。",
                "第 11 到 13 分钟：让肩颈、下颌和眼周逐渐放松，保持身体不再频繁调整姿势。",
                "第 14 到 15 分钟：确认困意开始出现后，直接过渡到闭眼休息，不再切回手机。"
            )
        )
    )

    fun find(protocolCode: String): InterventionSessionContent? = contents[protocolCode]
}
