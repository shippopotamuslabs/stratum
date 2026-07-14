package com.stratum.domain.training

import com.stratum.domain.program.Program
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.schedule.CycleProjection
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID

data class CyclePointerUpdate(
    val programId: ProgramId,
    val currentCycleNumber: Int,
    val cyclePosition: Int,
    /** New cycle start epoch day when the cycle rolls; null when unchanged / unknown. */
    val cycleStartEpochDay: Long? = null,
)

data class CycleAdvancementEffects(
    val pointerUpdate: CyclePointerUpdate,
    val cycleCompletedEvent: ProgramChangeEvent?,
)

fun computeCycleAdvancement(
    program: Program,
    sessions: List<TrainingSession>,
): CycleAdvancementEffects {
    val progress = CycleProgressResolver.reconcileSlotProgress(program, sessions)
    val cycleComplete = progress.isComplete
    val completedCycleNumber = program.currentCycleNumber
    val nextCycleNumber = if (cycleComplete) completedCycleNumber + 1 else completedCycleNumber
    val nextCyclePosition = if (cycleComplete) 0 else progress.cyclePosition
    val nextCycleStart =
        if (cycleComplete && program.cycleStartEpochDay != null) {
            CycleProjection.nextCycleStartEpochDay(program.cycleStartEpochDay, program.cycleLength)
        } else {
            null
        }
    return CycleAdvancementEffects(
        pointerUpdate =
            CyclePointerUpdate(
                programId = program.id,
                currentCycleNumber = nextCycleNumber,
                cyclePosition = nextCyclePosition,
                cycleStartEpochDay = nextCycleStart,
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
