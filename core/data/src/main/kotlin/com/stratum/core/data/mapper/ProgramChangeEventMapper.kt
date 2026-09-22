package com.stratum.core.data.mapper

import com.stratum.core.data.db.ProgramChangeEventEntity
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import java.time.Instant

internal fun ProgramChangeEvent.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): ProgramChangeEventEntity {
    val liftSwapped = payload as? ProgramChangeEventPayload.LiftSwapped
    val workoutEdited = payload as? ProgramChangeEventPayload.WorkoutEdited
    val cycleCompleted = payload as? ProgramChangeEventPayload.CycleCompleted
    val schemeChanged = payload as? ProgramChangeEventPayload.SchemeChanged
    return ProgramChangeEventEntity(
        id = id,
        programId = programId,
        eventType = eventType,
        occurredAtEpochMillis = occurredAt.toEpochMilli(),
        workoutLiftId = liftSwapped?.workoutLiftId ?: schemeChanged?.workoutLiftId,
        previousLiftId = liftSwapped?.previousLiftId,
        newLiftId = liftSwapped?.newLiftId,
        workoutId = workoutEdited?.workoutId,
        completedCycleNumber = cycleCompleted?.completedCycleNumber,
        schemeLiftId = schemeChanged?.liftId,
        previousScheme = schemeChanged?.previous,
        currentScheme = schemeChanged?.current,
        updatedAtEpochMillis = nowMillis,
        pendingSync = SyncFlags.PENDING_UPSERT,
    )
}

/**
 * Maps a [ProgramChangeEventEntity] back to the domain model.
 *
 * Returns `null` for `SCHEME_CHANGED` rows whose payload columns are absent. Those rows were
 * written before v6 persisted the scheme payload, so the previous/current scheme cannot be
 * reconstructed — callers skip them instead of crashing (this was a `NotImplementedError`
 * crash in the coaching check-in path).
 */
internal fun ProgramChangeEventEntity.toDomain(): ProgramChangeEvent? {
    val payload =
        when (eventType) {
            ProgramChangeEventType.LIFT_SWAPPED ->
                ProgramChangeEventPayload.LiftSwapped(
                    workoutLiftId = requireNotNull(workoutLiftId),
                    previousLiftId = requireNotNull(previousLiftId),
                    newLiftId = requireNotNull(newLiftId),
                )
            ProgramChangeEventType.WORKOUT_EDITED ->
                ProgramChangeEventPayload.WorkoutEdited(
                    workoutId = requireNotNull(workoutId),
                )
            ProgramChangeEventType.PROGRAM_REGENERATED ->
                ProgramChangeEventPayload.ProgramRegenerated
            ProgramChangeEventType.CYCLE_COMPLETED ->
                ProgramChangeEventPayload.CycleCompleted(
                    completedCycleNumber = requireNotNull(completedCycleNumber),
                )
            ProgramChangeEventType.SCHEME_CHANGED -> {
                val schemeWorkoutLiftId = workoutLiftId ?: return null
                val schemeLiftIdValue = schemeLiftId ?: return null
                val previous = previousScheme ?: return null
                val current = currentScheme ?: return null
                ProgramChangeEventPayload.SchemeChanged(
                    workoutLiftId = schemeWorkoutLiftId,
                    liftId = schemeLiftIdValue,
                    previous = previous,
                    current = current,
                )
            }
        }
    return ProgramChangeEvent(
        id = id,
        programId = programId,
        eventType = eventType,
        occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
        payload = payload,
    )
}
