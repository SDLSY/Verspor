package com.example.newstart.core.data

import com.example.newstart.core.model.InterventionExecution
import com.example.newstart.core.model.InterventionTask

interface InterventionRepository {
    suspend fun upsertTask(task: InterventionTask)
    suspend fun upsertExecution(execution: InterventionExecution)
}
