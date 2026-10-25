package com.stratum.domain.program

import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Persists a program as inactive library inventory without changing the active training program.
 */
class SaveProgramToLibraryUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
) {
    suspend operator fun invoke(program: Program): Result<Unit> =
        runCatching {
            require(program.workouts.isNotEmpty()) { "Program must have at least one workout" }
            require(program.workouts.any { it.workoutLifts.isNotEmpty() }) {
                "Program must have at least one exercise"
            }
            val toSave = program.deactivate()
            programRepository.saveToLibrary(toSave)
            programChangeEventRepository.append(
                ProgramChangeEvent(
                    id = ProgramChangeEventId(UUID.randomUUID().toString()),
                    programId = toSave.id,
                    eventType = ProgramChangeEventType.PROGRAM_REGENERATED,
                    occurredAt = Instant.now(),
                    payload = ProgramChangeEventPayload.ProgramRegenerated,
                ),
            )
        }
}
