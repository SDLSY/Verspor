package com.example.newstart.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.newstart.database.dao.DeviceDao
import com.example.newstart.database.dao.DoctorAssessmentDao
import com.example.newstart.database.dao.DoctorMessageDao
import com.example.newstart.database.dao.DoctorSessionDao
import com.example.newstart.database.dao.FoodAnalysisDao
import com.example.newstart.database.dao.HealthMetricsDao
import com.example.newstart.database.dao.AssessmentAnswerDao
import com.example.newstart.database.dao.AssessmentSessionDao
import com.example.newstart.database.dao.InterventionExecutionDao
import com.example.newstart.database.dao.InterventionProfileSnapshotDao
import com.example.newstart.database.dao.InterventionTaskDao
import com.example.newstart.database.dao.MedicalMetricDao
import com.example.newstart.database.dao.MedicationAnalysisDao
import com.example.newstart.database.dao.MedicalReportDao
import com.example.newstart.database.dao.PpgSampleDao
import com.example.newstart.database.dao.PrescriptionBundleDao
import com.example.newstart.database.dao.PrescriptionItemDao
import com.example.newstart.database.dao.RecoveryScoreDao
import com.example.newstart.database.dao.RelaxSessionDao
import com.example.newstart.database.dao.SleepDataDao
import com.example.newstart.database.entity.AssessmentAnswerEntity
import com.example.newstart.database.entity.AssessmentSessionEntity
import com.example.newstart.database.entity.DeviceEntity
import com.example.newstart.database.entity.DoctorAssessmentEntity
import com.example.newstart.database.entity.DoctorMessageEntity
import com.example.newstart.database.entity.DoctorSessionEntity
import com.example.newstart.database.entity.FoodAnalysisEntity
import com.example.newstart.database.entity.HealthMetricsEntity
import com.example.newstart.database.entity.InterventionExecutionEntity
import com.example.newstart.database.entity.InterventionProfileSnapshotEntity
import com.example.newstart.database.entity.InterventionTaskEntity
import com.example.newstart.database.entity.MedicalMetricEntity
import com.example.newstart.database.entity.MedicationAnalysisEntity
import com.example.newstart.database.entity.MedicalReportEntity
import com.example.newstart.database.entity.PpgSampleEntity
import com.example.newstart.database.entity.PrescriptionBundleEntity
import com.example.newstart.database.entity.PrescriptionItemEntity
import com.example.newstart.database.entity.RecoveryScoreEntity
import com.example.newstart.database.entity.RelaxSessionEntity
import com.example.newstart.database.entity.SleepDataEntity

