package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface TrainingLogRepository {
    suspend fun getPersonalRecordForLift(liftId: LiftId): LoggedSet?
    suspend fun updateSets(sets: List<LoggedSet>)
    suspend fun replaceAll(sessions: List<TrainingSession>, sets: List<LoggedSet>)
    suspend fun replaceImportHistory(sessions: List<TrainingSession>, sets: List<LoggedSet>)
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
    suspend fun getSession(id: TrainingSessionId): TrainingSession?
    suspend fun getSetsForSession(sessionId: TrainingSessionId): List<LoggedSet>
    suspend fun getCompletedSessionsForCycle(programId: ProgramId, cycleNumber: Int): List<TrainingSession>
    suspend fun getActiveSession(): TrainingSession?
    fun observeActiveSession(): Flow<TrainingSession?>
    fun observeAllSessions(): Flow<List<TrainingSession>>
    suspend fun insertSession(session: TrainingSession)
    suspend fun updateSession(session: TrainingSession)
    suspend fun deleteSession(id: TrainingSessionId)
    suspend fun insertSet(set: LoggedSet)
    suspend fun updateSet(set: LoggedSet)
    suspend fun deleteSet(id: LoggedSetId)

    /**
     * Atomically persists a completed session and optional cycle-pointer / change-event side effects.
     * Used when finalizing or skipping a program workout so ledger and pointer stay consistent.
     */
    suspend fun persistSessionWithCycleEffects(
        session: TrainingSession,
        cyclePointerUpdate: CyclePointerUpdate?,
        cycleCompletedEvent: ProgramChangeEvent?,
    )
}
