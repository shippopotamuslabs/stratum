package com.stratum.application.dashboard

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.lift.LiftRepository
import com.stratum.application.program.ProgramRepository
import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.dashboard.MUSCLE_DETAIL_WINDOW_WEEKS
import com.stratum.domain.dashboard.MuscleDetail
import com.stratum.domain.dashboard.MuscleDetailSession
import com.stratum.domain.dashboard.buildMuscleDetail
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.response.ResponseExtractionInput
import com.stratum.domain.response.TrainingSignalCleaner
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of [LoadMuscleDetailUseCase] (STR-331).
 *
 * A load failure rides [Failed] as an [OperationalDiagnostic], drained once at the
 * presentation boundary. Cancellation propagates.
 */
sealed interface LoadMuscleDetailResult {
    data class Loaded(val detail: MuscleDetail) : LoadMuscleDetailResult

    data class Failed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : LoadMuscleDetailResult
}

/**
 * Loads the Dashboard muscle detail pane (STR-331): lifts that train [MuscleGroup] with their
 * credited sets per week and e1RM direction, plus recent sessions with top sets.
 *
 * Reads only sets for lifts that credit the muscle, cleans them with the same working-set rules
 * as the weekly tables, and resolves workout labels for the listed sessions only.
 */
class LoadMuscleDetailUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftRepository: LiftRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(
        muscle: MuscleGroup,
        now: Instant,
    ): LoadMuscleDetailResult =
        try {
            LoadMuscleDetailResult.Loaded(load(muscle, now))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            LoadMuscleDetailResult.Failed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to load muscle detail: ${error.message}",
                        cause = error,
                    ),
                ),
            )
        }

    private suspend fun load(muscle: MuscleGroup, now: Instant): MuscleDetail {
        // Window plus one week of slack; the domain builder trims to calendar weeks.
        val since = now.minus(Duration.ofDays(DAYS_PER_WEEK * (MUSCLE_DETAIL_WINDOW_WEEKS + 1)))
        val sets = trainingLogRepository.getSetsCreditingMuscle(muscle)
            .filter { !it.completedAt.isBefore(since) && !it.completedAt.isAfter(now) }
        val liftIds = sets.mapNotNullTo(mutableSetOf()) { it.liftId }
        if (liftIds.isEmpty()) {
            return MuscleDetail(muscle = muscle, lifts = emptyList(), recentSessions = emptyList())
        }
        val lifts = liftRepository.getLiftsByIds(liftIds)
        val cleanSets = TrainingSignalCleaner.clean(
            ResponseExtractionInput(
                sessions = emptyList(),
                sets = sets,
                lifts = lifts,
                provenLiftIds = emptySet(),
                specializeMuscles = emptySet(),
                liftInsights = emptyList(),
                volumeBaselines = emptyList(),
                analysisWindowStart = since,
                analysisWindowEnd = now,
                activeExperiments = emptyList(),
            ),
        )
        val detail = buildMuscleDetail(muscle = muscle, cleanSets = cleanSets, lifts = lifts, now = now)
        if (detail.recentSessions.isEmpty()) return detail
        val workoutNames = programRepository.getProgram()
            ?.workouts
            ?.associate { workout -> workout.id to workout.name }
            .orEmpty()
        return detail.copy(
            recentSessions = detail.recentSessions.map { session ->
                session.withLabel(workoutNames)
            },
        )
    }

    private suspend fun MuscleDetailSession.withLabel(
        workoutNames: Map<WorkoutId, String>,
    ): MuscleDetailSession {
        val session = trainingLogRepository.getSession(sessionId) ?: return this
        val label = session.workoutNameSnapshot?.takeIf { it.isNotBlank() }
            ?: session.workoutId?.let { workoutNames[it] }
            ?: session.sourceApp?.takeIf { it.isNotBlank() }
        return copy(workoutLabel = label)
    }

    private companion object {
        const val TAG = "LoadMuscleDetail"
        const val DAYS_PER_WEEK = 7L
    }
}
