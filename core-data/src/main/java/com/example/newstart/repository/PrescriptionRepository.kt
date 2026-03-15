package com.example.newstart.repository

import android.content.Context
import android.util.Log
import com.example.newstart.database.AppDatabase
import com.example.newstart.database.entity.PrescriptionBundleEntity
import com.example.newstart.database.entity.PrescriptionItemEntity
import com.example.newstart.intervention.InterventionProfileSnapshot
import com.example.newstart.intervention.InterventionProtocolCatalog
import com.example.newstart.intervention.PrescriptionBundleDetails
import com.example.newstart.intervention.PrescriptionItemDetails
import com.example.newstart.intervention.PrescriptionItemType
import com.example.newstart.intervention.PrescriptionTimingSlot
import com.example.newstart.intervention.ProfileTriggerType
import com.example.newstart.repository.prescription.PrescriptionDecisionPayload
import com.example.newstart.repository.prescription.PrescriptionDecisionProvider
import com.example.newstart.repository.prescription.PrescriptionDecisionProviders
import com.example.newstart.repository.prescription.PrescriptionDecisionRequest
import com.example.newstart.repository.prescription.PrescriptionProtocolDescriptor
import com.example.newstart.repository.prescription.PrescriptionRagContextBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrescriptionRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(context),
    private val profileRepository: InterventionProfileRepository = InterventionProfileRepository(context, db),
    private val decisionProviders: List<PrescriptionDecisionProvider> = PrescriptionDecisionProviders.default()
) {

    private val gson = Gson()
    private val bundleDao = db.prescriptionBundleDao()
    private val itemDao = db.prescriptionItemDao()
    private val taskDao = db.interventionTaskDao()

    suspend fun getLatestActiveBundle(): PrescriptionBundleDetails? = withContext(Dispatchers.IO) {
        val bundle = bundleDao.getLatestActive() ?: return@withContext null
        bundle.toDetails(itemDao.getByBundleId(bundle.id))
    }

    suspend fun generateForTrigger(
        triggerType: ProfileTriggerType
    ): PrescriptionBundleDetails? = withContext(Dispatchers.IO) {
        profileRepository.syncPersonalizationSupportIfPossible()
        val snapshot = profileRepository.refreshSnapshot(triggerType)
        val personalizationStatus = profileRepository.getPersonalizationStatus()
        val providerSpec = requestProviderSpec(snapshot, personalizationStatus)
        val spec = validateSpec(providerSpec, snapshot) ?: buildRuleBundle(snapshot)

        bundleDao.archiveActive()
        val bundleEntity = PrescriptionBundleEntity(
            triggerType = triggerType.name,
            profileSnapshotId = snapshot.id,
            primaryGoal = spec.primaryGoal,
            riskLevel = spec.riskLevel,
            rationale = spec.rationale,
            evidenceJson = gson.toJson(spec.evidence),
            status = "ACTIVE"
        )
        val itemEntities = spec.items.mapIndexed { index, item ->
            PrescriptionItemEntity(
                bundleId = bundleEntity.id,
                itemType = item.itemType.name,
                protocolCode = item.protocolCode,
                assetRef = item.assetRef,
                durationSec = item.durationSec,
                sequenceOrder = index,
                timingSlot = item.timingSlot.name,
                isRequired = item.isRequired,
                status = "PENDING"
            )
        }

        bundleDao.upsert(bundleEntity)
        itemDao.deleteByBundleId(bundleEntity.id)
        itemDao.upsertAll(itemEntities)
        bundleEntity.toDetails(itemEntities)
    }

    private suspend fun requestProviderSpec(
        snapshot: InterventionProfileSnapshot,
        personalizationStatus: com.example.newstart.intervention.PersonalizationStatus
    ): BundleSpec? {
        val request = PrescriptionDecisionRequest(
            triggerType = snapshot.triggerType.name,
            domainScores = snapshot.domainScores,
            evidenceFacts = snapshot.evidenceFacts,
            redFlags = snapshot.redFlags,
            personalizationLevel = personalizationStatus.level.name,
            missingInputs = personalizationStatus.missingInputs.map { it.name },
            ragContext = PrescriptionRagContextBuilder.build(snapshot),
            catalog = InterventionProtocolCatalog.all().map {
                PrescriptionProtocolDescriptor(
                    protocolCode = it.protocolCode,
                    displayName = it.displayName,
                    interventionType = it.interventionType,
                    description = it.description
                )
            }
        )

        decisionProviders.forEach { provider ->
            val payload = try {
                provider.generate(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.w(TAG, "Prescription provider failed: ${provider.providerId}, error=${t.message}")
                null
            }

            val spec = payload?.toBundleSpecOrNull()
            if (spec != null) {
                Log.i(TAG, "Prescription provider success: ${provider.providerId}")
                return spec
            }
        }
        return null
    }

    private suspend fun buildRuleBundle(snapshot: InterventionProfileSnapshot): BundleSpec {
        val sleep = snapshot.domainScores["sleepDisturbance"] ?: 0
        val stress = snapshot.domainScores["stressLoad"] ?: 0
        val fatigue = snapshot.domainScores["fatigueLoad"] ?: 0
        val recovery = snapshot.domainScores["recoveryCapacity"] ?: 50
        val anxiety = snapshot.domainScores["anxietyRisk"] ?: 0
        val depression = snapshot.domainScores["depressiveRisk"] ?: 0
        val medication = snapshot.domainScores["medicationRisk"] ?: 0
        val nutrition = snapshot.domainScores["nutritionRisk"] ?: 0
        val breathingFatigue = hasBreathingFatigue()
        val hasBloodPressureEvidence = snapshot.evidenceFacts.values.flatten().any {
            it.contains("血压")
        }
        val allEvidence = snapshot.evidenceFacts.values.flatten().distinct()

        return when {
            snapshot.redFlags.isNotEmpty() || depression >= 75 -> {
                createSpec(
                    primaryGoal = "先稳定状态并把医生评估前置",
                    riskLevel = "HIGH",
                    rationale = "当前画像存在高风险提示，放松训练仅作为辅助，医生优先。",
                    evidence = allEvidence.take(4) + snapshot.redFlags.take(2),
                    primaryCode = "BODY_SCAN_NSDR_10M",
                    secondaryCode = "COGNITIVE_OFFLOAD_5M",
                    lifestyleCodes = listOf("TASK_DOCTOR_PRIORITY", "TASK_WORRY_LIST")
                )
            }
            medication >= 70 -> {
                createSpec(
                    primaryGoal = "先核对药物风险，再安排温和恢复",
                    riskLevel = "HIGH",
                    rationale = "最近一次药物识别提示风险较高或仍需人工确认，今天的建议先把医生或药师确认前置。",
                    evidence = allEvidence.take(4) + listOf("药物识别结果需要优先核对"),
                    primaryCode = "BODY_SCAN_NSDR_10M",
                    secondaryCode = "COGNITIVE_OFFLOAD_5M",
                    lifestyleCodes = listOf("TASK_DOCTOR_PRIORITY", "TASK_WORRY_LIST")
                )
            }
            sleep >= 65 && stress >= 60 -> {
                createSpec(
                    primaryGoal = "降低睡前唤醒并改善入睡准备",
                    riskLevel = "MEDIUM",
                    rationale = "睡眠扰动和压力负荷同时偏高，先做睡前流程，再用身体扫描收尾。",
                    evidence = allEvidence.take(5),
                    primaryCode = "SLEEP_WIND_DOWN_15M",
                    secondaryCode = "BODY_SCAN_NSDR_10M",
                    lifestyleCodes = listOf("TASK_SCREEN_CURFEW", "TASK_CAFFEINE_CUTOFF")
                )
            }
            nutrition >= 65 -> {
                createSpec(
                    primaryGoal = "先稳定恢复节律并校正饮食负担",
                    riskLevel = "MEDIUM",
                    rationale = "近 24 小时饮食结构或总热量偏离较大，今天优先安排轻恢复与白天节律任务，避免继续堆叠负荷。",
                    evidence = allEvidence.take(5),
                    primaryCode = "RECOVERY_WALK_10M",
                    secondaryCode = "GUIDED_STRETCH_MOBILITY_8M",
                    lifestyleCodes = listOf("TASK_DAYLIGHT_WALK", "TASK_CAFFEINE_CUTOFF")
                )
            }
            fatigue >= 65 || recovery <= 40 -> {
                createSpec(
                    primaryGoal = "先做低门槛恢复，减轻疲劳积累",
                    riskLevel = if (recovery <= 30) "HIGH" else "MEDIUM",
                    rationale = "当前疲劳负荷高或恢复能力偏低，优先安排轻活动恢复和拉伸，而不是继续堆叠静息训练。",
                    evidence = allEvidence.take(5),
                    primaryCode = "RECOVERY_WALK_10M",
                    secondaryCode = "GUIDED_STRETCH_MOBILITY_8M",
                    lifestyleCodes = listOf("TASK_DAYLIGHT_WALK")
                )
            }
            hasBloodPressureEvidence -> {
                createSpec(
                    primaryGoal = "平稳节律并降低白天生理负荷",
                    riskLevel = "MEDIUM",
                    rationale = "医检提示血压相关风险时，优先用节律呼吸和轻步行做稳态恢复。",
                    evidence = allEvidence.take(4),
                    primaryCode = if (breathingFatigue) "RECOVERY_WALK_10M" else "BREATH_4_6",
                    secondaryCode = "GUIDED_STRETCH_MOBILITY_8M",
                    lifestyleCodes = listOf("TASK_DAYLIGHT_WALK")
                )
            }
            stress >= 60 || anxiety >= 60 -> {
                createSpec(
                    primaryGoal = "降低压力唤醒并提升可执行性",
                    riskLevel = "MEDIUM",
                    rationale = "压力或焦虑风险偏高，先给短时、明确的减压处方；若近期对呼吸训练依从差，则切换为肌肉放松。",
                    evidence = allEvidence.take(5),
                    primaryCode = if (breathingFatigue) "PMR_10M" else "BREATH_4_6",
                    secondaryCode = if (breathingFatigue) "BODY_SCAN_NSDR_10M" else "PMR_10M",
                    lifestyleCodes = listOf("TASK_WORRY_LIST")
                )
            }
            else -> {
                createSpec(
                    primaryGoal = "维持稳定恢复节律",
                    riskLevel = "LOW",
                    rationale = "当前风险没有集中落在单一高危域，安排一个易开始的音景方案，再搭配轻量思绪卸载。",
                    evidence = allEvidence.take(4),
                    primaryCode = "SOUNDSCAPE_SLEEP_AUDIO_15M",
                    secondaryCode = "COGNITIVE_OFFLOAD_5M",
                    lifestyleCodes = listOf("TASK_DAYLIGHT_WALK")
                )
            }
        }
    }

    private fun createSpec(
        primaryGoal: String,
        riskLevel: String,
        rationale: String,
        evidence: List<String>,
        primaryCode: String,
        secondaryCode: String,
        lifestyleCodes: List<String>
    ): BundleSpec {
        val items = mutableListOf<BundleItemSpec>()
        items += definitionToItem(primaryCode, PrescriptionItemType.PRIMARY, true)
        items += definitionToItem(secondaryCode, PrescriptionItemType.SECONDARY, false)
        lifestyleCodes.forEach { code ->
            items += definitionToItem(code, PrescriptionItemType.LIFESTYLE, false)
        }
        return BundleSpec(
            primaryGoal = primaryGoal,
            riskLevel = riskLevel,
            rationale = rationale,
            evidence = evidence.distinct(),
            items = items
        )
    }

    private fun validateSpec(spec: BundleSpec?, snapshot: InterventionProfileSnapshot): BundleSpec? {
        spec ?: return null
        if (spec.items.none { it.itemType == PrescriptionItemType.PRIMARY }) {
            return null
        }
        val validCodes = InterventionProtocolCatalog.validCodes()
        if (spec.items.any { it.protocolCode !in validCodes }) {
            return null
        }
        if (snapshot.redFlags.isNotEmpty() && spec.items.none { it.protocolCode == "TASK_DOCTOR_PRIORITY" }) {
            return spec.copy(
                riskLevel = "HIGH",
                items = spec.items + definitionToItem("TASK_DOCTOR_PRIORITY", PrescriptionItemType.LIFESTYLE, false)
            )
        }
        return spec
    }

    private suspend fun hasBreathingFatigue(): Boolean {
        val recent = taskDao.getRecent(6).take(3)
        if (recent.size < 3) return false
        return recent.all {
            it.protocolType.startsWith("BREATH") && it.status != "COMPLETED"
        }
    }

    private fun definitionToItem(
        protocolCode: String,
        itemType: PrescriptionItemType,
        isRequired: Boolean
    ): BundleItemSpec {
        val definition = InterventionProtocolCatalog.find(protocolCode)
            ?: error("Unknown protocol code: $protocolCode")
        return BundleItemSpec(
            itemType = itemType,
            protocolCode = definition.protocolCode,
            assetRef = definition.assetRef,
            durationSec = definition.defaultDurationSec,
            timingSlot = definition.defaultTimingSlot,
            isRequired = isRequired
        )
    }

    private fun PrescriptionBundleEntity.toDetails(items: List<PrescriptionItemEntity>): PrescriptionBundleDetails {
        val evidenceType = object : TypeToken<List<String>>() {}.type
        return PrescriptionBundleDetails(
            id = id,
            createdAt = createdAt,
            triggerType = triggerType,
            primaryGoal = primaryGoal,
            riskLevel = riskLevel,
            rationale = rationale,
            evidence = gson.fromJson(evidenceJson, evidenceType) ?: emptyList(),
            items = items.map {
                PrescriptionItemDetails(
                    id = it.id,
                    itemType = runCatching { PrescriptionItemType.valueOf(it.itemType) }
                        .getOrDefault(PrescriptionItemType.LIFESTYLE),
                    protocolCode = it.protocolCode,
                    assetRef = it.assetRef,
                    durationSec = it.durationSec,
                    sequenceOrder = it.sequenceOrder,
                    timingSlot = runCatching { PrescriptionTimingSlot.valueOf(it.timingSlot) }
                        .getOrDefault(PrescriptionTimingSlot.FLEXIBLE),
                    isRequired = it.isRequired,
                    status = it.status
                )
            }
        )
    }

    private data class BundleSpec(
        val primaryGoal: String,
        val riskLevel: String,
        val rationale: String,
        val evidence: List<String>,
        val items: List<BundleItemSpec>
    )

    private data class BundleItemSpec(
        val itemType: PrescriptionItemType,
        val protocolCode: String,
        val assetRef: String,
        val durationSec: Int,
        val timingSlot: PrescriptionTimingSlot,
        val isRequired: Boolean
    )

    companion object {
        private const val TAG = "PrescriptionRepository"
    }

    private fun PrescriptionDecisionPayload.toBundleSpecOrNull(): BundleSpec? {
        val items = mutableListOf<BundleItemSpec>()
        if (primaryInterventionType.isNotBlank()) {
            items += createItem(
                code = primaryInterventionType,
                itemType = PrescriptionItemType.PRIMARY,
                isRequired = true,
                preferredDurationSec = durationSec,
                preferredTiming = timing
            )
        }
        if (secondaryInterventionType.isNotBlank()) {
            items += createItem(
                code = secondaryInterventionType,
                itemType = PrescriptionItemType.SECONDARY,
                isRequired = false,
                preferredDurationSec = durationSec,
                preferredTiming = timing
            )
        }
        lifestyleTaskCodes.forEach { code ->
            items += createItem(
                code = code,
                itemType = PrescriptionItemType.LIFESTYLE,
                isRequired = false,
                preferredDurationSec = durationSec,
                preferredTiming = timing
            )
        }
        if (items.isEmpty()) {
            return null
        }
        return BundleSpec(
            primaryGoal = primaryGoal.ifBlank { "今日恢复处方" },
            riskLevel = riskLevel.ifBlank { "MEDIUM" },
            rationale = rationale.ifBlank { "基于近期状态生成的恢复处方。" },
            evidence = evidence
                .ifEmpty { contraindications }
                .ifEmpty { listOf("系统已根据最近画像和执行记录完成本次选方。") },
            items = items
        )
    }

    private fun createItem(
        code: String,
        itemType: PrescriptionItemType,
        isRequired: Boolean,
        preferredDurationSec: Int = 0,
        preferredTiming: String? = null
    ): BundleItemSpec {
        val definition = InterventionProtocolCatalog.find(code)
            ?: return BundleItemSpec(
                itemType = itemType,
                protocolCode = code,
                assetRef = "task://unsupported",
                durationSec = preferredDurationSec.coerceAtLeast(0),
                timingSlot = PrescriptionTimingSlot.FLEXIBLE,
                isRequired = isRequired
            )
        return BundleItemSpec(
            itemType = itemType,
            protocolCode = definition.protocolCode,
            assetRef = definition.assetRef,
            durationSec = if (preferredDurationSec > 0) preferredDurationSec else definition.defaultDurationSec,
            timingSlot = preferredTiming
                ?.let { runCatching { PrescriptionTimingSlot.valueOf(it) }.getOrNull() }
                ?: definition.defaultTimingSlot,
            isRequired = isRequired
        )
    }
}
