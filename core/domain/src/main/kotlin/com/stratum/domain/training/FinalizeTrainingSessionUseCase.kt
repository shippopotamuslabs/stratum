package com.stratum.domain.training

import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.di.DefaultDispatcher
import com.stratum.domain.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.importing.InsightsAnalysisScope
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.response.ResponseModelingSchedulerPort
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FinalizeTrainingSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val computePersonalRecords: ComputePersonalRecordsUseCase,
    private val extractTrainingInsights: ExtractTrainingInsightsUseCase,
    private val refreshWeeklySeries: RefreshWeeklySeriesUseCase,
    private val responseModelingScheduler: ResponseModelingSchedulerPort =
        NoOpResponseModelingScheduler,
    // Process-lifetime scope (SupervisorJob) so the post-completion derived-data refresh outlives
    // the ViewModel that triggered completion. Defaulted to a Main.immediate scope so JVM tests
    // can construct the use case without Hilt.
    @ApplicationScope private val refreshScope: CoroutineScope =
        CoroutineScope(Dispatchers.Main.immediate),
    // CPU-bound dispatcher for the refresh work (signal cleaning + weekly aggregation over full
    // history). Defaulted so JVM tests can construct without Hilt.
    @DefaultDispatcher private val refreshDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val logger = Logger.getLogger(FinalizeTrainingSessionUseCase::class.java.name)

    suspend operator fun invoke(sessionId: TrainingSessionId): Result<TrainingSession> = runCatching {
        val session =
            trainingLogRepository.getSession(sessionId)
                ?: throw IllegalArgumentException("Training session ${sessionId.value} not found")
        if (session.status != TrainingSessionStatus.IN_PROGRESS) {
            throw TrainingSessionNotInProgressException(sessionId, session.status)
        }

        val completed =
            session.copy(
                status = TrainingSessionStatus.COMPLETED,
                endedAt = Instant.now(),
            )

        val sessionSets = trainingLogRepository.getSetsForSession(sessionId)
        val computation = computePersonalRecords(sessionSets).getOrThrow()

        trainingLogRepository.updateSets(computation.updatedIncomingSets)
        if (computation.oldPrSetsToUpdate.isNotEmpty()) {
            trainingLogRepository.updateSets(computation.oldPrSetsToUpdate)
        }

        val cycleEffects =
            if (completed.kind == TrainingSessionKind.PROGRAM_WORKOUT) {
                val program =
                    programRepository.getProgram()
                        ?: error("No active program found to advance cycle after workout completion")
                val sessions =
                    trainingLogRepository.getAllSessions()
                        .filter { it.id != session.id } + completed
                computeCycleAdvancement(program, sessions)
            } else {
                null
            }

        trainingLogRepository.persistSessionWithCycleEffects(
            session = completed,
            cyclePointerUpdate = cycleEffects?.pointerUpdate,
            cycleCompletedEvent = cycleEffects?.cycleCompletedEvent,
        )

        // The session + cycle effects are durable above. The derived-data refresh is fire-and-forget
        // on the application scope so completion returns (and the UI navigates) immediately; the
        // dashboard tables refresh behind the user. Failures are swallowed inside refreshInsights().
        refreshScope.launch(refreshDispatcher) { refreshInsights() }

        // Schedule heavy dose-response re-extract (WorkManager). Non-blocking: only enqueues unique
        // work; the worker enforces RE_EXTRACT_MIN_COMPLETED_WORKOUTS and does not delay this return.
        responseModelingScheduler.enqueueRefresh()

        completed
    }

    /**
     * Recomputes derived dashboard tables over the entire history (imported + live) so the dashboard
     * never reflects a frozen import snapshot. Two stages, each independently fail-safe:
     *  1. Rollup insights (lift_training_insights, muscle_volume_baselines, …) via
     *     [extractTrainingInsights] with [InsightsAnalysisScope.All].
     *  2. Weekly series (muscle_weekly_stimulus, lift_weekly_performance) via
     *     [refreshWeeklySeries] — the cheap deterministic tables the dashboard charts read.
     * Dose-response / outcome-index modeling is **not** run here — that is scheduled separately
     * via [responseModelingScheduler] (WorkManager worker, 28-day gate).
     * A failure in either stage must never fail the other or session completion — the session is
     * already persisted, so we swallow and log here.
     */
    private suspend fun refreshInsights() {
        val rollupResult = extractTrainingInsights(scope = InsightsAnalysisScope.All)
        if (rollupResult.isFailure) {
            logger.log(
                Level.WARNING,
                "Insights rollup refresh after session finalize failed",
                rollupResult.exceptionOrNull(),
            )
        }
        val weeklyResult = refreshWeeklySeries()
        if (weeklyResult.isFailure) {
            logger.log(
                Level.WARNING,
                "Weekly series refresh after session finalize failed",
                weeklyResult.exceptionOrNull(),
            )
        }
    }
}

/** Default no-op for JVM unit tests that construct finalize without Hilt. */
private object NoOpResponseModelingScheduler : ResponseModelingSchedulerPort {
    override fun enqueueRefresh() = Unit
}
