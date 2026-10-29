package com.stratum.application.dashboard

import com.stratum.domain.importing.ImportStubLiftGuesser
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LOWER_BODY_MUSCLE_GROUPS
import com.stratum.domain.lift.Lift
import com.stratum.application.lift.LiftRepository
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.application.program.ProgramRepository
import com.stratum.application.response.LiftWeeklyPerformanceRepository
import com.stratum.application.response.MuscleWeeklyOutcomeIndexRepository
import com.stratum.application.response.MuscleWeeklyStimulusRepository
import com.stratum.domain.response.ResponseExtractionInput
import com.stratum.domain.response.TrainingSignalCleaner
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleDailyVolumeBuilder
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import com.stratum.application.training.TrainingLogRepository
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import com.stratum.domain.dashboard.DashboardSummary
import com.stratum.domain.dashboard.LiftSeries
import com.stratum.domain.dashboard.MuscleDailyVolumeSeries
import com.stratum.domain.dashboard.MuscleSeries
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.dashboard.olsSlope
import com.stratum.domain.dashboard.selectDashboardCallouts
import com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase
import com.stratum.application.training.RefreshWeeklySeriesUseCase
import com.stratum.domain.dashboard.DASHBOARD_PERFORMANCE_WINDOW_WEEKS
import com.stratum.domain.dashboard.windowStart
import com.stratum.domain.response.RE_EXTRACT_MIN_COMPLETED_WORKOUTS

