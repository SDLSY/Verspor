package com.example.newstart.repository

import com.example.newstart.database.dao.DoctorAssessmentDao
import com.example.newstart.database.dao.DoctorMessageDao
import com.example.newstart.database.dao.DoctorSessionDao
import com.example.newstart.database.entity.DoctorAssessmentEntity
import com.example.newstart.database.entity.DoctorMessageEntity
import com.example.newstart.database.entity.DoctorSessionEntity

class DoctorConversationRepository(
    private val sessionDao: DoctorSessionDao,
    private val assessmentDao: DoctorAssessmentDao,
    private val messageDao: DoctorMessageDao
) {

    suspend fun upsertSession(session: DoctorSessionEntity) {
        sessionDao.insert(session)
    }

    suspend fun getSession(sessionId: String): DoctorSessionEntity? {
        return sessionDao.getById(sessionId)
    }

    suspend fun getLatestSession(): DoctorSessionEntity? {
        return sessionDao.getLatest()
    }

    suspend fun getRecentSessions(limit: Int): List<DoctorSessionEntity> {
        return sessionDao.getRecent(limit)
    }

    suspend fun saveMessage(message: DoctorMessageEntity) {
        messageDao.insert(message)
    }

    suspend fun getMessages(sessionId: String, limit: Int): List<DoctorMessageEntity> {
        return messageDao.getRecentForSession(sessionId, limit)
    }

    suspend fun saveAssessment(assessment: DoctorAssessmentEntity) {
        assessmentDao.insert(assessment)
    }

    suspend fun getLatestAssessment(sessionId: String): DoctorAssessmentEntity? {
        return assessmentDao.getLatestForSession(sessionId)
    }
}
