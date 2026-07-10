package com.stratum.domain.workout

import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionEngine
import com.stratum.domain.workout.progression.ProgressionBaseline
import com.stratum.domain.workout.progression.ProgressionEngine
import com.stratum.domain.workout.progression.SchemeTransitionEvent
import com.stratum.domain.workout.progression.SlotProgressionRequest
import com.stratum.domain.workout.progression.buildRecentSessionBlock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Request for `GenerateProgressionSnapshotUseCase`. */
data class SnapshotRequest(
    val programId: ProgramId,
    val workoutId: WorkoutId,
    val lifts: List<WorkoutLift>,
    /**
     * Per-lift [LoadType] from the catalog, used so the progression engine can branch on
     * loading modality (bodyweight short-circuits to 0 load; assisted caps at 0). Absent
     * entries default to [LoadType.Weighted] inside [GenerateProgressionSnapshotUseCase.invoke].
     * Empty by default so existing callers/tests keep working unchanged.
     */
    val loadTypesById: Map<LiftId, LoadType> = emptyMap(),
    /**
     * Per-lift increment bands from the catalog. Empty map / missing keys → engines use
     * scheme defaults (Double 5 lb, Linear config).
     */
    val incrementTiersById: Map<LiftId, List<IncrementTier>> = emptyMap(),
)

/**
 * Computes the progression snapshot once per session from history (D3). Recomputed
 * only on exercise swap. Dispatches to the matching progression engine per lift.
 *
 * Per lift, builds a capacity-ordered recent-session block
 * ([com.stratum.domain.workout.progression.buildRecentSessionBlock]) from the per-slot
 * histories and assigns each slot a [ProgressionBaseline]. This corrects the
 * "reps going up across sets at the same weight" pattern (misreported earlier-set RIR)
 * by feeding the engines a descending-capacity baseline so recommendations come out
 * monotonically non-increasing, and lets slots beyond the most-recent session's set
 * count (e.g. 5→3→5) project from the top-set rec instead of falling back to stale
 * positional history.
 *
 * When Linear exhausts ([LinearProgressionEngine.diagnose] emits a transition), the
 * snapshot flips that lift to Double for this compute, appends [SCHEME_CHANGED], and
 * **persists** [WorkoutLift.progressionScheme] so subsequent sessions do not re-fire
 * the transition.
 */
