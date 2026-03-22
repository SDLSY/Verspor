package com.example.newstart.ui.doctor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.newstart.core.common.R
import com.example.newstart.feature.doctor.databinding.ItemDoctorMessageAssessmentBinding
import com.example.newstart.feature.doctor.databinding.ItemDoctorMessageAssistantBinding
import com.example.newstart.feature.doctor.databinding.ItemDoctorMessageFollowUpBinding
import com.example.newstart.feature.doctor.databinding.ItemDoctorMessageUserBinding

class DoctorChatAdapter(
    private val onActionClick: (DoctorMessageAction) -> Unit
) : ListAdapter<DoctorChatMessage, RecyclerView.ViewHolder>(DiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when {
            item.role == DoctorRole.USER -> VIEW_TYPE_USER
            item.messageType == DoctorMessageType.FOLLOW_UP -> VIEW_TYPE_FOLLOW_UP
            item.messageType == DoctorMessageType.ASSESSMENT -> VIEW_TYPE_ASSESSMENT
            else -> VIEW_TYPE_ASSISTANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> UserViewHolder(
                ItemDoctorMessageUserBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            VIEW_TYPE_FOLLOW_UP -> FollowUpViewHolder(
                ItemDoctorMessageFollowUpBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            VIEW_TYPE_ASSESSMENT -> AssessmentViewHolder(
                ItemDoctorMessageAssessmentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> AssistantViewHolder(
                ItemDoctorMessageAssistantBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                onActionClick
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserViewHolder -> holder.bind(getItem(position))
            is AssistantViewHolder -> holder.bind(getItem(position))
            is FollowUpViewHolder -> holder.bind(getItem(position))
            is AssessmentViewHolder -> holder.bind(getItem(position))
        }
    }

    private class UserViewHolder(
        private val binding: ItemDoctorMessageUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DoctorChatMessage) {
            binding.tvMessage.text = item.content
        }
    }

    private class AssistantViewHolder(
        private val binding: ItemDoctorMessageAssistantBinding,
        private val onActionClick: (DoctorMessageAction) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DoctorChatMessage) {
            binding.tvMessage.text = item.content
            val action = item.action
            if (action == null || item.isPending) {
                binding.btnExecuteAction.visibility = View.GONE
                binding.btnExecuteAction.setOnClickListener(null)
            } else {
                binding.btnExecuteAction.visibility = View.VISIBLE
                binding.btnExecuteAction.setOnClickListener { onActionClick(action) }
            }
        }
    }

    private class FollowUpViewHolder(
        private val binding: ItemDoctorMessageFollowUpBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DoctorChatMessage) {
            val payload = item.followUpPayload
            val missingInfo = payload?.missingInfo.orEmpty()
            binding.tvFollowUpQuestion.text = safeText(payload?.question, item.content)
            binding.tvFollowUpMissing.text = if (missingInfo.isEmpty()) {
                binding.root.context.getString(R.string.doctor_follow_up_missing_default)
            } else {
                binding.root.context.getString(
                    R.string.doctor_follow_up_missing_format,
                    missingInfo.joinToString("、")
                )
            }
        }
    }

    private class AssessmentViewHolder(
        private val binding: ItemDoctorMessageAssessmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DoctorChatMessage) {
            val context = binding.root.context
            val payload = item.assessmentPayload
            if (payload == null) {
                binding.tvAssessmentSummary.text = item.content
                binding.tvAssessmentComplaint.text = ""
                binding.tvAssessmentSymptomFacts.text = ""
                binding.tvAssessmentSuspectedIssues.text = ""
                binding.tvAssessmentRedFlags.visibility = View.GONE
                binding.tvAssessmentDepartment.text = ""
                binding.tvAssessmentNextSteps.text = ""
                binding.tvAssessmentDisclaimer.text = ""
                return
            }

            val symptomFacts = payload.symptomFacts.orEmpty()
            val suspectedIssues = payload.suspectedIssues.orEmpty()
            val redFlags = payload.redFlags.orEmpty()
            val nextStepAdvice = payload.nextStepAdvice.orEmpty()

            binding.tvAssessmentSummary.text = safeText(payload.doctorSummary, item.content)
            binding.tvAssessmentComplaint.text = context.getString(
                R.string.doctor_assessment_complaint_format,
                safeText(payload.chiefComplaint, "待补充")
            )
            binding.tvAssessmentSymptomFacts.text = context.getString(
                R.string.doctor_assessment_symptom_facts_format,
                symptomFacts.joinToString("\n- ", prefix = "- ").ifBlank { "- 暂无补充症状事实" }
            )
            binding.tvAssessmentSuspectedIssues.text = context.getString(
                R.string.doctor_assessment_suspected_issues_format,
                suspectedIssues.mapIndexed { index, issue ->
                    "${index + 1}. ${safeText(issue.name, "待确认问题")}（${issue.confidence}%）\n${
                        safeText(issue.rationale, "暂无补充说明")
                    }"
                }.joinToString("\n\n").ifBlank { "暂无明确怀疑问题，请结合后续问诊继续补充。" }
            )
            if (redFlags.isEmpty()) {
                binding.tvAssessmentRedFlags.visibility = View.GONE
            } else {
                binding.tvAssessmentRedFlags.visibility = View.VISIBLE
                binding.tvAssessmentRedFlags.text = context.getString(
                    R.string.doctor_assessment_red_flags_format,
                    redFlags.joinToString("、")
                )
            }
            binding.tvAssessmentDepartment.text = context.getString(
                R.string.doctor_assessment_department_format,
                safeText(payload.recommendedDepartment, "综合门诊")
            )
            binding.tvAssessmentNextSteps.text = context.getString(
                R.string.doctor_assessment_next_steps_format,
                nextStepAdvice.joinToString("\n- ", prefix = "- ").ifBlank {
                    "- 建议继续补充主诉、症状持续时间和危险信号。"
                }
            )
            binding.tvAssessmentDisclaimer.text = safeText(
                payload.disclaimer,
                "本问诊结果仅用于健康辅助与演示，不替代医生面诊。"
            )
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DoctorChatMessage>() {
        override fun areItemsTheSame(oldItem: DoctorChatMessage, newItem: DoctorChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DoctorChatMessage, newItem: DoctorChatMessage): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
        private const val VIEW_TYPE_FOLLOW_UP = 3
        private const val VIEW_TYPE_ASSESSMENT = 4

        private fun safeText(value: String?, fallback: String = ""): String {
            return value?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback
        }
    }
}
