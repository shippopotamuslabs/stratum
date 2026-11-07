package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.schedule.resolveInitialCycleStartEpochDay
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.application.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.program.Program

/**
 * Outcome of [ActivateProgramUseCase] (STR-243).
 *
 * The validation branches are expected caller outcomes and are not logged;
 * an operational save failure is logged once via [LoggingPort.error] at the
 * use-case boundary and surfaces as [PersistenceFailed]. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface ActivateProgramResult {
    data object Success : ActivateProgramResult

    /** The program has no workouts at all. */
    data object NoWorkouts : ActivateProgramResult

    /** The program has workouts but none of them contains an exercise. */
    data object NoExercises : ActivateProgramResult

    /** The activation save failed (repository / store I/O). */
    data object PersistenceFailed : ActivateProgramResult
}

/**
 * Saves [program] (if needed) and makes it the sole active training program. Prior active programs
 * demote to the library — nothing is deleted.
 *
 * Sets [Program.cycleStartEpochDay] via [resolveInitialCycleStartEpochDay]: 7-day Mon-start weeks
 * anchor to Monday so slot 0 stays Monday (not "today", which mis-labels MTRFSa as Tue-first).
 */
class ActivateProgramUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(program: Program): ActivateProgramResult {
        if (program.workouts.isEmpty()) return ActivateProgramResult.NoWorkouts
        if (program.workouts.none { it.workoutLifts.isNotEmpty() }) {
            return ActivateProgramResult.NoExercises
        }
        return try {
            val toActivate =
                program
                    .copy(cycleStartEpochDay = resolveInitialCycleStartEpochDay(program))
                    .activate()
            programRepository.saveProgram(toActivate)
            programChangeEventRepository.append(
                ProgramChangeEvent(
                    id = ProgramChangeEventId(UUID.randomUUID().toString()),
                    programId = toActivate.id,
                    eventType = ProgramChangeEventType.PROGRAM_REGENERATED,
                    occurredAt = Instant.now(),
                    payload = ProgramChangeEventPayload.ProgramRegenerated,
                ),
            )
            ActivateProgramResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to activate program: ${error.message}", error)
            ActivateProgramResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "ActivateProgram"
    }
}
