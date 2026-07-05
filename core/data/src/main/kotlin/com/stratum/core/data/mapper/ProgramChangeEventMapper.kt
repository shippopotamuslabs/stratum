package com.stratum.core.data.mapper

import com.stratum.core.data.db.ProgramChangeEventEntity
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload

internal fun ProgramChangeEvent.toEntity(): ProgramChangeEventEntity {
    val liftSwapped = payload as? ProgramChangeEventPayload.LiftSwapped
    val workoutEdited = payload as? ProgramChangeEventPayload.WorkoutEdited
    val cycleCompleted = payload as? ProgramChangeEventPayload.CycleCompleted
    return ProgramChangeEventEntity(
        id = id,
        programId = programId,
        eventType = eventType,
        occurredAtEpochMillis = occurredAt.toEpochMilli(),
        workoutLiftId = liftSwapped?.workoutLiftId,
        previousLiftId = liftSwapped?.previousLiftId,
        newLiftId = liftSwapped?.newLiftId,
        workoutId = workoutEdited?.workoutId,
        completedCycleNumber = cycleCompleted?.completedCycleNumber,
    )
}
