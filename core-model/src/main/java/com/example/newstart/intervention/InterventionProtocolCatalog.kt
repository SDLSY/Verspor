package com.example.newstart.intervention

data class InterventionProtocolDefinition(
    val protocolCode: String,
    val interventionType: String,
    val displayName: String,
    val description: String,
    val defaultDurationSec: Int,
    val defaultTimingSlot: PrescriptionTimingSlot,
    val assetRef: String,
    val steps: List<String>,
    val supportsBreathingCoach: Boolean = false
)

object InterventionProtocolCatalog {

    private val protocols = listOf(
        InterventionProtocolDefinition(
            protocolCode = "BREATH_4_6",
            interventionType = "PACED_BREATHING",
            displayName = "4-6 节律呼吸",
            description = "通过延长呼气降低生理唤醒，适合压力高或睡前紧张时执行。",
            defaultDurationSec = 180,
            defaultTimingSlot = PrescriptionTimingSlot.EVENING,
            assetRef = "breathing://4-6",
            steps = listOf("吸气 4 秒", "呼气 6 秒", "保持肩颈放松", "持续 3 分钟"),
            supportsBreathingCoach = true
        ),
        InterventionProtocolDefinition(
            protocolCode = "BREATH_4_7_8",
            interventionType = "PACED_BREATHING",
            displayName = "4-7-8 放松呼吸",
            description = "更偏向睡前降唤醒，适合入睡困难与思绪活跃场景。",
            defaultDurationSec = 240,
            defaultTimingSlot = PrescriptionTimingSlot.BEFORE_SLEEP,
            assetRef = "breathing://4-7-8",
            steps = listOf("吸气 4 秒", "停顿 7 秒", "呼气 8 秒", "完成 4 到 6 轮"),
            supportsBreathingCoach = true
        ),
        InterventionProtocolDefinition(
            protocolCode = "BREATH_BOX",
            interventionType = "PACED_BREATHING",
            displayName = "方框呼吸",
            description = "适合白天需要快速稳定节律与注意力时执行。",
            defaultDurationSec = 180,
            defaultTimingSlot = PrescriptionTimingSlot.AFTERNOON,
            assetRef = "breathing://box",
            steps = listOf("吸气 4 秒", "停顿 4 秒", "呼气 4 秒", "停顿 4 秒", "循环 3 分钟"),
            supportsBreathingCoach = true
        ),
        InterventionProtocolDefinition(
            protocolCode = "PMR_10M",
            interventionType = "PROGRESSIVE_MUSCLE_RELAXATION",
            displayName = "渐进式肌肉放松",
            description = "通过先紧张再放松的方式缓解躯体紧绷和高压力。",
            defaultDurationSec = 600,
            defaultTimingSlot = PrescriptionTimingSlot.EVENING,
            assetRef = "session://pmr/10m",
            steps = listOf("按顺序收紧肌群 5 秒", "慢慢放松 10 秒", "依次完成肩、臂、腹、腿")
        ),
        InterventionProtocolDefinition(
            protocolCode = "BODY_SCAN_NSDR_10M",
            interventionType = "BODY_SCAN_NSDR",
            displayName = "身体扫描 NSDR",
            description = "适合高唤醒、思绪过载、睡前难以放松时执行。",
            defaultDurationSec = 600,
            defaultTimingSlot = PrescriptionTimingSlot.BEFORE_SLEEP,
            assetRef = "session://body-scan/10m",
            steps = listOf("平躺或半躺", "从头到脚依次关注身体感受", "不评价，只记录紧张与放松变化")
        ),
        InterventionProtocolDefinition(
            protocolCode = "GUIDED_STRETCH_MOBILITY_8M",
            interventionType = "GUIDED_STRETCH_MOBILITY",
            displayName = "引导拉伸与活动度恢复",
            description = "适合久坐、恢复差或轻度疲劳时快速做一轮活动恢复。",
            defaultDurationSec = 480,
            defaultTimingSlot = PrescriptionTimingSlot.AFTERNOON,
            assetRef = "session://stretch/8m",
            steps = listOf("肩颈拉伸 2 分钟", "胸椎与髋部活动 3 分钟", "下肢拉伸 3 分钟")
        ),
        InterventionProtocolDefinition(
            protocolCode = "RECOVERY_WALK_10M",
            interventionType = "RECOVERY_WALK",
            displayName = "恢复步行",
            description = "适合恢复能力低、久坐或血压偏高场景。",
            defaultDurationSec = 600,
            defaultTimingSlot = PrescriptionTimingSlot.AFTERNOON,
            assetRef = "session://walk/10m",
            steps = listOf("低强度步行 10 分钟", "保持能正常说话的强度", "结束后记录呼吸与疲劳感")
        ),
        InterventionProtocolDefinition(
            protocolCode = "SLEEP_WIND_DOWN_15M",
            interventionType = "SLEEP_WIND_DOWN",
            displayName = "睡前减刺激流程",
            description = "用固定流程降低睡前刺激，适合失眠和睡前高唤醒。",
            defaultDurationSec = 900,
            defaultTimingSlot = PrescriptionTimingSlot.BEFORE_SLEEP,
            assetRef = "session://wind-down/15m",
            steps = listOf("停止强蓝光屏幕", "降低灯光与声音刺激", "切换到轻音频或身体扫描")
        ),
        InterventionProtocolDefinition(
            protocolCode = "SOUNDSCAPE_SLEEP_AUDIO_15M",
            interventionType = "SOUNDSCAPE_SLEEP_AUDIO",
            displayName = "助眠音景",
            description = "适合作为睡前辅助放松入口，降低执行门槛。",
            defaultDurationSec = 900,
            defaultTimingSlot = PrescriptionTimingSlot.BEFORE_SLEEP,
            assetRef = "audio://soundscape/15m",
            steps = listOf("佩戴耳机或外放低音量", "保持静卧", "仅关注声音，不主动用力放松")
        ),
        InterventionProtocolDefinition(
            protocolCode = "ZEN_MIST_ERASE_5M",
            interventionType = "ZEN_INTERACTION",
            displayName = "擦除迷雾",
            description = "通过缓慢擦除迷雾，把注意力从反复思考拉回到眼前动作。",
            defaultDurationSec = 300,
            defaultTimingSlot = PrescriptionTimingSlot.EVENING,
            assetRef = "interactive://zen-mist",
            steps = listOf("慢慢滑动手指", "只观察雾气散开的过程", "不用追求完成得很快")
        ),
        InterventionProtocolDefinition(
            protocolCode = "ZEN_WAVE_GARDEN_5M",
            interventionType = "ZEN_INTERACTION",
            displayName = "拨动波浪",
            description = "通过轻触和拨动波浪，让节律和注意力一起慢下来。",
            defaultDurationSec = 300,
            defaultTimingSlot = PrescriptionTimingSlot.EVENING,
            assetRef = "interactive://zen-wave",
            steps = listOf("轻触屏幕拨动线条", "跟着回弹节律慢下来", "不要急着切换动作")
        ),
        InterventionProtocolDefinition(
            protocolCode = "COGNITIVE_OFFLOAD_5M",
            interventionType = "COGNITIVE_OFFLOAD",
            displayName = "思绪卸载",
            description = "适合担忧感强、脑内反刍明显时快速落地。",
            defaultDurationSec = 300,
            defaultTimingSlot = PrescriptionTimingSlot.EVENING,
            assetRef = "session://cognitive-offload/5m",
            steps = listOf("写下当前最担心的 3 件事", "每件事写 1 个下一步", "把剩余思绪留到明天处理")
        ),
        InterventionProtocolDefinition(
            protocolCode = "TASK_SCREEN_CURFEW",
            interventionType = "SLEEP_WIND_DOWN",
            displayName = "睡前 60 分钟停用强蓝光屏幕",
            description = "生活任务，用于降低睡前刺激暴露。",
            defaultDurationSec = 900,
            defaultTimingSlot = PrescriptionTimingSlot.BEFORE_SLEEP,
            assetRef = "task://screen-curfew",
            steps = listOf("睡前 60 分钟不再看高亮度手机和平板")
        ),
        InterventionProtocolDefinition(
            protocolCode = "TASK_CAFFEINE_CUTOFF",
            interventionType = "SLEEP_WIND_DOWN",
            displayName = "下午 2 点后不摄入咖啡因",
            description = "生活任务，用于降低睡前残余兴奋。",
            defaultDurationSec = 60,
            defaultTimingSlot = PrescriptionTimingSlot.AFTERNOON,
            assetRef = "task://caffeine-cutoff",
            steps = listOf("下午 2 点后避免咖啡、浓茶、能量饮料")
        ),
        InterventionProtocolDefinition(
            protocolCode = "TASK_WORRY_LIST",
            interventionType = "COGNITIVE_OFFLOAD",
            displayName = "担忧清单",
            description = "把待处理问题写出来，降低反复思考。",
            defaultDurationSec = 300,
            defaultTimingSlot = PrescriptionTimingSlot.EVENING,
            assetRef = "task://worry-list",
            steps = listOf("写下担忧事项", "区分今天能做和明天再做")
        ),
        InterventionProtocolDefinition(
            protocolCode = "TASK_DAYLIGHT_WALK",
            interventionType = "RECOVERY_WALK",
            displayName = "白天户外轻步行",
            description = "生活任务，用于提升觉醒节律和恢复能力。",
            defaultDurationSec = 600,
            defaultTimingSlot = PrescriptionTimingSlot.MORNING,
            assetRef = "task://daylight-walk",
            steps = listOf("白天到户外步行 10 分钟，尽量接触自然光")
        ),
        InterventionProtocolDefinition(
            protocolCode = "TASK_DOCTOR_PRIORITY",
            interventionType = "COGNITIVE_OFFLOAD",
            displayName = "优先联系医生或心理专业人员",
            description = "高风险时把就医动作前置，不让干预建议掩盖风险。",
            defaultDurationSec = 120,
            defaultTimingSlot = PrescriptionTimingSlot.FLEXIBLE,
            assetRef = "task://doctor-priority",
            steps = listOf("尽快联系医生或心理专业人员", "若症状加重，直接线下就医")
        )
    )

    fun all(): List<InterventionProtocolDefinition> = protocols

    fun find(protocolCode: String): InterventionProtocolDefinition? {
        return protocols.firstOrNull { it.protocolCode == protocolCode }
    }

    fun byType(interventionType: String): List<InterventionProtocolDefinition> {
        return protocols.filter { it.interventionType == interventionType }
    }

    fun validCodes(): Set<String> = protocols.map { it.protocolCode }.toSet()
}
