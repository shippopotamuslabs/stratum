package com.stratum.domain.workout

import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
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
import com.stratum.domain.workout.dropset.EstimateDropSetWeightUseCase
import com.stratum.domain.workout.dropset.applyDropSetPrescriptions
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionEngine
import com.stratum.domain.workout.progression.ProgressionBaseline
import com.stratum.domain.workout.progression.ProgressionDiagnostic
import com.stratum.domain.workout.progression.ProgressionEngine
import com.stratum.domain.workout.progression.SchemeTransitionEvent
import com.stratum.domain.workout.progression.SlotProgressionRequest
import com.stratum.domain.workout.progression.buildRecentSessionBlock
import com.stratum.domain.workout.progression.resolveStraightSetSharedWeight
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
    /**
     * Per-lift adder stops from the catalog (storage Lb). Empty map / missing keys → no
     * adder (plain major-lattice path). Populated from the lift's effective micro.
     * Mixed-unit stops are converted to the tiers unit at the rounding boundary.
     */
    val microIncrementsById: Map<LiftId, List<Weight>> = emptyMap(),
    /**
     * In-use equipment per template lift (STR-123: swap override, else template
     * assignment, else lift default — resolved by the caller). A present entry scopes
     * that lift's slot histories to the (lift, equipment) pair; legacy null-equipment
     * rows are excluded from a scoped pool. Missing keys leave history unfiltered
     * (back-compat + unassigned lifts). Bodyweight lifts are never filtered.
     * An empty scoped pool cold-starts like a lift with no history (conservative).
     */
    val equipmentIdByWorkoutLiftId: Map<WorkoutLiftId, EquipmentId> = emptyMap(),
)

