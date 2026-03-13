package com.example.newstart.repository

import com.example.newstart.database.dao.InterventionExecutionDao
import com.example.newstart.database.dao.InterventionExecutionSummary
import com.example.newstart.database.dao.InterventionTaskDao
import com.example.newstart.database.dao.InterventionCompletionSummary
import com.example.newstart.database.entity.InterventionExecutionEntity
import com.example.newstart.database.entity.InterventionTaskEntity
import kotlinx.coroutines.flow.Flow

class InterventionRepository(
    private val taskDao: InterventionTaskDao,
    private val executionDao: InterventionExecutionDao
) {

    suspend fun upsertTask(task: InterventionTaskEntity) {
        taskDao.upsert(task)
    }

    suspend fun upsertTasks(tasks: List<InterventionTaskEntity>) {
        taskDao.upsertAll(tasks)
    }

    suspend fun getTask(taskId: String): InterventionTaskEntity? {
        return taskDao.getById(taskId)
    }

    fun getTasksByDateRange(startDate: Long, endDate: Long): Flow<List<InterventionTaskEntity>> {
        return taskDao.getByDateRange(startDate, endDate)
    }

    suspend fun getNextPendingTask(): InterventionTaskEntity? {
        return taskDao.getNextPendingTask()
    }

    suspend fun updateTaskStatus(taskId: String, status: InterventionTaskStatus) {
        taskDao.updateStatus(taskId = taskId, status = status.name, updatedAt = System.currentTimeMillis())
    }

    suspend fun insertExecution(execution: InterventionExecutionEntity) {
        executionDao.insert(execution)
    }

    fun getCompletionSummary(startDate: Long, endDate: Long): Flow<InterventionCompletionSummary> {
        return taskDao.getCompletionSummary(startDate, endDate)
    }

    fun getExecutionSummary(startTime: Long, endTime: Long): Flow<InterventionExecutionSummary> {
        return executionDao.getExecutionSummary(startTime, endTime)
    }

    suspend fun getLatestExecution(): InterventionExecutionEntity? {
        return executionDao.getLatest()
    }
}

enum class InterventionTaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    SKIPPED,
    FAILED
}

enum class InterventionTaskSourceType {
    AI_COACH,
    MEDICAL_REPORT,
    RULE_ENGINE,
    RULE_IMMEDIATE,
    LLM_ENHANCED
}
