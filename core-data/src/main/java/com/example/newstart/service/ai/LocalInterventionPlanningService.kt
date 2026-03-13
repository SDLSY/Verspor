package com.example.newstart.service.ai

import android.content.Context
import com.example.newstart.ai.EdgeLlmOnDeviceModel
import com.example.newstart.ai.InterventionPlanInput
import com.example.newstart.ai.InterventionPlanResult

object LocalInterventionPlanningService {

    fun warmUp(context: Context) {
        EdgeLlmOnDeviceModel.init(context)
    }

    fun hasLocalModel(): Boolean = EdgeLlmOnDeviceModel.hasModel()

    fun generateInterventionPlan(input: InterventionPlanInput): InterventionPlanResult {
        return EdgeLlmOnDeviceModel.generateInterventionPlan(input)
    }
}