/**
 * Loads the windowed dashboard read model from the deterministic weekly tables. Reads a ~3-month
 * window ([DASHBOARD_PERFORMANCE_WINDOW_WEEKS]).
 *
 * Freshness:
 * - Volume / lift e1RM series: [com.stratum.application.training.RefreshWeeklySeriesUseCase] on finalize
 *   and after a successful cloud pull via
 *   [com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase] (tables are not synced)
 * - Muscle progress (outcome index): full extract via the response-modeling WorkManager worker
 *   ([com.stratum.domain.response.RE_EXTRACT_MIN_COMPLETED_WORKOUTS] completed-workout cadence;
 *   also enqueued post-pull)
 *
 * Returns [Result.success] with the summary, or `null` data when the board is empty.
 * Unexpected failures are logged once and returned as [Result.failure] so the UI can show
 * retry chrome instead of an empty board.
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
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): Result<DashboardSummary?> =
        invoke(now = Instant.now(), zone = ZoneId.systemDefault())

    /**
     * [zone] drives the 7-Day rolling window (device local calendar days). Weekly series remain
     * UTC-week based via the stimulus/performance tables.
     */
    suspend operator fun invoke(
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result<DashboardSummary?> =
        runCatchingLogged(loggingPort, TAG, "Failed to load dashboard") {
            load(now, zone)
        }

    private suspend fun load(now: Instant, zone: ZoneId): DashboardSummary? {
        val windowStart = now.minus(WINDOW)
        val stimulus = muscleWeeklyStimulusRepository.getSince(windowStart)
        val performance = liftWeeklyPerformanceRepository.getSince(windowStart)
        // 7-Day volume is independent of weekly tables — build it even when stimulus/performance
        // are empty so recent hard sets still surface before weekly rebuild.
        val runningDailyVolume = buildRunningDailyVolume(now, zone)
        if (stimulus.isEmpty() && performance.isEmpty() && runningDailyVolume.isEmpty()) {
            return null
        }

        val outcomeIndices = muscleWeeklyOutcomeIndexRepository.getSince(windowStart)
        val lifts = liftRepository.observeLifts().first()
        val liftsById = lifts.associateBy { it.id }
        val firstSetPerLift = trainingLogRepository.getFirstSetDatePerLift()
        val programLiftIds = activeProgramLiftIds()

        val muscles = buildMuscleSeries(stimulus, outcomeIndices, liftsById)
        val liftSeries = buildLiftSeries(performance, liftsById, firstSetPerLift, now, programLiftIds)
        val callouts = selectDashboardCallouts(muscles = muscles, lifts = liftSeries)

        return DashboardSummary(
            windowWeeks = DASHBOARD_PERFORMANCE_WINDOW_WEEKS,
            asOf = now,
            zone = zone,
            muscles = muscles,
            lifts = liftSeries,
            runningDailyVolume = runningDailyVolume,
            callouts = callouts,
        )
    }

    /**
     * Hard-set volume per muscle for the last 7 **local** calendar days (zero-filled). Built
     * on-read from logged sets so no daily Room table is required.
     *
     * Sets are loaded via indexed [TrainingLogRepository.getSetsInWindow]; lifts are only those
     * referenced by those sets ([LiftRepository.getLiftsByIds]) — not the full catalog.
     * Window bounds use [zone] midnights so the board does not flip at UTC midnight.
     */
    private suspend fun buildRunningDailyVolume(
        now: Instant,
        zone: ZoneId,
    ): List<MuscleDailyVolumeSeries> {
        val today = now.atZone(zone).toLocalDate()
        val startDate = today.minusDays(
            (MuscleDailyVolumeBuilder.RUNNING_VOLUME_DAY_COUNT - 1).toLong(),
        )
        val startDay = startDate.atStartOfDay(zone).toInstant()
        // Inclusive end of today's local day (instant just before local midnight tomorrow).
        val windowEndInclusive = today.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
        val sets = trainingLogRepository.getSetsInWindow(startDay, windowEndInclusive)
        if (sets.isEmpty()) return emptyList()

        val liftIds = sets.mapNotNullTo(mutableSetOf()) { it.liftId }
        if (liftIds.isEmpty()) return emptyList()
        val lifts = liftRepository.getLiftsByIds(liftIds)
        if (lifts.isEmpty()) return emptyList()

        val input = ResponseExtractionInput(
            sessions = emptyList(),
            sets = sets,
            lifts = lifts,
            provenLiftIds = emptySet(),
            specializeMuscles = emptySet(),
            liftInsights = emptyList(),
            volumeBaselines = emptyList(),
            analysisWindowStart = startDay,
            analysisWindowEnd = now,
            activeExperiments = emptyList(),
        )
        val cleanSets = TrainingSignalCleaner.clean(input)
        val built = MuscleDailyVolumeBuilder.build(cleanSets, lifts, zone)
        if (built.isEmpty()) return emptyList()

        return built.map { series ->
            val filled = MuscleDailyVolumeBuilder.zeroFillLastDays(series, now, zone = zone)
            val total = filled.sumOf { it.hardSets }
            MuscleDailyVolumeSeries(
                muscle = series.muscle,
                displayName = series.muscle.displayLabel,
                dailyVolume = filled.map { TrendPoint(weekStart = it.dayStart, value = it.hardSets) },
                totalHardSets = total,
                contributingLiftCount = series.contributingLiftIds.size,
            )
        }
            .filter { it.totalHardSets > 0.0 }
            .sortedWith(
                compareByDescending<MuscleDailyVolumeSeries> { it.totalHardSets }
                    .thenBy { it.displayName },
            )
    }

    private fun buildMuscleSeries(
        stimulus: List<MuscleWeeklyStimulus>,
        outcomeIndices: List<MuscleWeeklyOutcomeIndex>,
        liftsById: Map<LiftId, Lift>,
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
            // Footer "N lifts" = distinct primary volume contributors over the whole dashboard
            // window (union), not max concurrent outcome-index contributors in a single week.
            // Outcome index is still used for progress bars; its per-week count systematically
            // undercounts when primary lifts rotate across sessions/weeks.
            val contributingLiftCount = countPrimaryContributingLifts(muscle, rows, liftsById)
                .takeIf { it > 0 }
                ?: muscleIndices.maxOfOrNull { it.contributingLiftCount }
                ?: 0

            MuscleSeries(
                muscle = muscle,
                displayName = muscle.displayLabel,
                volumeSeries = volumeSeries,
                progressSeries = progressSeries,
                progressSlope = progressSlope,
                meanWeeklyHardSets = meanWeekly,
                contributingLiftCount = contributingLiftCount,
            )
        }.sortedWith(muscleComparator())

    /**
     * Unique lifts that credited [muscle] as a **primary** mover in any in-window stimulus week.
     * Parsed from [MuscleWeeklyStimulus.compositionHash] (`liftId:creditTenths|…`) and filtered
     * with the same effective-primary rules as volume builders.
     */
    private fun countPrimaryContributingLifts(
        muscle: MuscleGroup,
        stimulusRows: List<MuscleWeeklyStimulus>,
        liftsById: Map<LiftId, Lift>,
    ): Int {
        val liftIds = stimulusRows
            .flatMap { parseCompositionLiftIds(it.compositionHash) }
            .toSet()
        if (liftIds.isEmpty()) return 0
        return liftIds.count { liftId ->
            val lift = liftsById[liftId] ?: return@count false
            val movementPattern = ImportStubLiftGuesser.effectiveMovementPattern(lift)
            val primaryMuscles =
                ImportStubLiftGuesser.effectivePrimaryMuscles(lift, movementPattern)
            muscle in primaryMuscles
        }
    }

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
                val fatigueProfile = lift?.fatigueProfile ?: FatigueProfile.Low
                LiftSeries(
                    liftId = liftId,
                    name = lift?.name ?: liftId.value,
                    performanceSeries = performanceSeries,
                    volumeSeries = volumeSeries,
                    latestE1rm = latestE1rm,
                    tenureWeeks = tenureWeeks,
                    sessionCount = sessionCount,
                    isLower = isLower,
                    fatigueProfile = fatigueProfile,
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

    /** Upper first, then Lower; within section High → Medium → Low fatigue; name as stable tie-break. */
    private fun liftComparator(): Comparator<LiftSeries> =
        compareBy<LiftSeries> { it.isLower }
            .thenBy { it.fatigueProfile.ordinal }
            .thenBy { it.name }

    private fun Double?.orZero(): Double = this ?: 0.0

    private companion object {
        const val TAG = "LoadDashboard"
        val WINDOW: Period = Period.ofWeeks(DASHBOARD_PERFORMANCE_WINDOW_WEEKS)
        /** Minimum weekly outcome-index points before a progress series is shown. */
        const val MIN_PROGRESS_POINTS = 3
    }
}

/**
 * Parses lift ids from a stimulus/outcome composition hash (`liftId:creditTenths|…`).
 * Tokens without a non-blank id before `:` are skipped.
 */
internal fun parseCompositionLiftIds(compositionHash: String): List<LiftId> {
    if (compositionHash.isBlank()) return emptyList()
    return compositionHash.split('|').mapNotNull { token ->
        val rawId = token.substringBefore(':').trim()
        if (rawId.isEmpty()) return@mapNotNull null
        LiftId(rawId)
    }
}
