package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface TrainingLogRepository {
    suspend fun getPersonalRecordForLift(liftId: LiftId): LoggedSet?

    /** All logged sets for [liftId], newest first by [LoggedSet.completedAt]. */
    suspend fun getSetsForLift(liftId: LiftId): List<LoggedSet>

    /**
     * All logged sets whose [LoggedSet.liftId] is in [liftIds]. Empty [liftIds] returns empty
     * without a full-table scan when the data layer can short-circuit.
     */
    suspend fun getSetsForLifts(liftIds: Collection<LiftId>): List<LoggedSet>

    suspend fun updateSets(sets: List<LoggedSet>)
    suspend fun replaceAll(sessions: List<TrainingSession>, sets: List<LoggedSet>)
    suspend fun replaceImportHistory(sessions: List<TrainingSession>, sets: List<LoggedSet>)

    /**
     * Inserts only; does not delete prior IMPORT or LIVE rows.
     * Used by Settings merge-safe import ([com.stratum.domain.importing.ImportHistoryPersistPolicy.MergeSkipDuplicates]).
     */
    suspend fun appendImportHistory(sessions: List<TrainingSession>, sets: List<LoggedSet>)

    suspend fun getAllSessions(): List<TrainingSession>
    suspend fun getAllSets(): List<LoggedSet>

    /**
     * Earliest logged-set timestamp per lift across all history. Keys are lifts with at least one
     * logged set; used by the dashboard to answer "how long have I been on this lift?"
     */
    suspend fun getFirstSetDatePerLift(): Map<LiftId, Instant>
    suspend fun getImportSessions(): List<TrainingSession>
    suspend fun getImportSets(): List<LoggedSet>
    suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet>

    /**
     * All-time logged sets for lifts that credit [muscle] as primary or secondary in the catalog
     * (SQL join on lifts primary/secondary muscle bitmasks — not load-all-then-filter).
     */
    suspend fun getSetsCreditingMuscle(muscle: MuscleGroup): List<LoggedSet>

    suspend fun getSession(id: TrainingSessionId): TrainingSession?
    suspend fun getSetsForSession(sessionId: TrainingSessionId): List<LoggedSet>
    suspend fun getCompletedSessionsForCycle(programId: ProgramId, cycleNumber: Int): List<TrainingSession>
    suspend fun getActiveSession(): TrainingSession?
    fun observeActiveSession(): Flow<TrainingSession?>
    fun observeAllSessions(): Flow<List<TrainingSession>>
    suspend fun insertSession(session: TrainingSession)
    suspend fun updateSession(session: TrainingSession)
    suspend fun updateSessionAndSets(session: TrainingSession, sets: List<LoggedSet>)
    suspend fun deleteSession(id: TrainingSessionId)
    suspend fun insertSet(set: LoggedSet)
    suspend fun updateSet(set: LoggedSet)
    suspend fun deleteSet(id: LoggedSetId)

    /**
     * Atomically applies deletes, inserts, and updates to logged sets (single Room transaction).
     * Used by historical session amendment so a mid-write crash cannot leave a partial set list.
     */
    suspend fun applySetMutations(
        deleteIds: Collection<LoggedSetId>,
        inserts: List<LoggedSet>,
        updates: List<LoggedSet>,
    )

    /**
     * Atomically persists a completed session and optional cycle-pointer / change-event side effects.
     * Used when finalizing or skipping a program workout so ledger and pointer stay consistent.
     */
    suspend fun persistSessionWithCycleEffects(
        session: TrainingSession,
        cyclePointerUpdate: CyclePointerUpdate?,
        cycleCompletedEvent: ProgramChangeEvent?,
    ) =
        persistSessionsWithCycleEffects(
            sessions = listOf(session),
            cyclePointerUpdate = cyclePointerUpdate,
            cycleCompletedEvent = cycleCompletedEvent,
        )

    /**
     * Atomically persists one or more sessions (e.g. program workout + auto-materialised mid-cycle
     * REST_DAY rows) and optional cycle-pointer / change-event side effects.
     */
    suspend fun persistSessionsWithCycleEffects(
        sessions: List<TrainingSession>,
        cyclePointerUpdate: CyclePointerUpdate?,
        cycleCompletedEvent: ProgramChangeEvent?,
    )

    /**
     * Atomically deletes sessions and applies optional cycle-pointer / change-event side effects.
     * Used when unskipping so ledger and pointer stay consistent (mirror of
     * [persistSessionWithCycleEffects]).
     */
    suspend fun deleteSessionsWithCycleEffects(
        sessionIds: Collection<TrainingSessionId>,
        cyclePointerUpdate: CyclePointerUpdate?,
        cycleCompletedEvent: ProgramChangeEvent?,
    )
}
