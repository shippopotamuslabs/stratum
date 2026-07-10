package com.stratum.domain.checkin

import com.stratum.domain.dashboard.DASHBOARD_PERFORMANCE_WINDOW_WEEKS
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.RankSwapCandidatesUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.response.LiftWeeklyPerformanceRepository
import com.stratum.domain.training.ProgramChangeEventRepository
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Builds a full [CheckInEvaluation] from weekly performance tables, the active program, lift
 * rotation tenure, swap ranking, and ephemeral subjective feedback.
 *
 * Policy lives in [CheckInDecisionEngine.decide]; this use case only enriches Swap with catalog
 * candidates (failing closed to +volume when none), reserving chosen lifts across the batch.
 */
class EvaluateCheckInUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val liftRepository: LiftRepository,
    private val liftWeeklyPerformanceRepository: LiftWeeklyPerformanceRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val rankSwapCandidates: RankSwapCandidatesUseCase,
) {
    suspend operator fun invoke(
        subjective: CheckInSubjectiveFeedback,
        now: Instant = Instant.now(),
    ): CheckInEvaluation? {
        val program = programRepository.getProgram() ?: return null
        val liftsById = liftRepository.observeLifts().first().associateBy { it.id }
        val windowStart = now.minus(Period.ofWeeks(DASHBOARD_PERFORMANCE_WINDOW_WEEKS))
        val performance = liftWeeklyPerformanceRepository.getSince(windowStart)
        val performanceByLift = performance.groupBy { it.liftId }
        val events = programChangeEventRepository.getEventsForProgram(program.id)

        val programLiftIds =
            program.workouts.flatMap { workout -> workout.workoutLifts.map { it.liftId } }.toSet()

        val slots = mutableListOf<Slot>()
        for (workout in program.workouts) {
            val liftIdsInWorkout = workout.workoutLifts.map { it.liftId }.toSet()
            for (workoutLift in workout.workoutLifts) {
                val lift = liftsById[workoutLift.liftId]
                val weeklyRows =
                    performanceByLift[workoutLift.liftId].orEmpty().sortedBy { it.weekStart }
                val series = weeklyRows.map { TrendPoint(it.weekStart, it.peakE1rm) }
                val sessionCount = weeklyRows.sumOf { it.sessions }
                val trend =
                    CheckInDecisionEngine.classifyPerformanceTrend(
                        performanceSeries = series,
                        sessionCount = sessionCount,
                    )
                val primaryMuscles = lift?.primaryMuscles.orEmpty()
                val recovery =
                    CheckInDecisionEngine.resolveRecoverySignal(
                        overall = subjective.overallRecovery,
                        muscleSignals = subjective.muscleSignals,
                        primaryMuscles = primaryMuscles,
                    )
                val tenure =
                    liftRotationTenureWeeks(
                        workoutLiftId = workoutLift.id,
                        currentLiftId = workoutLift.liftId,
                        programCreatedAt = program.createdAt,
                        events = events,
                        now = now,
                    )
                val input =
                    LiftCheckInInput(
                        workoutLiftId = workoutLift.id,
                        liftId = workoutLift.liftId,
                        liftName = lift?.name ?: workoutLift.liftId.value,
                        primaryMuscles = primaryMuscles,
                        performanceTrend = trend,
                        recoverySignal = recovery,
                        rotationTenureWeeks = tenure,
                        currentSetCount = workoutLift.sets.size,
                    )
                slots +=
                    Slot(
                        input = input,
                        liftIdsInWorkout = liftIdsInWorkout,
                        decision = CheckInDecisionEngine.decide(input),
                    )
            }
        }

        val reservedSwapIds = mutableSetOf<LiftId>()
        val recommendations =
            slots
                .sortedWith(
                    compareBy<Slot> { it.decision.kind.ordinalPriority() }
                        .thenByDescending { it.input.performanceTrend == PerformanceTrend.Dropping }
                        .thenBy { it.input.liftName.lowercase() },
                )
                .map { slot ->
                    resolveRecommendation(
                        slot = slot,
                        programLiftIds = programLiftIds,
                        reservedSwapIds = reservedSwapIds,
                    )
                }

        return CheckInEvaluation(
            evaluatedAt = now,
            recommendations = recommendations,
            programLiftIds = programLiftIds,
        )
    }

    private suspend fun resolveRecommendation(
        slot: Slot,
        programLiftIds: Set<LiftId>,
        reservedSwapIds: MutableSet<LiftId>,
    ): CheckInRecommendation {
        val input = slot.input
        val decision = slot.decision
        val (action, rationale) =
            when (decision.kind) {
                CheckInActionKind.Maintain ->
                    CheckInAction.Maintain to decision.rationale
                CheckInActionKind.IncreaseVolume ->
                    CheckInAction.IncreaseVolume(addSets = decision.addSets.coerceAtLeast(1)) to
                        decision.rationale
                CheckInActionKind.CutVolume ->
                    CheckInAction.CutVolume(removeSets = decision.removeSets) to decision.rationale
                CheckInActionKind.SwapLift -> {
                    val exclude =
                        slot.liftIdsInWorkout + programLiftIds + reservedSwapIds - input.liftId
                    val pick = pickSwapCandidate(input.liftId, exclude)
                    if (pick != null) {
                        reservedSwapIds += pick.first
                        CheckInAction.SwapLift(
                            suggestedLiftId = pick.first,
                            suggestedLiftName = pick.second,
                        ) to decision.rationale
                    } else {
                        val soft =
                            (decision.rationale as? CheckInRationale.SoftStaleSwap)?.softTrend
                                ?: SoftPerformanceTrend.Flat
                        CheckInAction.IncreaseVolume(addSets = 1) to
                            CheckInRationale.SoftStaleIncreaseNoCandidate(
                                softTrend = soft,
                                tenureWeeks = input.rotationTenureWeeks,
                            )
                    }
                }
            }
        return CheckInRecommendation(
            workoutLiftId = input.workoutLiftId,
            liftId = input.liftId,
            liftName = input.liftName,
            primaryMuscles = input.primaryMuscles,
            performanceTrend = input.performanceTrend,
            recoverySignal = input.recoverySignal,
            rotationTenureWeeks = input.rotationTenureWeeks,
            currentSetCount = input.currentSetCount,
            action = action,
            rationale = rationale,
        )
    }

    /**
     * Picks the best enabled catalog match not in [excludeLiftIds]. Returns null when none —
     * never falls back to an excluded lift.
     */
    private suspend fun pickSwapCandidate(
        targetLiftId: LiftId,
        excludeLiftIds: Set<LiftId>,
    ): Pair<LiftId, String>? {
        val ranked = rankSwapCandidates(targetLiftId).first()
        val pick = ranked.firstOrNull { scored -> scored.lift.id !in excludeLiftIds }
        return pick?.let { it.lift.id to it.lift.name }
    }

    private fun CheckInActionKind.ordinalPriority(): Int =
        when (this) {
            CheckInActionKind.CutVolume -> 0
            CheckInActionKind.SwapLift -> 1
            CheckInActionKind.IncreaseVolume -> 2
            CheckInActionKind.Maintain -> 3
        }

    private data class Slot(
        val input: LiftCheckInInput,
        val liftIdsInWorkout: Set<LiftId>,
        val decision: CheckInCoreDecision,
    )
}
