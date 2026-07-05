package com.stratum.domain.training

import com.stratum.domain.program.Program
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID

data class CyclePointerUpdate(
    val programId: ProgramId,
    val currentCycleNumber: Int,
    val cyclePosition: Int,
)

data class CycleAdvancementEffects(
    val pointerUpdate: CyclePointerUpdate,
    val cycleCompletedEvent: ProgramChangeEvent?,
)

fun computeCycleAdvancement(
    program: Program,
    sessions: List<TrainingSession>,
): CycleAdvancementEffects {
    val cyclePosition = CycleProgressResolver.reconcileCyclePosition(program, sessions)
    val cycleComplete = CycleProgressResolver.isCycleComplete(program, sessions)
    val completedCycleNumber = program.currentCycleNumber
    val nextCycleNumber = if (cycleComplete) completedCycleNumber + 1 else completedCycleNumber
    val nextCyclePosition = if (cycleComplete) 0 else cyclePosition
    return CycleAdvancementEffects(
        pointerUpdate =
            CyclePointerUpdate(
                programId = program.id,
                currentCycleNumber = nextCycleNumber,
                cyclePosition = nextCyclePosition,
            ),
        cycleCompletedEvent =
            if (cycleComplete) {
                ProgramChangeEvent(
                    id = ProgramChangeEventId(UUID.randomUUID().toString()),
                    programId = program.id,
                    eventType = ProgramChangeEventType.CYCLE_COMPLETED,
                    occurredAt = Instant.now(),
                    payload = ProgramChangeEventPayload.CycleCompleted(completedCycleNumber),
                )
            } else {
                null
            },
    )
}
