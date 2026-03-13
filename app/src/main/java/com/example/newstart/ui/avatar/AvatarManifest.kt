package com.example.newstart.ui.avatar

import org.json.JSONObject

data class AvatarManifest(
    val profiles: AvatarProfiles,
    val selectionPolicy: AvatarSelectionPolicy,
    val animations: AvatarAnimationConfig
) {
    companion object {
        fun fromJson(raw: String): AvatarManifest {
            val root = JSONObject(raw)
            val profilesJson = root.optJSONObject("profiles")
            val hqPath = profilesJson?.optJSONObject("hq")?.optString("assetPath")
                .orEmpty()
                .ifBlank { "3d_avatar/guide_avatar_vroid_hq.glb" }
            val litePath = profilesJson?.optJSONObject("lite")?.optString("assetPath")
                .orEmpty()
                .ifBlank { "3d_avatar/guide_avatar_vroid_lite.glb" }
            val hqCoverage = profilesJson?.optJSONObject("hq")?.optDouble("retargetBoneCoverage", Double.NaN)
                ?.takeUnless { it.isNaN() }
            val liteCoverage = profilesJson?.optJSONObject("lite")?.optDouble("retargetBoneCoverage", Double.NaN)
                ?.takeUnless { it.isNaN() }

            val policyJson = root.optJSONObject("selectionPolicy")
            val ramThresholdGb = policyJson?.optInt("ramThresholdGb", 6) ?: 6
            val firstFrameTimeoutMs = policyJson?.optLong("firstFrameTimeoutMs", 1200L) ?: 1200L

            val animationsJson = root.optJSONObject("animations")
            val idleName = animationsJson?.optString("idleName")
                .orEmpty()
                .ifBlank { "Waving Gesture_2" }
            val speakName = animationsJson?.optString("speakName")
                .orEmpty()
                .ifBlank { "Pointing Forward_1" }
            val emphasisName = animationsJson?.optString("emphasisName")
                .orEmpty()
                .ifBlank { "Jumping Down_3" }
            val idleVariants = animationsJson?.optJSONArray("idleVariants")
                ?.let { arr ->
                    buildList {
                        for (i in 0 until arr.length()) {
                            val name = arr.optString(i).orEmpty().trim()
                            if (name.isNotBlank()) add(name)
                        }
                    }
                }
                ?.distinct()
                ?.ifEmpty { listOf(idleName) }
                ?: listOf(idleName)
            val idleCycleMs = (animationsJson?.optLong("idleCycleMs", 2600L) ?: 2600L).coerceAtLeast(800L)

            return AvatarManifest(
                profiles = AvatarProfiles(
                    hq = AvatarProfile(
                        id = "hq",
                        assetPath = hqPath,
                        retargetBoneCoverage = hqCoverage
                    ),
                    lite = AvatarProfile(
                        id = "lite",
                        assetPath = litePath,
                        retargetBoneCoverage = liteCoverage
                    )
                ),
                selectionPolicy = AvatarSelectionPolicy(
                    ramThresholdGb = ramThresholdGb,
                    firstFrameTimeoutMs = firstFrameTimeoutMs
                ),
                animations = AvatarAnimationConfig(
                    idleName = idleName,
                    speakName = speakName,
                    emphasisName = emphasisName,
                    idleVariants = idleVariants,
                    idleCycleMs = idleCycleMs
                )
            )
        }

        fun default(): AvatarManifest {
            return AvatarManifest(
                profiles = AvatarProfiles(
                    hq = AvatarProfile(
                        id = "hq",
                        assetPath = "3d_avatar/guide_avatar_vroid_hq.glb",
                        retargetBoneCoverage = null
                    ),
                    lite = AvatarProfile(
                        id = "lite",
                        assetPath = "3d_avatar/guide_avatar_vroid_lite.glb",
                        retargetBoneCoverage = null
                    )
                ),
                selectionPolicy = AvatarSelectionPolicy(
                    ramThresholdGb = 6,
                    firstFrameTimeoutMs = 1200L
                ),
                animations = AvatarAnimationConfig(
                    idleName = "Waving Gesture_2",
                    speakName = "Pointing Forward_1",
                    emphasisName = "Jumping Down_3",
                    idleVariants = listOf(
                        "Waving Gesture_2",
                        "Friendly Wave_4",
                        "Idle Sway_5",
                        "Cheer Pose_6",
                        "Guide Left_7"
                    ),
                    idleCycleMs = 2600L
                )
            )
        }
    }
}

data class AvatarProfiles(
    val hq: AvatarProfile,
    val lite: AvatarProfile
)

data class AvatarProfile(
    val id: String,
    val assetPath: String,
    val retargetBoneCoverage: Double? = null
)

data class AvatarSelectionPolicy(
    val ramThresholdGb: Int,
    val firstFrameTimeoutMs: Long
)

data class AvatarAnimationConfig(
    val idleName: String,
    val speakName: String,
    val emphasisName: String,
    val idleVariants: List<String> = listOf(idleName),
    val idleCycleMs: Long = 2600L
)
