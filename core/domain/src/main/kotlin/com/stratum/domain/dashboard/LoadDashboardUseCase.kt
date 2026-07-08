package com.stratum.domain.dashboard

import com.stratum.domain.importing.CustomLiftMusclePolicy
import com.stratum.domain.importing.ImportStubLiftGuesser
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.response.MuscleVolumeCredit
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import com.stratum.domain.response.LiftWeeklyPerformanceRepository
import com.stratum.domain.response.MuscleWeeklyStimulusRepository
import com.stratum.domain.training.TrainingLogRepository
import java.time.Duration
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Loads the windowed dashboard read model from the deterministic weekly tables. Reads a ~3-month
 * window ([WINDOW_WEEKS]); the tables are kept fresh by
 * [com.stratum.domain.training.RefreshWeeklySeriesUseCase] on workout completion, so this is a pure
 * read with no recompute.
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

        val liftsById = liftRepository.observeLifts().first().associateBy { it.id }
        val firstSetPerLift = trainingLogRepository.getFirstSetDatePerLift()
        val programLiftIds = activeProgramLiftIds()

        val muscles = buildMuscleSeries(stimulus, performance, liftsById)
        val lifts = buildLiftSeries(performance, liftsById, firstSetPerLift, now, programLiftIds)

        return DashboardSummary(
            windowWeeks = WINDOW_WEEKS,
            asOf = now,
            muscles = muscles,
            lifts = lifts,
        )
    }

    private fun buildMuscleSeries(
        stimulus: List<MuscleWeeklyStimulus>,
        performance: List<LiftWeeklyPerformance>,
        liftsById: Map<LiftId, Lift>,
    ): List<MuscleSeries> =
        stimulus.groupBy { it.muscle }.map { (muscle, rows) ->
            val volumeSeries = rows
                .sortedBy { it.weekStart }
                .map { TrendPoint(it.weekStart, it.hardSets) }
            val meanWeekly = rows.map { it.hardSets }.average().orZero()

            val contributing = contributingPerformances(muscle, performance, liftsById)
            val volumeShares = muscleVolumeShares(muscle, contributing, liftsById)
            val progressSeries = MuscleProgressIndex.compute(muscle, contributing, volumeShares)
            val progressSlope = progressSeries?.let { olsSlope(it) }

            MuscleSeries(
                muscle = muscle,
                displayName = muscle.displayName(),
                volumeSeries = volumeSeries,
                progressSeries = progressSeries,
                progressSlope = progressSlope,
                meanWeeklyHardSets = meanWeekly,
                contributingLiftCount = volumeShares.size,
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
                LiftSeries(
                    liftId = liftId,
                    name = lift?.name ?: liftId.value,
                    performanceSeries = performanceSeries,
                    volumeSeries = volumeSeries,
                    latestE1rm = latestE1rm,
                    tenureWeeks = tenureWeeks,
                    sessionCount = sessionCount,
                )
            }.sortedWith(liftComparator())

    /**
     * The [LiftWeeklyPerformance] rows for lifts that credit [muscle] (primary 1.0 / secondary 0.5),
     * resolved through the lift catalog so the same crediting policy that built the stimulus table
     * applies here.
     */
    private fun contributingPerformances(
        muscle: MuscleGroup,
        performance: List<LiftWeeklyPerformance>,
        liftsById: Map<LiftId, Lift>,
    ): List<LiftWeeklyPerformance> =
        performance.filter { row ->
            val lift = liftsById[row.liftId] ?: return@filter false
            muscle in creditedMuscles(lift)
        }

    /**
     * Each contributing lift's fractional share of the muscle's total in-window hard-set volume,
     * weighted by its credit (1.0 primary / 0.5 secondary).
     */
    private fun muscleVolumeShares(
        muscle: MuscleGroup,
        contributing: List<LiftWeeklyPerformance>,
        liftsById: Map<LiftId, Lift>,
    ): Map<LiftId, Double> {
        val totals = contributing.groupBy { it.liftId }.mapValues { (liftId, rows) ->
            val lift = liftsById[liftId] ?: return@mapValues 0.0
            val credit = if (muscle in lift.primaryMuscles) {
                MuscleVolumeCredit.PRIMARY_SET_CREDIT
            } else {
                MuscleVolumeCredit.SECONDARY_SET_CREDIT
            }
            rows.sumOf { it.hardSets } * credit
        }
        val sum = totals.values.sum()
        if (sum <= 0.0) return emptyMap()
        return totals.mapValues { it.value / sum }
    }

    private fun creditedMuscles(lift: Lift): Set<MuscleGroup> {
        val movementPattern = ImportStubLiftGuesser.effectiveMovementPattern(lift)
        val primary = ImportStubLiftGuesser.effectivePrimaryMuscles(lift, movementPattern)
        val secondary = CustomLiftMusclePolicy.effectiveSecondaryMuscles(
            movementPattern = movementPattern,
            primaryMuscles = primary,
            secondaryMuscles = lift.secondaryMuscles,
        )
        return primary + (secondary - primary)
    }

    private suspend fun activeProgramLiftIds(): Set<LiftId> =
        programRepository.getProgram()?.workouts
            ?.flatMap { it.workoutLifts }
            ?.map { it.liftId }
            ?.toSet()
            ?: emptySet()

    private fun weeksBetween(start: Instant, end: Instant): Int =
        (Duration.between(start, end).toDays() / 7).toInt().coerceAtLeast(0)

    private fun muscleComparator(): Comparator<MuscleSeries> =
        compareBy<MuscleSeries> { it.progressSlope?.let { s -> if (s < 0) 0 else 1 } ?: 1 }
            .thenByDescending { it.meanWeeklyHardSets }

    private fun liftComparator(): Comparator<LiftSeries> = Comparator { a, b ->
        val slopeA = olsSlope(a.performanceSeries)
        val slopeB = olsSlope(b.performanceSeries)
        val prioA = if (slopeA != null && slopeA < 0) 0 else 1
        val prioB = if (slopeB != null && slopeB < 0) 0 else 1
        prioA.compareTo(prioB).let { if (it != 0) it else b.sessionCount.compareTo(a.sessionCount) }
    }

    private fun Double?.orZero(): Double = this ?: 0.0

    private fun MuscleGroup.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

    private companion object {
        val WINDOW: Period = Period.ofWeeks(WINDOW_WEEKS)
        const val WINDOW_WEEKS = 13
    }
}
