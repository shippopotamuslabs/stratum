package com.stratum.core.testing.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.training.CyclePointerUpdate
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    override suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet> =
        getAllSets().filter { it.completedAt in windowStart..windowEnd }

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

    private fun refreshActiveSession() {
        activeSessionFlow.value = sessions.values.firstOrNull { it.status == TrainingSessionStatus.IN_PROGRESS }
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
