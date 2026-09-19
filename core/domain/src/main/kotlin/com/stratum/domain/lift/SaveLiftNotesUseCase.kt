package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.ProgramRepository
import java.time.Instant
import javax.inject.Inject

/**
 * Persists dirty note tabs. Session notes go to [LiftNotesRepository]; catalog pins to
 * [LiftRepository]; workout pins to [ProgramRepository] (0-row update → overlay needed).
 *
 * Each tab prefers its multi-note list field when non-null (whole-list replace) and
 * falls back to the legacy single-body field otherwise, so pre-list callers keep working.
 */
class SaveLiftNotesUseCase @Inject constructor(
    private val liftNotesRepository: LiftNotesRepository,
    private val liftRepository: LiftRepository,
    private val programRepository: ProgramRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(command: SaveLiftNotesCommand): Result<SaveLiftNotesResult> =
        runCatchingLogged(loggingPort, TAG, "Failed to save lift notes") {
            command.sessionNotes?.let { inputs ->
                persistSessionNotes(command, inputs)
            } ?: command.sessionNote?.let { edit -> persistSessionNote(command, edit) }
            command.liftNotes?.let { inputs ->
                liftRepository.setPinnedNotes(command.liftId, inputs)
            } ?: command.liftNote?.let { edit ->
                liftRepository.setPinnedNote(command.liftId, sanitizeLiftNoteBody(edit.body))
            }
            val workoutNeedsOverlay =
                command.workoutNotes?.let { inputs ->
                    !programRepository.setWorkoutLiftPinnedNotes(command.workoutLiftId, inputs)
                } ?: command.workoutNote?.let { edit ->
                    val written =
                        programRepository.setWorkoutLiftPinnedNote(
                            command.workoutLiftId,
                            sanitizeLiftNoteBody(edit.body),
                        )
                    !written
                } ?: false
            SaveLiftNotesResult(workoutNoteNeedsOverlay = workoutNeedsOverlay)
        }

    private suspend fun persistSessionNotes(
        command: SaveLiftNotesCommand,
        inputs: List<SessionNoteInput>,
    ) {
        val surviving = inputs.filter { sanitizeLiftNoteBody(it.body) != null }
        if (surviving.isEmpty()) {
            liftNotesRepository.deleteSessionNote(command.sessionId, command.workoutLiftId)
            return
        }
        liftNotesRepository.replaceSessionNotes(
            sessionId = command.sessionId,
            workoutLiftId = command.workoutLiftId,
            liftId = command.liftId,
            notes = surviving,
        )
    }

    private suspend fun persistSessionNote(
        command: SaveLiftNotesCommand,
        edit: OptionalNoteEdit,
    ) {
        val body = sanitizeLiftNoteBody(edit.body)
        if (body == null) {
            liftNotesRepository.deleteSessionNote(command.sessionId, command.workoutLiftId)
            return
        }
        val existing =
            liftNotesRepository.getSessionNote(command.sessionId, command.workoutLiftId)
        val note =
            LiftSessionNote(
                id = existing?.id ?: LiftSessionNoteId.forSlot(command.sessionId, command.workoutLiftId),
                sessionId = command.sessionId,
                liftId = command.liftId,
                workoutLiftId = command.workoutLiftId,
                body = body,
                updatedAt = Instant.now(),
            )
        liftNotesRepository.upsertSessionNote(note)
    }

    private companion object {
        const val TAG = "SaveLiftNotes"
    }
}
