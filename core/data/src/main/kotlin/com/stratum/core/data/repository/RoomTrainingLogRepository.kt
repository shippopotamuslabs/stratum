package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingLoggedSetDao
import com.stratum.core.data.db.TrainingSessionDao
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.core.data.mapper.toTrainingDomain
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTrainingLogRepository @Inject constructor(
    private val database: StratumDatabase,
    private val sessionDao: TrainingSessionDao,
    private val setDao: TrainingLoggedSetDao,
) : TrainingLogRepository {
    override suspend fun replaceAll(sessions: List<TrainingSession>, sets: List<LoggedSet>) {
        database.withTransaction {
            sessionDao.deleteAll()
            setDao.deleteAll()
            if (sessions.isNotEmpty()) {
                sessionDao.insertAll(sessions.map { it.toEntity() })
            }
            if (sets.isNotEmpty()) {
                setDao.insertAll(sets.map { it.toEntity() })
            }
        }
    }

    override suspend fun getAllSessions(): List<TrainingSession> =
        sessionDao.getAll().map { it.toDomain() }

    override suspend fun getAllSets(): List<LoggedSet> =
        setDao.getAll().map { it.toTrainingDomain() }

    override suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet> =
        setDao.getInWindow(windowStart.toEpochMilli(), windowEnd.toEpochMilli())
            .map { it.toTrainingDomain() }

    override suspend fun getSession(id: TrainingSessionId): TrainingSession? =
        sessionDao.getById(id)?.toDomain()

    override suspend fun getSetsForSession(sessionId: TrainingSessionId): List<LoggedSet> =
        setDao.getForSession(sessionId).map { it.toTrainingDomain() }

    override suspend fun getCompletedSessionsForCycle(
        programId: ProgramId,
        cycleNumber: Int,
    ): List<TrainingSession> =
        sessionDao.getCompletedForCycle(programId, cycleNumber).map { it.toDomain() }

    override suspend fun getActiveSession(): TrainingSession? =
        sessionDao.getByStatus(TrainingSessionStatus.IN_PROGRESS)?.toDomain()

    override fun observeActiveSession(): Flow<TrainingSession?> =
        sessionDao.observeByStatus(TrainingSessionStatus.IN_PROGRESS).map { entity ->
            entity?.toDomain()
        }

    override suspend fun insertSession(session: TrainingSession) {
        sessionDao.insert(session.toEntity())
    }

    override suspend fun updateSession(session: TrainingSession) {
        sessionDao.insert(session.toEntity())
    }

    override suspend fun deleteSession(id: TrainingSessionId) {
        sessionDao.deleteById(id)
    }

    override suspend fun insertSet(set: LoggedSet) {
        setDao.insert(set.toEntity())
    }

    override suspend fun updateSet(set: LoggedSet) {
        setDao.insert(set.toEntity())
    }

    override suspend fun deleteSet(id: LoggedSetId) {
        setDao.deleteById(id)
    }
}
