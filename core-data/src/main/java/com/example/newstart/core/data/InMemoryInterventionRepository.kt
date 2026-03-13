package com.example.newstart.core.data

import com.example.newstart.core.model.InterventionExecution
import com.example.newstart.core.model.InterventionTask

class InMemoryInterventionRepository : InterventionRepository {
    private val tasks = mutableMapOf<String, InterventionTask>()
    private val executions = mutableMapOf<String, InterventionExecution>()

    override suspend fun upsertTask(task: InterventionTask) {
        tasks[task.taskId] = task
    }

    override suspend fun upsertExecution(execution: InterventionExecution) {
        executions[execution.executionId] = execution
    }
}
