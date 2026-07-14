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
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.response.MuscleSetQueryBits
import com.stratum.domain.training.CyclePointerUpdate
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomTrainingLogRepository @Inject constructor(
    private val database: StratumDatabase,
    private val sessionDao: TrainingSessionDao,
    private val setDao: TrainingLoggedSetDao,
    private val programDao: ProgramDao,
    private val programChangeEventDao: ProgramChangeEventDao,
    private val syncTouch: SyncTouch,
) : TrainingLogRepository {
    override suspend fun replaceAll(sessions: List<TrainingSession>, sets: List<LoggedSet>) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                val oldSessionIds = sessionDao.getAll().map { it.id.value }
                val oldSetIds = setDao.getAll().map { it.id.value }
                syncTouch.replaceIds(
                    SyncCollection.TrainingSessions,
                    previousIds = oldSessionIds,
                    nextIds = sessions.map { it.id.value },
                    nowMillis = now,
                )
                syncTouch.replaceIds(
                    SyncCollection.LoggedSets,
                    previousIds = oldSetIds,
                    nextIds = sets.map { it.id.value },
                    nowMillis = now,
                )
                sessionDao.deleteAll()
                setDao.deleteAll()
                val nextSessions = sessions.map { it.toEntity(nowMillis = now) }
                val nextSets = sets.map { it.toEntity(nowMillis = now) }
                if (nextSessions.isNotEmpty()) sessionDao.upsertAll(nextSessions)
                if (nextSets.isNotEmpty()) setDao.upsertAll(nextSets)
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun replaceImportHistory(sessions: List<TrainingSession>, sets: List<LoggedSet>) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                programDao.upsert(importHistoryProgramEntity())
                val oldSessionIds = sessionDao.getAllImported().map { it.id.value }
                val oldSetIds = setDao.getAllForImportedSessions().map { it.id.value }
                syncTouch.replaceIds(
                    SyncCollection.TrainingSessions,
                    previousIds = oldSessionIds,
                    nextIds = sessions.map { it.id.value },
                    nowMillis = now,
                )
                syncTouch.replaceIds(
                    SyncCollection.LoggedSets,
                    previousIds = oldSetIds,
                    nextIds = sets.map { it.id.value },
                    nowMillis = now,
                )
                setDao.deleteAllForImportedSessions()
                sessionDao.deleteAllImported()
                val nextSessions = sessions.map { it.toEntity(nowMillis = now) }
                val nextSets = sets.map { it.toEntity(nowMillis = now) }
                if (nextSessions.isNotEmpty()) sessionDao.upsertAll(nextSessions)
                if (nextSets.isNotEmpty()) setDao.upsertAll(nextSets)
            }
            syncTouch.markDirty()
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

    override suspend fun getSetsCreditingMuscle(muscle: MuscleGroup): List<LoggedSet> =
        setDao.getForMuscleCredit(
            muscleBit = MuscleSetQueryBits.muscleBit(muscle),
            pressPatternBits = MuscleSetQueryBits.pressPatternBitsForPolicySecondary(muscle),
            pullPatternBits = MuscleSetQueryBits.pullPatternBitsForPolicySecondary(muscle),
        ).map { it.toTrainingDomain() }

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
        syncTouch.withWriteLock {
            sessionDao.upsert(session.toEntity())
            syncTouch.cancelDelete(SyncCollection.TrainingSessions, session.id.value)
            syncTouch.markDirty()
        }
    }

    override suspend fun updateSession(session: TrainingSession) {
        syncTouch.withWriteLock {
            sessionDao.upsert(session.toEntity())
            syncTouch.cancelDelete(SyncCollection.TrainingSessions, session.id.value)
            syncTouch.markDirty()
        }
    }

    override suspend fun deleteSession(id: TrainingSessionId) {
        syncTouch.withWriteLock {
            database.withTransaction {
                syncTouch.enqueueDelete(SyncCollection.TrainingSessions, id.value)
                val setIds = setDao.getForSession(id).map { it.id.value }
                syncTouch.enqueueDelete(SyncCollection.LoggedSets, setIds)
                sessionDao.deleteById(id)
            }
        }
    }

    override suspend fun insertSet(set: LoggedSet) {
        syncTouch.withWriteLock {
            setDao.upsert(set.toEntity())
            syncTouch.cancelDelete(SyncCollection.LoggedSets, set.id.value)
            syncTouch.markDirty()
        }
    }

    override suspend fun updateSet(set: LoggedSet) {
        syncTouch.withWriteLock {
            setDao.upsert(set.toEntity())
            syncTouch.cancelDelete(SyncCollection.LoggedSets, set.id.value)
            syncTouch.markDirty()
        }
    }

    override suspend fun deleteSet(id: LoggedSetId) {
        syncTouch.withWriteLock {
            database.withTransaction {
                syncTouch.enqueueDelete(SyncCollection.LoggedSets, id.value)
                setDao.deleteById(id)
            }
        }
    }

    override suspend fun getPersonalRecordForLift(liftId: com.stratum.domain.lift.identity.LiftId): LoggedSet? =
        setDao.getPersonalRecordForLift(liftId)?.toTrainingDomain()

    override suspend fun getSetsForLift(liftId: com.stratum.domain.lift.identity.LiftId): List<LoggedSet> =
        setDao.getSetsForLift(liftId).map { it.toTrainingDomain() }

    override suspend fun getSetsForLifts(
        liftIds: Collection<com.stratum.domain.lift.identity.LiftId>,
    ): List<LoggedSet> {
        if (liftIds.isEmpty()) return emptyList()
        return setDao.getSetsForLifts(liftIds.distinct()).map { it.toTrainingDomain() }
    }

    override suspend fun updateSets(sets: List<LoggedSet>) {
        if (sets.isEmpty()) return
        syncTouch.withWriteLock {
            setDao.upsertAll(sets.map { it.toEntity() })
            syncTouch.markDirty()
        }
    }

    override suspend fun applySetMutations(
        deleteIds: Collection<LoggedSetId>,
        inserts: List<LoggedSet>,
        updates: List<LoggedSet>,
    ) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                if (deleteIds.isNotEmpty()) {
                    syncTouch.enqueueDelete(
                        SyncCollection.LoggedSets,
                        deleteIds.map { it.value },
                        now,
                    )
                    deleteIds.forEach { setDao.deleteById(it) }
                }
                if (inserts.isNotEmpty()) {
                    val ids = inserts.map { it.id.value }
                    syncTouch.cancelDelete(SyncCollection.LoggedSets, ids)
                    setDao.upsertAll(inserts.map { it.toEntity(nowMillis = now) })
                }
                if (updates.isNotEmpty()) {
                    val ids = updates.map { it.id.value }
                    syncTouch.cancelDelete(SyncCollection.LoggedSets, ids)
                    setDao.upsertAll(updates.map { it.toEntity(nowMillis = now) })
                }
            }
            if (deleteIds.isEmpty() && (inserts.isNotEmpty() || updates.isNotEmpty())) {
                syncTouch.markDirty()
            }
        }
    }

    override suspend fun persistSessionsWithCycleEffects(
        sessions: List<TrainingSession>,
        cyclePointerUpdate: CyclePointerUpdate?,
        cycleCompletedEvent: ProgramChangeEvent?,
    ) {
        if (sessions.isEmpty() && cyclePointerUpdate == null && cycleCompletedEvent == null) return
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                for (session in sessions) {
                    sessionDao.upsert(session.toEntity(nowMillis = now))
                    syncTouch.cancelDelete(SyncCollection.TrainingSessions, session.id.value)
                }
                applyCycleEffectsInTransaction(
                    cyclePointerUpdate = cyclePointerUpdate,
                    cycleCompletedEvent = cycleCompletedEvent,
                    now = now,
                )
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun deleteSessionsWithCycleEffects(
        sessionIds: Collection<TrainingSessionId>,
        cyclePointerUpdate: CyclePointerUpdate?,
        cycleCompletedEvent: ProgramChangeEvent?,
    ) {
        if (sessionIds.isEmpty() && cyclePointerUpdate == null && cycleCompletedEvent == null) return
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                for (id in sessionIds) {
                    syncTouch.enqueueDelete(SyncCollection.TrainingSessions, id.value, now)
                    val setIds = setDao.getForSession(id).map { it.id.value }
                    if (setIds.isNotEmpty()) {
                        syncTouch.enqueueDelete(SyncCollection.LoggedSets, setIds, now)
                    }
                    sessionDao.deleteById(id)
                }
                applyCycleEffectsInTransaction(
                    cyclePointerUpdate = cyclePointerUpdate,
                    cycleCompletedEvent = cycleCompletedEvent,
                    now = now,
                )
            }
            // Parity with persistSessionWithCycleEffects; covers pointer/event-only and set deletes.
            syncTouch.markDirty()
        }
    }

    private suspend fun applyCycleEffectsInTransaction(
        cyclePointerUpdate: CyclePointerUpdate?,
        cycleCompletedEvent: ProgramChangeEvent?,
        now: Long,
    ) {
        cyclePointerUpdate?.let { update ->
            programDao.updateCyclePointer(
                programId = update.programId,
                currentCycleNumber = update.currentCycleNumber,
                cyclePosition = update.cyclePosition,
                cycleStartEpochDay = update.cycleStartEpochDay,
                updatedAtEpochMillis = now,
            )
        }
        cycleCompletedEvent?.let { event ->
            programChangeEventDao.upsert(event.toEntity(nowMillis = now))
            syncTouch.cancelDelete(SyncCollection.ProgramChangeEvents, event.id.value)
        }
    }
}
