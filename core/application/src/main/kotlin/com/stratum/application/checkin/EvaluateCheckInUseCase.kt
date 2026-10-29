package com.stratum.application.checkin

import com.stratum.domain.dashboard.DASHBOARD_PERFORMANCE_WINDOW_WEEKS
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.application.lift.LiftRepository
import com.stratum.application.lift.RankSwapCandidatesUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.program.ComputeProgramMuscleVolumeUseCase
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.application.response.LiftWeeklyPerformanceRepository
import com.stratum.application.response.MuscleVolumeResponseSnapshotRepository
import com.stratum.application.training.ProgramChangeEventRepository
import java.time.Instant
import java.time.Period
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import com.stratum.domain.checkin.CheckInAction
import com.stratum.domain.checkin.CheckInActionKind
import com.stratum.domain.checkin.CheckInBatchSelector
import com.stratum.domain.checkin.CheckInCoreDecision
import com.stratum.domain.checkin.CheckInDecisionEngine
import com.stratum.domain.checkin.CheckInEvaluation
import com.stratum.domain.checkin.CheckInProvisionalRecommendation
import com.stratum.domain.checkin.CheckInRanking
import com.stratum.domain.checkin.CheckInRationale
import com.stratum.domain.checkin.CheckInRecommendation
import com.stratum.domain.checkin.CheckInSubjectiveFeedback
import com.stratum.domain.checkin.CheckInVolumeCeiling
import com.stratum.domain.checkin.CycleHardSetVolume
import com.stratum.domain.checkin.LiftCheckInInput
import com.stratum.domain.checkin.SoftPerformanceTrend
import com.stratum.domain.checkin.WeeklyHardSetVolume
import com.stratum.domain.checkin.liftRotationTenureWeeks
import com.stratum.domain.checkin.selectSwapCandidate
import com.stratum.domain.checkin.CHECKIN_VOLUME_STEP
import com.stratum.domain.checkin.sortPriority
import com.stratum.domain.dashboard.windowStart

/**
 * Builds a full [CheckInEvaluation] from weekly performance, program volume, tenure, and
 * subjective feedback.
 *
 * Pipeline:
 * 1. Per-lift [CheckInDecisionEngine.decide]
 * 2. Enrich Swap kinds with catalog candidates (similarity band); fail closed to +vol/Maintain
 * 3. [CheckInBatchSelector.select] on **final** kinds (one non-cut lever per muscle)
 *
 * Step 3 after step 2 ensures a failed swap re-opens the muscle for a peer +1 (G4 rank).
 *
 * **Volume units:** [ComputeProgramMuscleVolumeUseCase] returns **cycle** hard-set credits
 * ([CycleHardSetVolume]). Check-in ceilings (MV/PV/RC) are **weekly**, so planned volume is
 * converted to [WeeklyHardSetVolume] before [CheckInVolumeCeiling]. Programs UI volume cards may
 * still show raw cycle totals.
 */
class EvaluateCheckInUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val liftRepository: LiftRepository,
    private val liftWeeklyPerformanceRepository: LiftWeeklyPerformanceRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val rankSwapCandidates: RankSwapCandidatesUseCase,
    private val computeProgramMuscleVolume: ComputeProgramMuscleVolumeUseCase,
    private val muscleVolumeResponseSnapshotRepository: MuscleVolumeResponseSnapshotRepository,
) {
    suspend operator fun invoke(
        subjective: CheckInSubjectiveFeedback,
        now: Instant = Instant.now(),
    ): CheckInEvaluation? {
        val program = programRepository.getProgram() ?: return null
        val lifts = liftRepository.observeLifts().first()
        val liftsById = lifts.associateBy { it.id }
        val windowStart = now.minus(Period.ofWeeks(DASHBOARD_PERFORMANCE_WINDOW_WEEKS))
        val performance = liftWeeklyPerformanceRepository.getSince(windowStart)
        val performanceByLift = performance.groupBy { it.liftId }
        val events = programChangeEventRepository.getEventsForProgram(program.id)

        val cycleVolume =
            CycleHardSetVolume(
                computeProgramMuscleVolume(program, lifts).associate { it.muscle to it.setCount },
            )
        // Ceilings are weekly landmarks; scale cycle totals by 7/cycleLength (identity at 7).
        val weeklyVolume = WeeklyHardSetVolume.fromCycle(cycleVolume, program.cycleLength)
        val programVolumeByMuscle = weeklyVolume.byMuscle
        val snapshotsByMuscle =
            muscleVolumeResponseSnapshotRepository.getAll().associateBy { it.muscle }

        val programLiftIds =
            program.workouts.flatMap { workout -> workout.workoutLifts.map { it.liftId } }.toSet()

        val decided = mutableListOf<DecidedSlot>()
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
                val atCeiling =
                    CheckInVolumeCeiling.isAtCeiling(
                        primaryMuscles = primaryMuscles,
                        programVolumeByMuscle = programVolumeByMuscle,
                        snapshotsByMuscle = snapshotsByMuscle,
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
                        atVolumeCeiling = atCeiling,
                        fatigueProfile = lift?.fatigueProfile,
                    )
                decided +=
                    DecidedSlot(
                        input = input,
                        liftIdsInWorkout = liftIdsInWorkout,
                        decision = CheckInDecisionEngine.decide(input),
                    )
            }
        }

        val reservedSwapIds = mutableSetOf<LiftId>()
        // Enrich swaps first (reservation order = shared G4 rank with batch contenders).
        val swapEnrichOrder =
            decided
                .filter { it.decision.kind == CheckInActionKind.SwapLift }
                .sortedWith(
                    compareBy<DecidedSlot> { it.decision.kind.sortPriority() }
                        .then { a, b ->
                            CheckInRanking.swapEnrichInputComparator().compare(a.input, b.input)
                        },
                )
        val enrichedByWorkoutLift = mutableMapOf<WorkoutLiftId, CheckInProvisionalRecommendation>()
        for (slot in swapEnrichOrder) {
            enrichedByWorkoutLift[slot.input.workoutLiftId] =
                enrichSwapSlot(slot, programLiftIds, reservedSwapIds)
        }
        for (slot in decided) {
            if (slot.input.workoutLiftId in enrichedByWorkoutLift) continue
            enrichedByWorkoutLift[slot.input.workoutLiftId] = mapNonSwapSlot(slot)
        }

        val provisional = decided.map { enrichedByWorkoutLift.getValue(it.input.workoutLiftId) }
        val selected = CheckInBatchSelector.select(provisional)

        val recommendations =
            selected.map { row ->
                CheckInRecommendation(
                    workoutLiftId = row.input.workoutLiftId,
                    liftId = row.input.liftId,
                    liftName = row.input.liftName,
                    primaryMuscles = row.input.primaryMuscles,
                    performanceTrend = row.input.performanceTrend,
                    recoverySignal = row.input.recoverySignal,
                    rotationTenureWeeks = row.input.rotationTenureWeeks,
                    currentSetCount = row.input.currentSetCount,
                    action = row.action,
                    rationale = row.rationale,
                    fatigueProfile = row.input.fatigueProfile,
                    atVolumeCeiling = row.input.atVolumeCeiling,
                )
            }

        return CheckInEvaluation(
            evaluatedAt = now,
            recommendations =
                recommendations.sortedWith(CheckInDecisionEngine.recommendationComparator()),
            programLiftIds = programLiftIds,
        )
    }

    private suspend fun enrichSwapSlot(
        slot: DecidedSlot,
        programLiftIds: Set<LiftId>,
        reservedSwapIds: MutableSet<LiftId>,
    ): CheckInProvisionalRecommendation {
        val input = slot.input
        val decision = slot.decision
        val exclude = slot.liftIdsInWorkout + programLiftIds + reservedSwapIds - input.liftId
        val pick = pickSwapCandidate(input.liftId, exclude)
        return if (pick != null) {
            reservedSwapIds += pick.first
            CheckInProvisionalRecommendation(
                input = input,
                action =
                    CheckInAction.SwapLift(
                        suggestedLiftId = pick.first,
                        suggestedLiftName = pick.second,
                    ),
                rationale = decision.rationale,
            )
        } else {
            failedSwapFallback(input, decision)
        }
    }

    private fun failedSwapFallback(
        input: LiftCheckInInput,
        decision: CheckInCoreDecision,
    ): CheckInProvisionalRecommendation {
        val soft =
            (decision.rationale as? CheckInRationale.SoftStaleSwap)?.softTrend
                ?: SoftPerformanceTrend.Flat
        return if (input.atVolumeCeiling) {
            CheckInProvisionalRecommendation(
                input = input,
                action = CheckInAction.Maintain,
                rationale = CheckInRationale.SoftStaleNoCandidateAtCeiling,
            )
        } else {
            CheckInProvisionalRecommendation(
                input = input,
                action = CheckInAction.IncreaseVolume(addSets = CHECKIN_VOLUME_STEP),
                rationale =
                    CheckInRationale.SoftStaleIncreaseNoCandidate(
                        softTrend = soft,
                        tenureWeeks = input.rotationTenureWeeks,
                    ),
            )
        }
    }

    private fun mapNonSwapSlot(slot: DecidedSlot): CheckInProvisionalRecommendation {
        val input = slot.input
        val decision = slot.decision
        val (action, rationale) =
            when (decision.kind) {
                CheckInActionKind.Maintain ->
                    CheckInAction.Maintain to decision.rationale
                CheckInActionKind.IncreaseVolume ->
                    CheckInAction.IncreaseVolume(
                        addSets = decision.addSets.coerceAtLeast(CHECKIN_VOLUME_STEP),
                    ) to decision.rationale
                CheckInActionKind.CutVolume ->
                    CheckInAction.CutVolume(removeSets = decision.removeSets) to decision.rationale
                CheckInActionKind.SwapLift ->
                    error("Swap slots must use enrichSwapSlot")
            }
        return CheckInProvisionalRecommendation(
            input = input,
            action = action,
            rationale = rationale,
        )
    }

    private suspend fun pickSwapCandidate(
        targetLiftId: LiftId,
        excludeLiftIds: Set<LiftId>,
    ): Pair<LiftId, String>? {
        val ranked = rankSwapCandidates(targetLiftId).first()
        val pick = selectSwapCandidate(ranked, excludeLiftIds)
        return pick?.let { it.lift.id to it.lift.name }
    }

    private data class DecidedSlot(
        val input: LiftCheckInInput,
        val liftIdsInWorkout: Set<LiftId>,
        val decision: CheckInCoreDecision,
    )
}
