package com.stratum.application.program

import com.stratum.domain.program.schedule.resolveInitialCycleStartEpochDay
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.application.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.stratum.domain.program.Program

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
) {
    suspend operator fun invoke(program: Program): Result<Unit> =
        runCatching {
            require(program.workouts.isNotEmpty()) { "Program must have at least one workout" }
            require(program.workouts.any { it.workoutLifts.isNotEmpty() }) {
                "Program must have at least one exercise"
            }
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
        }
}
