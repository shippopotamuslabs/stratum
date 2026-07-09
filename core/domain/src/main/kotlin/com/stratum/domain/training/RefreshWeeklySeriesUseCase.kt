package com.stratum.domain.training

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.response.ResponseExtractionInput
import com.stratum.domain.response.TrainingSignalCleaner
import com.stratum.domain.response.WeeklySignalSupport
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.modeling.LiftPerformancePanelBuilder
import com.stratum.domain.response.modeling.MuscleWeeklyStimulusBuilder
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.EpochSplitReason
import com.stratum.domain.response.ExtractedResponseProfile
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.ResponseProfilePersistencePort
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Recomputes the two cheap deterministic weekly tables the dashboard charts read —
 * `muscle_weekly_stimulus` (per-muscle/week hard-set volume) and `lift_weekly_performance`
 * (per-lift/week peak e1RM) — over **all** history (imported + live), and replaces only those two
 * tables in one transaction. Dose-response / outcome-index modeling is intentionally **not** run
 * here: that heavier pipeline runs on the response-modeling WorkManager worker
 * (`ResponseModelingWorker`, [com.stratum.domain.response.RE_EXTRACT_MIN_COMPLETED_WORKOUTS] gate).
 *
 * Triggered fire-and-forget by [FinalizeTrainingSessionUseCase] on workout completion so the
 * dashboard tables never go stale relative to live workouts. Reads raw [LoggedSet]s (the source of
 * truth) and applies the canonical hard-set predicate (`TrainingSignalCleaner`: RIR < 4 or
 * relative load ≥ 0.65), unifying the volume definition the dashboard sees.
 */
class RefreshWeeklySeriesUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftRepository: LiftRepository,
    private val persistence: ResponseProfilePersistencePort,
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val sessions = trainingLogRepository.getAllSessions()
        val sets = trainingLogRepository.getAllSets()
        if (sets.isEmpty()) return@runCatching

        val lifts = liftRepository.observeLifts().first()
        val windowStart = sessions.minOfOrNull { it.startedAt } ?: Instant.EPOCH
        val windowEnd = sessions.maxOfOrNull { it.startedAt } ?: Instant.now()

        val input = weeklyInput(sessions, sets, lifts, windowStart, windowEnd)
        val cleanSets = TrainingSignalCleaner.clean(input)
        val epoch = fullWindowEpoch(windowStart, windowEnd)

        val stimulus = MuscleWeeklyStimulusBuilder.build(epoch, cleanSets, lifts)
        val performance = LiftPerformancePanelBuilder.build(epoch, cleanSets)

        persistence.replaceWeeklySeries(
            ExtractedResponseProfile(
                epochs = emptyList(),
                volumeResponses = emptyList(),
                liftEffectiveness = emptyList(),
                experimentBlocks = emptyList(),
                metadata =
                    ResponseProfileMetadata(
                        epochsAnalyzed = 0,
                        analysisWindowStart = windowStart,
                        analysisWindowEnd = windowEnd,
                        lastExtractedAt = Instant.now(),
                    ),
                weeklyStimulus = stimulus,
                liftPerformance = performance,
            ),
        )
    }

    private fun weeklyInput(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
        lifts: List<com.stratum.domain.lift.Lift>,
        windowStart: Instant,
        windowEnd: Instant,
    ): ResponseExtractionInput =
        ResponseExtractionInput(
            sessions = sessions,
            sets = sets,
            lifts = lifts,
            provenLiftIds = emptySet(),
            specializeMuscles = emptySet(),
            liftInsights = emptyList(),
            volumeBaselines = emptyList(),
            analysisWindowStart = windowStart,
            analysisWindowEnd = windowEnd,
            activeExperiments = emptyList(),
        )

    private fun fullWindowEpoch(windowStart: Instant, windowEnd: Instant): Epoch =
        Epoch(
            id = EpochId("weekly-refresh"),
            start = WeeklySignalSupport.weekStartFor(windowStart),
            end = windowEnd,
            anchorLiftIds = emptySet(),
            splitReason = EpochSplitReason.WindowEnd,
        )
}
