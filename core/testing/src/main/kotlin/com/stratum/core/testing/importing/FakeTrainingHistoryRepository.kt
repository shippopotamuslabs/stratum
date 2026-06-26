package com.stratum.core.testing.importing

import com.stratum.domain.importing.HistoricalSession
import com.stratum.domain.importing.LoggedSet
import com.stratum.domain.importing.TrainingHistoryRepository
import java.time.Instant

class FakeTrainingHistoryRepository : TrainingHistoryRepository {
    private var sessions: List<HistoricalSession> = emptyList()
    private var sets: List<LoggedSet> = emptyList()

    fun seed(sessions: List<HistoricalSession>, sets: List<LoggedSet>) {
        this.sessions = sessions
        this.sets = sets
    }

    override suspend fun replaceAll(sessions: List<HistoricalSession>, sets: List<LoggedSet>) {
        this.sessions = sessions
        this.sets = sets
    }

    override suspend fun getAllSessions(): List<HistoricalSession> = sessions

    override suspend fun getAllSets(): List<LoggedSet> = sets

    override suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet> =
        sets.filter { it.completedAt in windowStart..windowEnd }
}
