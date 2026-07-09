package com.stratum.domain.dashboard

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.LOWER_BODY_MUSCLE_GROUPS
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.response.LiftWeeklyPerformanceRepository
import com.stratum.domain.response.MuscleWeeklyOutcomeIndexRepository
import com.stratum.domain.response.MuscleWeeklyStimulusRepository
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import com.stratum.domain.training.TrainingLogRepository
import java.time.Duration
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Loads the windowed dashboard read model from the deterministic weekly tables. Reads a ~3-month
 * window ([WINDOW_WEEKS]).
 *
 * Freshness:
 * - Volume / lift e1RM series: [com.stratum.domain.training.RefreshWeeklySeriesUseCase] on finalize
 * - Muscle progress (outcome index): full extract via the response-modeling WorkManager worker
 *   ([com.stratum.domain.response.RE_EXTRACT_MIN_COMPLETED_WORKOUTS] completed-workout cadence)
 *
 * Returns `null` when no weekly data exists in the window (no completed workouts + no import) so the
 * UI renders an empty state rather than an empty-as-success board.
 *
 * Lifts tab: only lifts present in the active program (with in-window performance). Muscles tab: all
 * muscles with in-window stimulus, rolled up across every contributing lift — a muscle can be
 * trained without being a program slot.
 */
class LoadDashboardUseCase @Inject constructor(
    private val muscleWeeklyStimulusRepository: MuscleWeeklyStimulusRepository,
    private val liftWeeklyPerformanceRepository: LiftWeeklyPerformanceRepository,
    private val muscleWeeklyOutcomeIndexRepository: MuscleWeeklyOutcomeIndexRepository,
    private val liftRepository: LiftRepository,
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(): DashboardSummary? = invoke(now = Instant.now())

    suspend operator fun invoke(now: Instant): DashboardSummary? {
        val windowStart = now.minus(WINDOW)
        val stimulus = muscleWeeklyStimulusRepository.getSince(windowStart)
        val performance = liftWeeklyPerformanceRepository.getSince(windowStart)
        if (stimulus.isEmpty() && performance.isEmpty()) return null

        val outcomeIndices = muscleWeeklyOutcomeIndexRepository.getSince(windowStart)
        val liftsById = liftRepository.observeLifts().first().associateBy { it.id }
        val firstSetPerLift = trainingLogRepository.getFirstSetDatePerLift()
        val programLiftIds = activeProgramLiftIds()

        val muscles = buildMuscleSeries(stimulus, outcomeIndices)
        val lifts = buildLiftSeries(performance, liftsById, firstSetPerLift, now, programLiftIds)
        val callouts = selectDashboardCallouts(muscles = muscles, lifts = lifts)

        return DashboardSummary(
            windowWeeks = WINDOW_WEEKS,
            asOf = now,
            muscles = muscles,
            lifts = lifts,
            callouts = callouts,
        )
    }

    private fun buildMuscleSeries(
        stimulus: List<MuscleWeeklyStimulus>,
        outcomeIndices: List<MuscleWeeklyOutcomeIndex>,
    ): List<MuscleSeries> =
        stimulus.groupBy { it.muscle }.map { (muscle, rows) ->
            val volumeSeries = rows
                .sortedBy { it.weekStart }
                .map { TrendPoint(it.weekStart, it.hardSets) }
            val meanWeekly = rows.map { it.hardSets }.average().orZero()

            val muscleIndices = outcomeIndices
                .filter { it.muscle == muscle }
                .sortedBy { it.weekStart }
            val progressSeries = muscleIndices
                .map { TrendPoint(it.weekStart, it.indexValue) }
                .takeIf { it.size >= MIN_PROGRESS_POINTS }
            val progressSlope = progressSeries?.let { olsSlope(it) }
            val contributingLiftCount = muscleIndices
                .maxOfOrNull { it.contributingLiftCount }
                ?: 0

            MuscleSeries(
                muscle = muscle,
                displayName = muscle.displayName(),
                volumeSeries = volumeSeries,
                progressSeries = progressSeries,
                progressSlope = progressSlope,
                meanWeeklyHardSets = meanWeekly,
                contributingLiftCount = contributingLiftCount,
            )
        }.sortedWith(muscleComparator())

    private fun buildLiftSeries(
        performance: List<LiftWeeklyPerformance>,
        liftsById: Map<LiftId, Lift>,
        firstSetPerLift: Map<LiftId, Instant>,
        now: Instant,
        programLiftIds: Set<LiftId>,
    ): List<LiftSeries> =
        performance
            .groupBy { it.liftId }
            .filterKeys { it in programLiftIds }
            .map { (liftId, rows) ->
                val sorted = rows.sortedBy { it.weekStart }
                val lift = liftsById[liftId]
                val performanceSeries = sorted.map { TrendPoint(it.weekStart, it.peakE1rm) }
                val volumeSeries = sorted.map { TrendPoint(it.weekStart, it.hardSets) }
                val latestE1rm = sorted.lastOrNull()?.peakE1rm?.let { Weight(it, Weight.WeightUnit.Kg) }
                val firstSet = firstSetPerLift[liftId] ?: sorted.firstOrNull()?.weekStart
                val tenureWeeks = firstSet?.let { weeksBetween(it, now) } ?: 0
                val sessionCount = sorted.sumOf { it.sessions }
                val isLower = lift?.primaryMuscles?.any { it in LOWER_BODY_MUSCLE_GROUPS } == true
                val kinematicProfile = lift?.kinematicProfile ?: KinematicProfile.Isolation
                LiftSeries(
                    liftId = liftId,
                    name = lift?.name ?: liftId.value,
                    performanceSeries = performanceSeries,
                    volumeSeries = volumeSeries,
                    latestE1rm = latestE1rm,
                    tenureWeeks = tenureWeeks,
                    sessionCount = sessionCount,
                    isLower = isLower,
                    kinematicProfile = kinematicProfile,
                )
            }.sortedWith(liftComparator())

    private suspend fun activeProgramLiftIds(): Set<LiftId> =
        programRepository.getProgram()?.workouts
            ?.flatMap { it.workoutLifts }
            ?.map { it.liftId }
            ?.toSet()
            ?: emptySet()

    private fun weeksBetween(start: Instant, end: Instant): Int =
        (Duration.between(start, end).toDays() / 7).toInt().coerceAtLeast(0)

    private fun muscleComparator(): Comparator<MuscleSeries> =
        compareByDescending<MuscleSeries> { it.meanWeeklyHardSets }
            .thenBy { it.displayName }

    /** Upper first, then Lower; within section Axial → NonAxial → Isolation; name as stable tie-break. */
    private fun liftComparator(): Comparator<LiftSeries> =
        compareBy<LiftSeries> { it.isLower }
            .thenBy { it.kinematicProfile.ordinal }
            .thenBy { it.name }

    private fun Double?.orZero(): Double = this ?: 0.0

    private fun MuscleGroup.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

    private companion object {
        val WINDOW: Period = Period.ofWeeks(WINDOW_WEEKS)
        const val WINDOW_WEEKS = 13
        /** Minimum weekly outcome-index points before a progress series is shown. */
        const val MIN_PROGRESS_POINTS = 3
    }
}
