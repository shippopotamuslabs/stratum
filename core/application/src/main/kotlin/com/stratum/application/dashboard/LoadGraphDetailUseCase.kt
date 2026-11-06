package com.stratum.application.dashboard

import com.stratum.application.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.application.response.LiftWeeklyPerformanceRepository
import com.stratum.application.response.MuscleWeeklyOutcomeIndexRepository
import com.stratum.application.response.MuscleWeeklyStimulusRepository
import com.stratum.domain.response.ResponseExtractionInput
import com.stratum.domain.response.TrainingSignalCleaner
import com.stratum.domain.response.modeling.MuscleDailyVolumeBuilder
import com.stratum.application.training.TrainingLogRepository
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import com.stratum.domain.dashboard.GraphDetailKind
import com.stratum.domain.dashboard.GraphDetailSeries
import com.stratum.domain.dashboard.GraphDetailSubject
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.dashboard.windowStart
import com.stratum.domain.lift.Lift

/**
 * Outcome of [LoadGraphDetailUseCase] (STR-243).
 *
 * An operational load failure rides the [Failed] branch as an
 * [OperationalDiagnostic] and is drained exactly once at the presentation
 * boundary; it never reaches UI state. Coroutine cancellation is not a
 * branch: it propagates.
 */
sealed interface LoadGraphDetailResult {
    data class Loaded(val series: GraphDetailSeries) : LoadGraphDetailResult

    /** The subject has no points. */
    data object Empty : LoadGraphDetailResult

    /** The graph detail load failed (repository / store I/O). */
    data class Failed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : LoadGraphDetailResult
}

/**
 * Loads full history for one lift, weekly muscle series, or daily muscle volume for the fullscreen
 * graph detail.
 *
 * Unlike [LoadDashboardUseCase] (board window ~13 weeks), this reads **all** rows for the subject
 * so time-range chips (1Y / All) reflect real history. [LoadGraphDetailResult.Empty] means the
 * subject has no points. Unexpected failures are returned as
 * [LoadGraphDetailResult.Failed] with their diagnostics on the branch.
 */
class LoadGraphDetailUseCase @Inject constructor(
    private val liftWeeklyPerformanceRepository: LiftWeeklyPerformanceRepository,
    private val muscleWeeklyStimulusRepository: MuscleWeeklyStimulusRepository,
    private val muscleWeeklyOutcomeIndexRepository: MuscleWeeklyOutcomeIndexRepository,
    private val liftRepository: LiftRepository,
    private val trainingLogRepository: TrainingLogRepository,
) {
    /**
     * @param zone Calendar zone for daily volume bucketing. Board and detail must pass the same
     * zone used for presentation (ViewModel captures once).
     */
    suspend operator fun invoke(
        subject: GraphDetailSubject,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LoadGraphDetailResult =
        try {
            val series =
                when (subject) {
                    is GraphDetailSubject.Lift -> loadLift(subject.liftId)
                    is GraphDetailSubject.Muscle -> loadMuscle(subject.muscle)
                    is GraphDetailSubject.MuscleDailyVolume ->
                        loadMuscleDailyVolume(subject.muscle, zone)
                }
            when (series) {
                null -> LoadGraphDetailResult.Empty
                else -> LoadGraphDetailResult.Loaded(series)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            LoadGraphDetailResult.Failed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to load graph detail: ${error.message}",
                        cause = error,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "LoadGraphDetail"
    }

    private suspend fun loadLift(liftId: LiftId): GraphDetailSeries? {
        val rows = liftWeeklyPerformanceRepository.getAll()
            .filter { it.liftId == liftId }
            .sortedBy { it.weekStart }
        if (rows.isEmpty()) return null

        val liftName = liftRepository.observeLifts().first()
            .firstOrNull { it.id == liftId }
            ?.name
            ?: liftId.value

        return GraphDetailSeries(
            title = liftName,
            kind = GraphDetailKind.Lift,
            primary = rows.map { TrendPoint(it.weekStart, it.peakE1rm) },
            secondary = rows.map { TrendPoint(it.weekStart, it.hardSets) },
        )
    }

    private suspend fun loadMuscle(muscle: MuscleGroup): GraphDetailSeries? {
        val volume = muscleWeeklyStimulusRepository.getAll()
            .filter { it.muscle == muscle }
            .sortedBy { it.weekStart }
            .map { TrendPoint(it.weekStart, it.hardSets) }
        val progress = muscleWeeklyOutcomeIndexRepository.getAll()
            .filter { it.muscle == muscle }
            .sortedBy { it.weekStart }
            .map { TrendPoint(it.weekStart, it.indexValue) }

        if (volume.isEmpty() && progress.isEmpty()) return null

        return GraphDetailSeries(
            title = muscle.displayLabel,
            kind = GraphDetailKind.Muscle,
            primary = progress,
            secondary = volume,
        )
    }

    private suspend fun loadMuscleDailyVolume(
        muscle: MuscleGroup,
        zone: ZoneId,
    ): GraphDetailSeries? {
        // SQL join lifts ↔ logged_sets filtered by primary/secondary muscle bitmasks (all-time).
        // Not: load every set then filter by muscle in Kotlin.
        val sets = trainingLogRepository.getSetsCreditingMuscle(muscle)
        val liftIds = sets.mapNotNullTo(mutableSetOf()) { it.liftId }
        if (sets.isEmpty() || liftIds.isEmpty()) return null

        // Only catalog rows for lifts that appeared in those sets.
        val lifts = liftRepository.getLiftsByIds(liftIds)
        if (lifts.isEmpty()) return null

        val windowStart = sets.minOf { it.completedAt }
        val windowEnd = sets.maxOf { it.completedAt }
        val input = ResponseExtractionInput(
            sessions = emptyList(),
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
        val cleanSets = TrainingSignalCleaner.clean(input)
        val series = MuscleDailyVolumeBuilder.build(cleanSets, lifts, zone)
            .firstOrNull { it.muscle == muscle }
            ?.takeIf { it.points.isNotEmpty() }
            ?: return null

        return GraphDetailSeries(
            title = muscle.displayLabel,
            kind = GraphDetailKind.MuscleDailyVolume,
            primary = series.points.map { TrendPoint(weekStart = it.dayStart, value = it.hardSets) },
            secondary = emptyList(),
        )
    }
}
