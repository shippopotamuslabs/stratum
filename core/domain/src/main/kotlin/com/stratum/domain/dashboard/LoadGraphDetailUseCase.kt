package com.stratum.domain.dashboard

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.LiftWeeklyPerformanceRepository
import com.stratum.domain.response.MuscleWeeklyOutcomeIndexRepository
import com.stratum.domain.response.MuscleWeeklyStimulusRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Loads full weekly history for one lift or muscle for the fullscreen graph detail.
 *
 * Unlike [LoadDashboardUseCase] (board window ~13 weeks), this reads **all** rows for the subject
 * so time-range chips (1Y / All) reflect real history. Returns null when the subject has no
 * primary or secondary points. Unexpected failures are logged and returned as null.
 */
class LoadGraphDetailUseCase @Inject constructor(
    private val liftWeeklyPerformanceRepository: LiftWeeklyPerformanceRepository,
    private val muscleWeeklyStimulusRepository: MuscleWeeklyStimulusRepository,
    private val muscleWeeklyOutcomeIndexRepository: MuscleWeeklyOutcomeIndexRepository,
    private val liftRepository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(subject: GraphDetailSubject): GraphDetailSeries? =
        runCatching {
            when (subject) {
                is GraphDetailSubject.Lift -> loadLift(subject.liftId)
                is GraphDetailSubject.Muscle -> loadMuscle(subject.muscle)
            }
        }.onFailure { error ->
            loggingPort.error(TAG, "Failed to load graph detail: ${error.message}", error)
        }.getOrNull()

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
}
