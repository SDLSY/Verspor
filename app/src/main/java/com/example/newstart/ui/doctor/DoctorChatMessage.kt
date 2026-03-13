package com.example.newstart.ui.doctor

import java.util.UUID

data class DoctorChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: DoctorRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPending: Boolean = false,
    val action: DoctorMessageAction? = null,
    val messageType: DoctorMessageType = DoctorMessageType.TEXT,
    val followUpPayload: DoctorFollowUpPayload? = null,
    val assessmentPayload: DoctorAssessmentPayload? = null
) {
    companion object {
        fun user(content: String): DoctorChatMessage {
            return DoctorChatMessage(role = DoctorRole.USER, content = content)
        }

        fun assistant(
            content: String,
            isPending: Boolean = false,
            action: DoctorMessageAction? = null
        ): DoctorChatMessage {
            return DoctorChatMessage(
                role = DoctorRole.ASSISTANT,
                content = content,
                isPending = isPending,
                action = action
            )
        }

        fun followUp(payload: DoctorFollowUpPayload): DoctorChatMessage {
            return DoctorChatMessage(
                role = DoctorRole.ASSISTANT,
                content = payload.question,
                messageType = DoctorMessageType.FOLLOW_UP,
                followUpPayload = payload
            )
        }

        fun assessment(payload: DoctorAssessmentPayload): DoctorChatMessage {
            return DoctorChatMessage(
                role = DoctorRole.ASSISTANT,
                content = payload.doctorSummary,
                messageType = DoctorMessageType.ASSESSMENT,
                assessmentPayload = payload
            )
        }
    }
}

data class DoctorMessageAction(
    val protocolType: String,
    val durationSec: Int
)

enum class DoctorRole {
    USER,
    ASSISTANT
}