/**
 * Computes the progression snapshot once per session from history (D3). Recomputed
 * on exercise swap and on equipment swap. Dispatches to the matching progression
 * engine per lift.
 *
 * Per lift, builds a capacity-ordered recent-session block
 * ([com.stratum.domain.workout.progression.buildRecentSessionBlock]) from the per-slot
 * histories and assigns each slot a [ProgressionBaseline]. This corrects the
 * "reps going up across sets at the same weight" pattern (misreported earlier-set RIR)
 * by feeding the engines a descending-capacity baseline so recommendations come out
 * monotonically non-increasing.
 *
 * Slots beyond the most-recent session's set count (e.g. 5→3→5) use
 * [ProgressionBaseline.ProjectFromPrior] and project **sequentially** from the prior
 * slot rec — never from stale older-session positional history, and never by re-anchoring
 * every missing slot to the top set (which produced positively-sloped rep curves).
 *
 * After the scheme gate picks a candidate top weight, [resolveStraightSetSharedWeight]
 * may lower it once for the whole lift (straight sets stay uniform) when the last
 * program set cannot clear the rep floor — preferring real last-set data when the recent
 * session covered every slot. All slots are then re-emitted at that shared weight with
 * honest rep projections.
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
    private val estimateDropSetWeight: EstimateDropSetWeightUseCase = EstimateDropSetWeightUseCase(),
) {
    suspend operator fun invoke(request: SnapshotRequest): Result<ProgressionSnapshotOutcome> {
        val bySlot = mutableMapOf<SetSlotId, SlotRecommendation>()
        // One diagnostics collector per computation (STR-250): load-math steps that
        // lose their baseline to a zero-load history set record it here instead of
        // throwing; the caller logs each entry once at the boundary.
        val diagnostics = mutableListOf<ProgressionDiagnostic>()
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
                    myoRepKind = liftSet.myoRepKind,
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
            // STR-123: scope slot histories to the in-use (lift, equipment) pair so
            // recommendations and previous-sets follow the card's equipment. An empty
            // scoped pool cold-starts through the same null-baseline path as no history.
            val pairEquipmentId = request.equipmentIdByWorkoutLiftId[workoutLift.id]
            val scopedLoadType = request.loadTypesById[workoutLift.liftId] ?: LoadType.Weighted
            val scopedHistories =
                scopeSlotHistoriesToEquipmentPair(perSlotHistories, pairEquipmentId, scopedLoadType)

            if (scheme == ProgressionScheme.Linear) {
                val topIndex = slots.indexOfFirst { it.isTopSet }.takeIf { it >= 0 } ?: 0
                val transition =
                    linearProgressionEngine.diagnose(workoutLift.liftId, scopedHistories[topIndex] ?: emptyList())
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
            val liftMicro = request.microIncrementsById[workoutLift.liftId].orEmpty()

            val insights = liftTrainingInsightsRepository.getByLiftId(workoutLift.liftId)
            val fatigueSlope = insights?.fatigueDecaySlope
            val fatigueIntercept = insights?.fatigueDecayIntercept

            // Capacity-ordered recent block → per-slot math baselines. Null = cold start
            // (no history for this lift at all); every slot gets a null baseline.
            val recentBlock = buildRecentSessionBlock(scopedHistories)

            // Scheme gate on the top set → candidate shared weight, then look-ahead may
            // drop once for the whole straight set (real data preferred when fully covered).
            val topIndex = slots.indexOfFirst { it.isTopSet }.takeIf { it >= 0 } ?: 0
            val topSlot = slots[topIndex]
            val topBaseline = baselineForSlot(recentBlock, topIndex)
            val topRec = engine.recommend(
                SlotProgressionRequest(
                    slot = topSlot,
                    history = scopedHistories[topIndex] ?: emptyList(),
                    fallbackOneRm = null,
                    loadType = liftLoadType,
                    progressionBaseline = topBaseline,
                    incrementTiers = liftTiers,
                    microIncrements = liftMicro,
                    diagnostics = diagnostics,
                ),
            )
            val sharedWeight = resolveStraightSetSharedWeight(
                candidateWeight = topRec.recommendedWeight,
                slots = slots,
                recentBlock = recentBlock,
                perLiftSlope = fatigueSlope,
                perLiftIntercept = fatigueIntercept,
                incrementTiers = liftTiers,
                loadType = liftLoadType,
                micro = liftMicro,
                diagnostics = diagnostics,
            )

            // Emit every slot at the finalized shared weight with sequential priors so
            // ProjectFromPrior continues the decay curve instead of re-anchoring to top.
            // FromRecent slots reproject their own baseline at the shared load; only
            // ProjectFromPrior uses prior-slot fatigue.
            var previousSlot: SetSlot? = null
            var previousRec: SlotRecommendation? = null
            val orderedRecs = ArrayList<SlotRecommendation>(sortedSets.size)
            for (index in sortedSets.indices) {
                val slot = slots[index]
                val history = scopedHistories[index] ?: emptyList()
                val rec = engine.recommend(
                    SlotProgressionRequest(
                        slot = slot,
                        history = history,
                        fallbackOneRm = null,
                        loadType = liftLoadType,
                        currentTopSetWeight = sharedWeight,
                        priorSlotRecommendation = previousRec,
                        priorSlot = previousSlot,
                        perLiftSlope = fatigueSlope,
                        perLiftIntercept = fatigueIntercept,
                        progressionBaseline = baselineForSlot(recentBlock, index),
                        incrementTiers = liftTiers,
                        microIncrements = liftMicro,
                        diagnostics = diagnostics,
                    ),
                )
                orderedRecs.add(rec)
                previousSlot = slot
                previousRec = rec
            }
            // Guard against at-RIR asymmetry (e.g. top rirGoal 2 vs backoff 1 on tied
            // failure capacity) producing a positively-sloped rep curve.
            // Then rewrite DropSet rows: engines emit shared straight-set load for every
            // slot; drops must be 20% off the immediate prior with zero-rest rep projection.
            val monotonic = clampRepsMonotonicNonIncreasing(orderedRecs)
            val withDrops = applyDropSetPrescriptions(
                slots = slots,
                recs = monotonic,
                estimateDropSetWeight = estimateDropSetWeight,
                incrementTiers = liftTiers,
                loadType = liftLoadType,
                micro = liftMicro,
            )
            for (rec in withDrops) {
                bySlot[rec.slotId] = rec
            }
        }
        return Result.success(
            ProgressionSnapshotOutcome(
                snapshot = ProgressionSnapshot(request.workoutId, bySlot),
                diagnostics = diagnostics.toList(),
            ),
        )
    }

    /**
     * Maps a slot (by position order) to its progression math baseline from the
     * capacity-descending recent block. Slot `i` receives `performances[i]`; slots
     * beyond the block's size (most-recent session logged fewer sets than the current
     * program) use [ProgressionBaseline.ProjectFromPrior] and the orchestrator chains
     * fatigue from the previous slot rec.
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
        val targetLift =
            program.workouts
                .flatMap { workout -> workout.workoutLifts }
                .firstOrNull { lift -> lift.id == workoutLiftId }
        if (targetLift != null && targetLift.progressionScheme != newScheme) {
            programRepository.saveProgram(
                program.replaceWorkoutLift(targetLift.copy(progressionScheme = newScheme)),
            )
        }
    }
}

/**
 * Scopes per-slot histories to one (lift, equipment) pair (STR-123). Pure.
 * Null equipment (unassigned lift) and [LoadType.Bodyweight] return [histories]
 * unchanged; otherwise only entries logged on [equipmentId] survive, so an empty
 * scoped pool cold-starts like a lift with no history.
 */
internal fun scopeSlotHistoriesToEquipmentPair(
    histories: Map<Int, List<SlotHistoryEntry>>,
    equipmentId: EquipmentId?,
    loadType: LoadType,
): Map<Int, List<SlotHistoryEntry>> {
    if (equipmentId == null || loadType == LoadType.Bodyweight) return histories
    return histories.mapValues { (_, entries) ->
        entries.filter { it.loggedSet.equipmentId == equipmentId }
    }
}

/**
 * Forces recommended reps to be monotonically non-increasing across slots at the same
 * shared load. Pure; preserves weight / previous* / isProjected.
 *
 * Guards at-RIR asymmetry when failure capacities are tied but slot `rirGoal` falls
 * (e.g. top RIR 2 → backoff RIR 1 would otherwise recommend +1 rep).
 */
fun clampRepsMonotonicNonIncreasing(
    recs: List<SlotRecommendation>,
): List<SlotRecommendation> {
    if (recs.size <= 1) return recs
    var prevReps = Int.MAX_VALUE
    return recs.map { rec ->
        val clamped = minOf(rec.baselineExpectedReps, prevReps)
        prevReps = clamped
        if (clamped == rec.baselineExpectedReps) {
            rec
        } else {
            rec.copy(baselineExpectedReps = clamped)
        }
    }
}
