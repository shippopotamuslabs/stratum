package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.ProgramChangeEventDao
import com.stratum.core.data.db.ProgramDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.TrainingLoggedSetDao
import com.stratum.core.data.db.TrainingSessionDao
import com.stratum.core.data.mapper.importHistoryProgramEntity
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.core.data.mapper.toTrainingDomain
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.CyclePointerUpdate
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.ProgramChangeEvent
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
    private val programDao: ProgramDao,
    private val programChangeEventDao: ProgramChangeEventDao,
) : TrainingLogRepository {
    override suspend fun replaceAll(sessions: List<TrainingSession>, sets: List<LoggedSet>) {
        database.withTransaction {
            sessionDao.deleteAll()
            setDao.deleteAll()
            if (sessions.isNotEmpty()) {
                sessionDao.upsertAll(sessions.map { it.toEntity() })
            }
            if (sets.isNotEmpty()) {
                setDao.upsertAll(sets.map { it.toEntity() })
            }
        }
    }

    override suspend fun replaceImportHistory(sessions: List<TrainingSession>, sets: List<LoggedSet>) {
        database.withTransaction {
            programDao.upsert(importHistoryProgramEntity())
            setDao.deleteAllForImportedSessions()
            sessionDao.deleteAllImported()
            if (sessions.isNotEmpty()) {
                sessionDao.upsertAll(sessions.map { it.toEntity() })
            }
            if (sets.isNotEmpty()) {
                setDao.upsertAll(sets.map { it.toEntity() })
            }
        }
    }

    override suspend fun getImportSessions(): List<TrainingSession> =
        sessionDao.getAllImported().map { it.toDomain() }

    override suspend fun getImportSets(): List<LoggedSet> =
        setDao.getAllForImportedSessions().map { it.toTrainingDomain() }

    override suspend fun getAllSessions(): List<TrainingSession> =
        sessionDao.getAll().map { it.toDomain() }

    override suspend fun getAllSets(): List<LoggedSet> =
        setDao.getAll().map { it.toTrainingDomain() }

    override suspend fun getFirstSetDatePerLift(): Map<com.stratum.domain.lift.identity.LiftId, Instant> =
        setDao.getFirstSetEpochPerLift().associate { row ->
            com.stratum.domain.lift.identity.LiftId(row.liftId) to Instant.ofEpochMilli(row.firstEpochMillis)
        }

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

    override fun observeAllSessions(): Flow<List<TrainingSession>> =
        sessionDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun insertSession(session: TrainingSession) {
        sessionDao.upsert(session.toEntity())
    }

    override suspend fun updateSession(session: TrainingSession) {
        sessionDao.upsert(session.toEntity())
    }

    override suspend fun deleteSession(id: TrainingSessionId) {
        sessionDao.deleteById(id)
    }

    override suspend fun insertSet(set: LoggedSet) {
        setDao.upsert(set.toEntity())
    }

    override suspend fun updateSet(set: LoggedSet) {
        setDao.upsert(set.toEntity())
    }

    override suspend fun deleteSet(id: LoggedSetId) {
        setDao.deleteById(id)
    }

    override suspend fun getPersonalRecordForLift(liftId: com.stratum.domain.lift.identity.LiftId): LoggedSet? =
        setDao.getPersonalRecordForLift(liftId)?.toTrainingDomain()

    override suspend fun getSetsForLift(liftId: com.stratum.domain.lift.identity.LiftId): List<LoggedSet> =
        setDao.getSetsForLift(liftId).map { it.toTrainingDomain() }

    override suspend fun updateSets(sets: List<LoggedSet>) {
        if (sets.isNotEmpty()) {
            setDao.upsertAll(sets.map { it.toEntity() })
        }
    }

    override suspend fun applySetMutations(
        deleteIds: Collection<LoggedSetId>,
        inserts: List<LoggedSet>,
        updates: List<LoggedSet>,
    ) {
        database.withTransaction {
            deleteIds.forEach { setDao.deleteById(it) }
            if (inserts.isNotEmpty()) {
                setDao.upsertAll(inserts.map { it.toEntity() })
            }
            if (updates.isNotEmpty()) {
                setDao.upsertAll(updates.map { it.toEntity() })
            }
        }
    }

    override suspend fun persistSessionWithCycleEffects(
        session: TrainingSession,
        cyclePointerUpdate: CyclePointerUpdate?,
        cycleCompletedEvent: ProgramChangeEvent?,
    ) {
        database.withTransaction {
            sessionDao.upsert(session.toEntity())
            cyclePointerUpdate?.let { update ->
                programDao.updateCyclePointer(
                    programId = update.programId,
                    currentCycleNumber = update.currentCycleNumber,
                    cyclePosition = update.cyclePosition,
                )
            }
            cycleCompletedEvent?.let { event ->
                programChangeEventDao.upsert(event.toEntity())
            }
        }
    }
}