class GenerateProgressionSnapshotUseCase @Inject constructor(
    private val progressionHistoryPort: ProgressionHistoryPort,
    private val doubleProgressionEngine: DoubleProgressionEngine,
    private val linearProgressionEngine: LinearProgressionEngine,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(request: SnapshotRequest): Result<ProgressionSnapshot> {
        val bySlot = mutableMapOf<SetSlotId, SlotRecommendation>()
        for (workoutLift in request.lifts) {
            var scheme = workoutLift.progressionScheme
            val sortedSets = workoutLift.sets.sortedBy { it.position }
            val slots = sortedSets.mapIndexed { index, liftSet ->
                SetSlot(
                    slotId = SetSlotId.Template(liftSet.id),
                    workoutLiftId = workoutLift.id,
                    liftId = workoutLift.liftId,
                    position = index,
                    setType = liftSet.setType,
                    isTopSet = liftSet.isTopSet,
                    repMin = liftSet.repMin,
                    repMax = liftSet.repMax,
                    rirGoal = liftSet.rirGoal,
                    restSeconds = liftSet.restSeconds,
                )
            }

            // Per-slot histories, pulled once and reused for the recent-block build and
            // the per-slot engine dispatch (history is still passed for UI previous*).
            val perSlotHistories = mutableMapOf<Int, List<SlotHistoryEntry>>()
            for (index in sortedSets.indices) {
                perSlotHistories[index] = progressionHistoryPort.historyForSlot(
                    historyRequest(request, workoutLift, index),
                )
            }

            if (scheme == ProgressionScheme.Linear) {
                val topIndex = slots.indexOfFirst { it.isTopSet }.takeIf { it >= 0 } ?: 0
                val transition =
                    linearProgressionEngine.diagnose(workoutLift.liftId, perSlotHistories[topIndex] ?: emptyList())
                        .emittedEvent
                if (transition != null) {
                    appendSchemeTransition(request.programId, workoutLift, transition)
                    persistSchemeTransition(
                        programId = request.programId,
                        workoutLiftId = workoutLift.id,
                        newScheme = ProgressionScheme.Double,
                    )
                    scheme = ProgressionScheme.Double
                }
            }

            val engine = engineFor(scheme)
            val liftLoadType = request.loadTypesById[workoutLift.liftId] ?: LoadType.Weighted
            val liftTiers = request.incrementTiersById[workoutLift.liftId].orEmpty()

            val insights = liftTrainingInsightsRepository.getByLiftId(workoutLift.liftId)
            val fatigueSlope = insights?.fatigueDecaySlope
            val fatigueIntercept = insights?.fatigueDecayIntercept

            // Capacity-ordered recent block → per-slot math baselines. Null = cold start
            // (no history for this lift at all); every slot gets a null baseline.
            val recentBlock = buildRecentSessionBlock(perSlotHistories)

            // Compute top set recommended weight first to derive the target weight for all slots
            val topIndex = slots.indexOfFirst { it.isTopSet }.takeIf { it >= 0 } ?: 0
            val topSlot = slots[topIndex]
            val topBaseline = baselineForSlot(recentBlock, topIndex)
            val topRec = engine.recommend(
                SlotProgressionRequest(
                    slot = topSlot,
                    history = perSlotHistories[topIndex] ?: emptyList(),
                    fallbackOneRm = null,
                    loadType = liftLoadType,
                    progressionBaseline = topBaseline,
                    incrementTiers = liftTiers,
                ),
            )
            val currentTopSetWeight = topRec.recommendedWeight

            for ((index, liftSet) in sortedSets.withIndex()) {
                val slot = slots[index]
                val history = perSlotHistories[index] ?: emptyList()
                val priorSlot = if (index != topIndex) topSlot else null
                val priorRec = if (index != topIndex) topRec else null

                bySlot[slot.slotId] = engine.recommend(
                    SlotProgressionRequest(
                        slot = slot,
                        history = history,
                        fallbackOneRm = null,
                        loadType = liftLoadType,
                        currentTopSetWeight = currentTopSetWeight,
                        priorSlotRecommendation = priorRec,
                        priorSlot = priorSlot,
                        perLiftSlope = fatigueSlope,
                        perLiftIntercept = fatigueIntercept,
                        progressionBaseline = baselineForSlot(recentBlock, index),
                        incrementTiers = liftTiers,
                    ),
                )
            }
        }
        return Result.success(ProgressionSnapshot(request.workoutId, bySlot))
    }

    /**
     * Maps a slot (by position order) to its progression math baseline from the
     * capacity-descending recent block. Slot `i` receives `performances[i]`; slots
     * beyond the block's size (most-recent session logged fewer sets than the current
     * program) project from the prior slot rec.
     */
    private fun baselineForSlot(
        recentBlock: com.stratum.domain.workout.progression.RecentSessionBlock?,
        positionIndex: Int,
    ): ProgressionBaseline? {
        if (recentBlock == null) return null
        val performances = recentBlock.performances
        return if (positionIndex < performances.size) {
            ProgressionBaseline.FromRecent(performances[positionIndex].entry)
        } else {
            ProgressionBaseline.ProjectFromPrior
        }
    }

    private fun engineFor(scheme: ProgressionScheme): ProgressionEngine =
        when (scheme) {
            ProgressionScheme.Double -> doubleProgressionEngine
            ProgressionScheme.Linear -> linearProgressionEngine
        }

    private fun historyRequest(
        request: SnapshotRequest,
        workoutLift: WorkoutLift,
        setIndex: Int,
    ): SlotHistoryRequest =
        SlotHistoryRequest(
            programId = request.programId,
            workoutId = request.workoutId,
            workoutLiftId = workoutLift.id,
            liftId = workoutLift.liftId,
            setIndex = setIndex,
        )

    private suspend fun appendSchemeTransition(
        programId: ProgramId,
        workoutLift: WorkoutLift,
        transition: SchemeTransitionEvent,
    ) {
        programChangeEventRepository.append(
            ProgramChangeEvent(
                id = ProgramChangeEventId(UUID.randomUUID().toString()),
                programId = programId,
                eventType = ProgramChangeEventType.SCHEME_CHANGED,
                occurredAt = Instant.now(),
                payload = ProgramChangeEventPayload.SchemeChanged(
                    workoutLiftId = workoutLift.id,
                    liftId = workoutLift.liftId,
                    previous = transition.from,
                    current = transition.to,
                ),
            ),
        )
    }

    /**
     * Writes [newScheme] onto the matching [WorkoutLift] in the program template so the
     * lift does not remain Linear forever (which would re-emit transition events on every
     * snapshot). No-ops when the program cannot be loaded or the lift is already on
     * [newScheme].
     */
    private suspend fun persistSchemeTransition(
        programId: ProgramId,
        workoutLiftId: WorkoutLiftId,
        newScheme: ProgressionScheme,
    ) {
        val program = programRepository.getProgramById(programId)
            ?: programRepository.getProgram()
            ?: return
        var changed = false
        val updatedWorkouts = program.workouts.map { workout ->
            workout.copy(
                workoutLifts = workout.workoutLifts.map { lift ->
                    if (lift.id == workoutLiftId && lift.progressionScheme != newScheme) {
                        changed = true
                        lift.copy(progressionScheme = newScheme)
                    } else {
                        lift
                    }
                },
            )
        }
        if (changed) {
            programRepository.saveProgram(program.copy(workouts = updatedWorkouts))
        }
    }
}
