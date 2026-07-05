package com.stratum.core.testing.importing

import com.stratum.domain.importing.HistoricalSession
import com.stratum.domain.importing.ImportHistoryTrainingMapper
import com.stratum.domain.importing.LoggedSet
import com.stratum.domain.importing.TrainingHistoryRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.DefaultLoggedLiftContextFactory
import com.stratum.domain.training.LoggedLiftContextFactory
import com.stratum.domain.training.TrainingLogRepository
import java.time.Instant

class FakeTrainingHistoryRepository(
    private val trainingLogMirror: TrainingLogRepository? = null,
    private val mirrorLifts: Map<LiftId, Lift> = emptyMap(),
    private val mirrorLiftContextFactory: LoggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
) : TrainingHistoryRepository {
    private var sessions: List<HistoricalSession> = emptyList()
    private var sets: List<LoggedSet> = emptyList()

    fun seed(sessions: List<HistoricalSession>, sets: List<LoggedSet>) {
        this.sessions = sessions
        this.sets = sets
    }

    override suspend fun replaceAll(sessions: List<HistoricalSession>, sets: List<LoggedSet>) {
        this.sessions = sessions
        this.sets = sets
        trainingLogMirror?.replaceAll(
            sessions.map(ImportHistoryTrainingMapper::toTrainingSession),
            sets.map { set ->
                ImportHistoryTrainingMapper.toTrainingLoggedSet(
                    set = set,
                    lifts = mirrorLifts,
                    factory = mirrorLiftContextFactory,
                )
            },
        )
    }

    override suspend fun getAllSessions(): List<HistoricalSession> = sessions

    override suspend fun getAllSets(): List<LoggedSet> = sets

    override suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet> =
        sets.filter { it.completedAt in windowStart..windowEnd }
}
