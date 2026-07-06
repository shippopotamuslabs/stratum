package com.stratum.domain.workout

import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
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
) {
    suspend operator fun invoke(request: SnapshotRequest): Result<ProgressionSnapshot> {
        val bySlot = mutableMapOf<SetSlotId, SlotRecommendation>()
        for (workoutLift in request.lifts) {
            var scheme = workoutLift.progressionScheme
            val slots = workoutLift.sets.mapIndexed { index, liftSet ->
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
            for ((index, liftSet) in workoutLift.sets.withIndex()) {
                val slot = slots[index]
                val history = progressionHistoryPort.historyForSlot(
                    historyRequest(request, workoutLift, index),
                )
                bySlot[slot.slotId] = engine.recommend(
                    SlotProgressionRequest(
                        slot = slot,
                        history = history,
                        fallbackOneRm = null,
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
