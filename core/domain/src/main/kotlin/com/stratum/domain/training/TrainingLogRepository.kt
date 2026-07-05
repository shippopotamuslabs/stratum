package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface TrainingLogRepository {
    suspend fun replaceAll(sessions: List<TrainingSession>, sets: List<LoggedSet>)
    suspend fun getAllSessions(): List<TrainingSession>
    suspend fun getAllSets(): List<LoggedSet>
    suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet>
    suspend fun getSession(id: TrainingSessionId): TrainingSession?
    suspend fun getSetsForSession(sessionId: TrainingSessionId): List<LoggedSet>
    suspend fun getCompletedSessionsForCycle(programId: ProgramId, cycleNumber: Int): List<TrainingSession>
    suspend fun getActiveSession(): TrainingSession?
    fun observeActiveSession(): Flow<TrainingSession?>
    suspend fun insertSession(session: TrainingSession)
    suspend fun updateSession(session: TrainingSession)
    suspend fun deleteSession(id: TrainingSessionId)
    suspend fun insertSet(set: LoggedSet)
    suspend fun updateSet(set: LoggedSet)
    suspend fun deleteSet(id: LoggedSetId)
}
