package com.stratum.domain.workout

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionEngine
import com.stratum.domain.workout.progression.ProgressionEngine
import com.stratum.domain.workout.progression.SchemeTransitionEvent
import com.stratum.domain.workout.progression.SlotProgressionRequest
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
)

/**
 * Computes the progression snapshot once per session from history (D3). Recomputed
 * only on exercise swap. Dispatches to the matching progression engine per lift.
 */
class GenerateProgressionSnapshotUseCase @Inject constructor(
    private val progressionHistoryPort: ProgressionHistoryPort,
    private val doubleProgressionEngine: DoubleProgressionEngine,
    private val linearProgressionEngine: LinearProgressionEngine,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
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

            if (scheme == ProgressionScheme.Linear) {
                val topIndex = slots.indexOfFirst { it.isTopSet }.takeIf { it >= 0 } ?: 0
                val topHistory = progressionHistoryPort.historyForSlot(
                    historyRequest(request, workoutLift, topIndex),
                )
                val transition = linearProgressionEngine.diagnose(workoutLift.liftId, topHistory).emittedEvent
                if (transition != null) {
                    appendSchemeTransition(request.programId, workoutLift, transition)
                    scheme = ProgressionScheme.Double
                }
            }

            val engine = engineFor(scheme)
            val liftLoadType = request.loadTypesById[workoutLift.liftId] ?: LoadType.Weighted

            val insights = liftTrainingInsightsRepository.getByLiftId(workoutLift.liftId)
            val fatigueSlope = insights?.fatigueDecaySlope
            val fatigueIntercept = insights?.fatigueDecayIntercept

            // Compute top set recommended weight first to derive the target weight for all slots
            val topIndex = slots.indexOfFirst { it.isTopSet }.takeIf { it >= 0 } ?: 0
            val topSlot = slots[topIndex]
            val topHistory = progressionHistoryPort.historyForSlot(
                historyRequest(request, workoutLift, topIndex),
            )
            val topRec = engine.recommend(
                SlotProgressionRequest(
                    slot = topSlot,
                    history = topHistory,
                    fallbackOneRm = null,
                    loadType = liftLoadType,
                )
            )
            val currentTopSetWeight = topRec.recommendedWeight

            for ((index, liftSet) in sortedSets.withIndex()) {
                val slot = slots[index]
                val history = progressionHistoryPort.historyForSlot(
                    historyRequest(request, workoutLift, index),
                )
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
                    ),
                )
            }
        }
        return Result.success(ProgressionSnapshot(request.workoutId, bySlot))
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
}
