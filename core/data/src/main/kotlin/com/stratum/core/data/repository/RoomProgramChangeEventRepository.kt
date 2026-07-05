package com.stratum.core.data.repository

import com.stratum.core.data.db.ProgramChangeEventDao
import com.stratum.core.data.db.ProgramChangeEventEntity
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.ProgramChangeEventRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomProgramChangeEventRepository @Inject constructor(
    private val dao: ProgramChangeEventDao,
) : ProgramChangeEventRepository {
    override suspend fun append(event: ProgramChangeEvent) {
        dao.insert(event.toEntity())
    }

    override suspend fun getEventsForProgram(programId: ProgramId, since: Instant?): List<ProgramChangeEvent> {
        val entities = dao.getForProgram(programId)
        return entities
            .filter { entity -> since == null || entity.occurredAtEpochMillis >= since.toEpochMilli() }
            .map { it.toDomain() }
    }

    private fun ProgramChangeEvent.toEntity(): ProgramChangeEventEntity {
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

    private fun ProgramChangeEventEntity.toDomain(): ProgramChangeEvent {
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
            }
        return ProgramChangeEvent(
            id = id,
            programId = programId,
            eventType = eventType,
            occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
            payload = payload,
        )
    }
}