@Database(
    entities = [
        SleepDataEntity::class,
        HealthMetricsEntity::class,
        RecoveryScoreEntity::class,
        DeviceEntity::class,
        DoctorMessageEntity::class,
        DoctorSessionEntity::class,
        DoctorAssessmentEntity::class,
        AssessmentSessionEntity::class,
        AssessmentAnswerEntity::class,
        PpgSampleEntity::class,
        RelaxSessionEntity::class,
        InterventionTaskEntity::class,
        InterventionExecutionEntity::class,
        MedicalReportEntity::class,
        MedicalMetricEntity::class,
        InterventionProfileSnapshotEntity::class,
        PrescriptionBundleEntity::class,
        PrescriptionItemEntity::class,
        MedicationAnalysisEntity::class,
        FoodAnalysisEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sleepDataDao(): SleepDataDao
    abstract fun healthMetricsDao(): HealthMetricsDao
    abstract fun recoveryScoreDao(): RecoveryScoreDao
    abstract fun deviceDao(): DeviceDao
    abstract fun doctorMessageDao(): DoctorMessageDao
    abstract fun doctorSessionDao(): DoctorSessionDao
    abstract fun doctorAssessmentDao(): DoctorAssessmentDao
    abstract fun assessmentSessionDao(): AssessmentSessionDao
    abstract fun assessmentAnswerDao(): AssessmentAnswerDao
    abstract fun ppgSampleDao(): PpgSampleDao
    abstract fun relaxSessionDao(): RelaxSessionDao
    abstract fun interventionTaskDao(): InterventionTaskDao
    abstract fun interventionExecutionDao(): InterventionExecutionDao
    abstract fun medicalReportDao(): MedicalReportDao
    abstract fun medicalMetricDao(): MedicalMetricDao
    abstract fun interventionProfileSnapshotDao(): InterventionProfileSnapshotDao
    abstract fun prescriptionBundleDao(): PrescriptionBundleDao
    abstract fun prescriptionItemDao(): PrescriptionItemDao
    abstract fun medicationAnalysisDao(): MedicationAnalysisDao
    abstract fun foodAnalysisDao(): FoodAnalysisDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "sleep_health_database"

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS intervention_tasks (
                        id TEXT NOT NULL PRIMARY KEY,
                        date INTEGER NOT NULL,
                        sourceType TEXT NOT NULL,
                        triggerReason TEXT NOT NULL,
                        bodyZone TEXT NOT NULL,
                        protocolType TEXT NOT NULL,
                        durationSec INTEGER NOT NULL,
                        plannedAt INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_tasks_date ON intervention_tasks(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_tasks_status ON intervention_tasks(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_tasks_createdAt ON intervention_tasks(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS intervention_executions (
                        id TEXT NOT NULL PRIMARY KEY,
                        taskId TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER NOT NULL,
                        elapsedSec INTEGER NOT NULL,
                        beforeStress REAL NOT NULL,
                        afterStress REAL NOT NULL,
                        beforeHr INTEGER NOT NULL,
                        afterHr INTEGER NOT NULL,
                        effectScore REAL NOT NULL,
                        completionType TEXT NOT NULL,
                        FOREIGN KEY(taskId) REFERENCES intervention_tasks(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_executions_taskId ON intervention_executions(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_executions_startedAt ON intervention_executions(startedAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medical_reports (
                        id TEXT NOT NULL PRIMARY KEY,
                        reportDate INTEGER NOT NULL,
                        reportType TEXT NOT NULL,
                        imageUri TEXT NOT NULL,
                        ocrTextDigest TEXT NOT NULL,
                        parseStatus TEXT NOT NULL,
                        riskLevel TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_reports_reportDate ON medical_reports(reportDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_reports_parseStatus ON medical_reports(parseStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_reports_riskLevel ON medical_reports(riskLevel)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medical_metrics (
                        id TEXT NOT NULL PRIMARY KEY,
                        reportId TEXT NOT NULL,
                        metricCode TEXT NOT NULL,
                        metricName TEXT NOT NULL,
                        metricValue REAL NOT NULL,
                        unit TEXT NOT NULL,
                        refLow REAL,
                        refHigh REAL,
                        isAbnormal INTEGER NOT NULL,
                        confidence REAL NOT NULL,
                        FOREIGN KEY(reportId) REFERENCES medical_reports(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_metrics_reportId ON medical_metrics(reportId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_metrics_metricCode ON medical_metrics(metricCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_metrics_isAbnormal ON medical_metrics(isAbnormal)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS doctor_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        actionProtocolType TEXT,
                        actionDurationSec INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_messages_timestamp ON doctor_messages(timestamp)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE doctor_messages
                    ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    ALTER TABLE doctor_messages
                    ADD COLUMN messageType TEXT NOT NULL DEFAULT 'TEXT'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    ALTER TABLE doctor_messages
                    ADD COLUMN payloadJson TEXT
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_doctor_messages_sessionId_timestamp
                    ON doctor_messages(sessionId, timestamp)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS doctor_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        domain TEXT NOT NULL,
                        chiefComplaint TEXT NOT NULL,
                        riskLevel TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_sessions_updatedAt ON doctor_sessions(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_sessions_status ON doctor_sessions(status)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS doctor_assessments (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        suspectedIssuesJson TEXT NOT NULL,
                        symptomFactsJson TEXT NOT NULL,
                        missingInfoJson TEXT NOT NULL,
                        redFlagsJson TEXT NOT NULL,
                        recommendedDepartment TEXT NOT NULL,
                        doctorSummary TEXT NOT NULL,
                        nextStepAdviceJson TEXT NOT NULL,
                        disclaimer TEXT NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES doctor_sessions(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_assessments_sessionId ON doctor_assessments(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_assessments_createdAt ON doctor_assessments(createdAt)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS assessment_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        scaleCode TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        totalScore INTEGER NOT NULL,
                        severityLevel TEXT NOT NULL,
                        freshnessUntil INTEGER NOT NULL,
                        source TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assessment_sessions_scaleCode ON assessment_sessions(scaleCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assessment_sessions_completedAt ON assessment_sessions(completedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assessment_sessions_freshnessUntil ON assessment_sessions(freshnessUntil)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS assessment_answers (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        itemCode TEXT NOT NULL,
                        itemOrder INTEGER NOT NULL,
                        answerValue INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES assessment_sessions(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assessment_answers_sessionId ON assessment_answers(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assessment_answers_itemCode ON assessment_answers(itemCode)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS intervention_profile_snapshots (
                        id TEXT NOT NULL PRIMARY KEY,
                        generatedAt INTEGER NOT NULL,
                        triggerType TEXT NOT NULL,
                        domainScoresJson TEXT NOT NULL,
                        evidenceFactsJson TEXT NOT NULL,
                        redFlagsJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_profile_snapshots_generatedAt ON intervention_profile_snapshots(generatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_profile_snapshots_triggerType ON intervention_profile_snapshots(triggerType)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS prescription_bundles (
                        id TEXT NOT NULL PRIMARY KEY,
                        createdAt INTEGER NOT NULL,
                        triggerType TEXT NOT NULL,
                        profileSnapshotId TEXT NOT NULL,
                        primaryGoal TEXT NOT NULL,
                        riskLevel TEXT NOT NULL,
                        rationale TEXT NOT NULL,
                        evidenceJson TEXT NOT NULL,
                        status TEXT NOT NULL,
                        FOREIGN KEY(profileSnapshotId) REFERENCES intervention_profile_snapshots(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prescription_bundles_createdAt ON prescription_bundles(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prescription_bundles_triggerType ON prescription_bundles(triggerType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prescription_bundles_profileSnapshotId ON prescription_bundles(profileSnapshotId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prescription_bundles_status ON prescription_bundles(status)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS prescription_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        bundleId TEXT NOT NULL,
                        itemType TEXT NOT NULL,
                        protocolCode TEXT NOT NULL,
                        assetRef TEXT NOT NULL,
                        durationSec INTEGER NOT NULL,
                        sequenceOrder INTEGER NOT NULL,
                        timingSlot TEXT NOT NULL,
                        isRequired INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        FOREIGN KEY(bundleId) REFERENCES prescription_bundles(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prescription_items_bundleId ON prescription_items(bundleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prescription_items_protocolCode ON prescription_items(protocolCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prescription_items_timingSlot ON prescription_items(timingSlot)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medication_analysis_records (
                        id TEXT NOT NULL PRIMARY KEY,
                        capturedAt INTEGER NOT NULL,
                        imageUri TEXT NOT NULL,
                        recognizedName TEXT NOT NULL,
                        dosageForm TEXT NOT NULL,
                        specification TEXT NOT NULL,
                        activeIngredientsJson TEXT NOT NULL,
                        matchedSymptomsJson TEXT NOT NULL,
                        usageSummary TEXT NOT NULL,
                        riskLevel TEXT NOT NULL,
                        riskFlagsJson TEXT NOT NULL,
                        evidenceNotesJson TEXT NOT NULL,
                        advice TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        requiresManualReview INTEGER NOT NULL,
                        analysisMode TEXT NOT NULL,
                        providerId TEXT,
                        modelId TEXT,
                        traceId TEXT,
                        syncState TEXT NOT NULL,
                        cloudRecordId TEXT,
                        syncedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_analysis_records_capturedAt ON medication_analysis_records(capturedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_analysis_records_riskLevel ON medication_analysis_records(riskLevel)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_analysis_records_syncState ON medication_analysis_records(syncState)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_analysis_records_requiresManualReview ON medication_analysis_records(requiresManualReview)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_analysis_records (
                        id TEXT NOT NULL PRIMARY KEY,
                        capturedAt INTEGER NOT NULL,
                        imageUri TEXT NOT NULL,
                        mealType TEXT NOT NULL,
                        foodItemsJson TEXT NOT NULL,
                        estimatedCalories INTEGER NOT NULL,
                        carbohydrateGrams REAL NOT NULL,
                        proteinGrams REAL NOT NULL,
                        fatGrams REAL NOT NULL,
                        nutritionRiskLevel TEXT NOT NULL,
                        nutritionFlagsJson TEXT NOT NULL,
                        dailyContribution TEXT NOT NULL,
                        advice TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        requiresManualReview INTEGER NOT NULL,
                        analysisMode TEXT NOT NULL,
                        providerId TEXT,
                        modelId TEXT,
                        traceId TEXT,
                        syncState TEXT NOT NULL,
                        cloudRecordId TEXT,
                        syncedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_analysis_records_capturedAt ON food_analysis_records(capturedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_analysis_records_nutritionRiskLevel ON food_analysis_records(nutritionRiskLevel)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_analysis_records_syncState ON food_analysis_records(syncState)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_analysis_records_requiresManualReview ON food_analysis_records(requiresManualReview)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE relax_sessions
                    ADD COLUMN metadataJson TEXT
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    ALTER TABLE intervention_executions
                    ADD COLUMN metadataJson TEXT
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                    .addMigrations(MIGRATION_8_9)
                    .addMigrations(MIGRATION_9_10)
                    .addMigrations(MIGRATION_10_11)
                    .addMigrations(MIGRATION_11_12)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}
