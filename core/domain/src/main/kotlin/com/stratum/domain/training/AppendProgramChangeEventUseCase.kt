package com.stratum.domain.training

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Records program change events in a program's change history.
 *
 * This is the presentation layer's only entry point for recording program change
 * events: ViewModels depend on use cases, never on [ProgramChangeEventRepository]
 * directly (contracts/ARCHITECTURE.md §4.4). Event construction (identity,
 * timestamp, type/payload mapping) lives here, with the other program change
 * event producers, so callers pass only the semantic change.
 */
class AppendProgramChangeEventUseCase @Inject constructor(
    private val programChangeEventRepository: ProgramChangeEventRepository,
) {
    suspend fun appendSchemeChanged(
        programId: ProgramId,
        workoutLiftId: WorkoutLiftId,
        liftId: LiftId,
        previous: ProgressionScheme,
        current: ProgressionScheme,
    ) {
        programChangeEventRepository.append(
            ProgramChangeEvent(
                id = ProgramChangeEventId(UUID.randomUUID().toString()),
                programId = programId,
                eventType = ProgramChangeEventType.SCHEME_CHANGED,
                occurredAt = Instant.now(),
                payload =
                    ProgramChangeEventPayload.SchemeChanged(
                        workoutLiftId = workoutLiftId,
                        liftId = liftId,
                        previous = previous,
                        current = current,
                    ),
            ),
        )
    }

    suspend fun appendLiftSwapped(
        programId: ProgramId,
        workoutLiftId: WorkoutLiftId,
        previousLiftId: LiftId,
        newLiftId: LiftId,
    ) {
        programChangeEventRepository.append(
            ProgramChangeEvent(
                id = ProgramChangeEventId(UUID.randomUUID().toString()),
                programId = programId,
                eventType = ProgramChangeEventType.LIFT_SWAPPED,
                occurredAt = Instant.now(),
                payload =
                    ProgramChangeEventPayload.LiftSwapped(
                        workoutLiftId = workoutLiftId,
                        previousLiftId = previousLiftId,
                        newLiftId = newLiftId,
                    ),
            ),
        )
    }
}
