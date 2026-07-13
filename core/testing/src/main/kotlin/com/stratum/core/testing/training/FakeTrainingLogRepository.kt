package com.stratum.core.testing.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.response.MuscleSetQueryBits
import com.stratum.domain.training.CyclePointerUpdate
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.toMovementPatternsBitmask
import com.stratum.domain.training.toMusclesBitmask
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake for [TrainingLogRepository] — large surface mirrors the port (+ seed helpers for tests). */
@Suppress("TooManyFunctions")
class FakeTrainingLogRepository(
    initialSessions: List<TrainingSession> = emptyList(),
    initialSets: List<LoggedSet> = emptyList(),
    private val programRepository: ProgramRepository? = null,
    private val programChangeEventRepository: ProgramChangeEventRepository? = null,
) : TrainingLogRepository {
    private val sessions = initialSessions.associateBy { it.id }.toMutableMap()
    private val setsBySession = initialSets.groupBy { it.sessionId }.mapValues { (_, sets) ->
        sets.associateBy { it.id }.toMutableMap()
    }.toMutableMap()

    private val activeSessionFlow = MutableStateFlow<TrainingSession?>(
        initialSessions.firstOrNull { it.status == TrainingSessionStatus.IN_PROGRESS },
    )
    private val sessionsFlow = MutableStateFlow(sessions.values.toList())

    var insertSetCallCount: Int = 0

    suspend fun seedSession(session: TrainingSession) {
        insertSession(session)
    }

    suspend fun seedSet(set: LoggedSet) {
        insertSet(set)
    }

    override suspend fun replaceAll(sessions: List<TrainingSession>, sets: List<LoggedSet>) {
        this.sessions.clear()
        setsBySession.clear()
        sessions.forEach { insertSession(it) }
        sets.forEach { insertSet(it) }
        refreshActiveSession()
    }

    override suspend fun replaceImportHistory(sessions: List<TrainingSession>, sets: List<LoggedSet>) {
        getImportSessions().forEach { deleteSession(it.id) }
        sessions.forEach { insertSession(it) }
        sets.forEach { insertSet(it) }
        refreshActiveSession()
    }

    override suspend fun getImportSessions(): List<TrainingSession> =
        sessions.values.filter { it.origin == TrainingSessionOrigin.IMPORT }

    override suspend fun getImportSets(): List<LoggedSet> {
        val importSessionIds = getImportSessions().map { it.id }.toSet()
        return getAllSets().filter { it.sessionId in importSessionIds }
    }

    override suspend fun getAllSessions(): List<TrainingSession> = sessions.values.toList()

    override suspend fun getAllSets(): List<LoggedSet> =
        setsBySession.values.flatMap { it.values }

    override suspend fun getFirstSetDatePerLift(): Map<LiftId, Instant> =
        getAllSets()
            .filter { it.liftId != null }
            .groupBy { it.liftId!! }
            .mapValues { (_, liftSets) -> liftSets.minOf { it.completedAt } }

    override suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet> =
        getAllSets().filter { it.completedAt in windowStart..windowEnd }

    /**
     * Mirrors Room `getForMuscleCredit`: catalog tags + press/pull policy pattern bits.
     * Seed catalog rows via [seedLiftForMuscleFilter] (same bits as [com.stratum.domain.lift.Lift]).
     * Sets whose lift has no seed entry are excluded (no keep-all fallback).
     */
    override suspend fun getSetsCreditingMuscle(muscle: MuscleGroup): List<LoggedSet> =
        getAllSets().filter { set ->
            val liftId = set.liftId ?: return@filter false
            val tags = liftFilterCatalog[liftId] ?: return@filter false
            MuscleSetQueryBits.liftCreditsMuscleInSql(
                muscle = muscle,
                primaryMusclesBitmask = tags.primaryBitmask,
                secondaryMusclesBitmask = tags.secondaryBitmask,
                movementPatternsBitmask = tags.patternsBitmask,
            )
        }

    /**
     * Registers catalog muscle/pattern bits for [getSetsCreditingMuscle] (Room-parity).
     * Prefer [seedLiftForMuscleFilter] with a [Lift] so bits match production encoding.
     */
    fun seedLiftForMuscleFilter(lift: Lift) {
        liftFilterCatalog[lift.id] = LiftFilterTags(
            primaryBitmask = lift.primaryMuscles.toMusclesBitmask(),
            secondaryBitmask = lift.secondaryMuscles.toMusclesBitmask(),
            patternsBitmask = lift.movementPatterns.toMovementPatternsBitmask(),
        )
    }

    fun seedLiftForMuscleFilter(
        liftId: LiftId,
        primaryMusclesBitmask: Int,
        secondaryMusclesBitmask: Int = 0,
        movementPatternsBitmask: Int = 0,
    ) {
        liftFilterCatalog[liftId] = LiftFilterTags(
            primaryBitmask = primaryMusclesBitmask,
            secondaryBitmask = secondaryMusclesBitmask,
            patternsBitmask = movementPatternsBitmask,
        )
    }

    private data class LiftFilterTags(
        val primaryBitmask: Int,
        val secondaryBitmask: Int,
        val patternsBitmask: Int,
    )

    private val liftFilterCatalog = mutableMapOf<LiftId, LiftFilterTags>()

    override suspend fun getSession(id: TrainingSessionId): TrainingSession? = sessions[id]

    override suspend fun getSetsForSession(sessionId: TrainingSessionId): List<LoggedSet> =
        setsBySession[sessionId]?.values?.toList().orEmpty()

    override suspend fun getCompletedSessionsForCycle(
        programId: ProgramId,
        cycleNumber: Int,
    ): List<TrainingSession> =
        sessions.values.filter {
            it.programId == programId &&
                it.cycleNumberSnapshot == cycleNumber &&
                it.status == TrainingSessionStatus.COMPLETED
        }

    override suspend fun getActiveSession(): TrainingSession? =
        sessions.values.firstOrNull { it.status == TrainingSessionStatus.IN_PROGRESS }

    override fun observeActiveSession(): Flow<TrainingSession?> = activeSessionFlow.asStateFlow()

    override fun observeAllSessions(): Flow<List<TrainingSession>> = sessionsFlow.asStateFlow()

    private fun refreshActiveSession() {
        activeSessionFlow.value = sessions.values.firstOrNull { it.status == TrainingSessionStatus.IN_PROGRESS }
        sessionsFlow.value = sessions.values.toList()
    }

    override suspend fun insertSession(session: TrainingSession) {
        sessions[session.id] = session
        refreshActiveSession()
    }

    override suspend fun updateSession(session: TrainingSession) {
        sessions[session.id] = session
        refreshActiveSession()
    }

    override suspend fun deleteSession(id: TrainingSessionId) {
        sessions.remove(id)
        setsBySession.remove(id)
        refreshActiveSession()
    }

    override suspend fun insertSet(set: LoggedSet) {
        insertSetCallCount += 1
        val bucket = setsBySession.getOrPut(set.sessionId) { mutableMapOf() }
        bucket[set.id] = set
    }

    override suspend fun updateSet(set: LoggedSet) {
        val bucket = setsBySession.getOrPut(set.sessionId) { mutableMapOf() }
        bucket[set.id] = set
    }

    override suspend fun deleteSet(id: LoggedSetId) {
        setsBySession.values.forEach { bucket -> bucket.remove(id) }
    }

    override suspend fun getPersonalRecordForLift(liftId: com.stratum.domain.lift.identity.LiftId): LoggedSet? {
        return getAllSets().firstOrNull { it.liftId == liftId && it.isPersonalRecord }
    }

    override suspend fun getSetsForLift(liftId: LiftId): List<LoggedSet> =
        getAllSets()
            .filter { it.liftId == liftId }
            .sortedByDescending { it.completedAt }

    override suspend fun getSetsForLifts(liftIds: Collection<LiftId>): List<LoggedSet> {
        if (liftIds.isEmpty()) return emptyList()
        val wanted = liftIds.toSet()
        return getAllSets().filter { set -> set.liftId != null && set.liftId in wanted }
    }

    override suspend fun updateSets(sets: List<LoggedSet>) {
        sets.forEach { updateSet(it) }
    }

    override suspend fun applySetMutations(
        deleteIds: Collection<LoggedSetId>,
        inserts: List<LoggedSet>,
        updates: List<LoggedSet>,
    ) {
        deleteIds.forEach { deleteSet(it) }
        inserts.forEach { insertSet(it) }
        updates.forEach { updateSet(it) }
    }

    override suspend fun persistSessionWithCycleEffects(
        session: TrainingSession,
        cyclePointerUpdate: CyclePointerUpdate?,
        cycleCompletedEvent: ProgramChangeEvent?,
    ) {
        if (sessions.containsKey(session.id)) {
            updateSession(session)
        } else {
            insertSession(session)
        }
        cyclePointerUpdate?.let { update ->
            programRepository?.updateCyclePointer(
                programId = update.programId,
                currentCycleNumber = update.currentCycleNumber,
                cyclePosition = update.cyclePosition,
            )
        }
        cycleCompletedEvent?.let { event ->
            programChangeEventRepository?.append(event)
        }
    }
}
