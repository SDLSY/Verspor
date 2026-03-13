package com.example.newstart.ui.doctor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.newstart.R
import com.example.newstart.databinding.ItemDoctorMessageAssessmentBinding
import com.example.newstart.databinding.ItemDoctorMessageAssistantBinding
import com.example.newstart.databinding.ItemDoctorMessageFollowUpBinding
import com.example.newstart.databinding.ItemDoctorMessageUserBinding

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
            binding.tvFollowUpQuestion.text = payload?.question ?: item.content
            binding.tvFollowUpMissing.text = if (payload?.missingInfo.isNullOrEmpty()) {
                binding.root.context.getString(R.string.doctor_follow_up_missing_default)
            } else {
                binding.root.context.getString(
                    R.string.doctor_follow_up_missing_format,
                    payload?.missingInfo?.joinToString("、")
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

            binding.tvAssessmentSummary.text = payload.doctorSummary
            binding.tvAssessmentComplaint.text = context.getString(
                R.string.doctor_assessment_complaint_format,
                payload.chiefComplaint
            )
            binding.tvAssessmentSymptomFacts.text = context.getString(
                R.string.doctor_assessment_symptom_facts_format,
                payload.symptomFacts.joinToString("\n- ", prefix = "- ")
            )
            binding.tvAssessmentSuspectedIssues.text = context.getString(
                R.string.doctor_assessment_suspected_issues_format,
                payload.suspectedIssues.mapIndexed { index, issue ->
                    "${index + 1}. ${issue.name}（${issue.confidence}%）\n${issue.rationale}"
                }.joinToString("\n\n")
            )
            if (payload.redFlags.isEmpty()) {
                binding.tvAssessmentRedFlags.visibility = View.GONE
            } else {
                binding.tvAssessmentRedFlags.visibility = View.VISIBLE
                binding.tvAssessmentRedFlags.text = context.getString(
                    R.string.doctor_assessment_red_flags_format,
                    payload.redFlags.joinToString("；")
                )
            }
            binding.tvAssessmentDepartment.text = context.getString(
                R.string.doctor_assessment_department_format,
                payload.recommendedDepartment
            )
            binding.tvAssessmentNextSteps.text = context.getString(
                R.string.doctor_assessment_next_steps_format,
                payload.nextStepAdvice.joinToString("\n- ", prefix = "- ")
            )
            binding.tvAssessmentDisclaimer.text = payload.disclaimer
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
    }
}
