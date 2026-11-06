package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.CycleProgressResolver
import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.program.Program
import com.stratum.domain.program.ReorderDayItem
import com.stratum.domain.program.reorderCycleDays
import com.stratum.domain.program.slotIndexRemapAfterReorder
import com.stratum.domain.program.toReorderDayItems

/**
 * Outcome of [ReorderProgramCycleDaysUseCase] (STR-243).
 *
 * [ProgramNotFound] is an expected caller outcome and is not logged; an
 * operational load/save failure is logged once via [LoggingPort.error] at the
 * use-case boundary and surfaces as [PersistenceFailed]. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface ReorderProgramCycleDaysResult {
    data object Success : ReorderProgramCycleDaysResult

    /** No program exists for the requested id. */
    data class ProgramNotFound(val programId: ProgramId) : ReorderProgramCycleDaysResult

    /** The reorder failed (repository / store I/O). */
    data object PersistenceFailed : ReorderProgramCycleDaysResult
}

/**
 * Persists a reordered cycle-day list (train + rest slots) for an existing program.
 * Used from the Programs overview reorder screen (not Create Program drafts).
 *
 * Also rewrites [TrainingSession.cycleSlotIndexSnapshot] for the current cycle so rest ledger
 * progress and unskip matching track the new slot layout, then reconciles [Program.cyclePosition].
 */
class ReorderProgramCycleDaysUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        programId: ProgramId,
        orderedDayIds: List<String>,
    ): ReorderProgramCycleDaysResult {
        val program =
            try {
                programRepository.getProgramById(programId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                loggingPort.error(TAG, "Failed to reorder program cycle days: ${error.message}", error)
                return ReorderProgramCycleDaysResult.PersistenceFailed
            }
                ?: return ReorderProgramCycleDaysResult.ProgramNotFound(programId)
        val reordered = program.reorderCycleDays(orderedDayIds)
        if (reordered == program) return ReorderProgramCycleDaysResult.Success

        return try {
            val slotRemap = program.slotIndexRemapAfterReorder(orderedDayIds)
            val sessions = trainingLogRepository.getAllSessions()
            val remappedSessions =
                remapCurrentCycleSlotSnapshots(
                    program = program,
                    sessions = sessions,
                    slotRemap = slotRemap,
                )
            remappedSessions.forEach { session ->
                trainingLogRepository.updateSession(session)
            }

            val sessionsForPointer =
                sessions.map { session ->
                    remappedSessions.firstOrNull { updated -> updated.id == session.id } ?: session
                }
            val reconciled =
                reordered.copy(
                    cyclePosition =
                        CycleProgressResolver.reconcileCyclePosition(
                            reordered,
                            sessionsForPointer,
                        ),
                )
            programRepository.saveProgram(reconciled)
            ReorderProgramCycleDaysResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to reorder program cycle days: ${error.message}", error)
            ReorderProgramCycleDaysResult.PersistenceFailed
        }
    }

    /** Loads reorder rows for the given program (empty when missing). */
    suspend fun loadReorderItems(programId: ProgramId): List<ReorderDayItem> {
        val program = programRepository.getProgramById(programId) ?: return emptyList()
        return program.toReorderDayItems()
    }

    private fun remapCurrentCycleSlotSnapshots(
        program: Program,
        sessions: List<TrainingSession>,
        slotRemap: Map<Int, Int>,
    ): List<TrainingSession> {
        if (slotRemap.isEmpty()) return emptyList()
        return sessions.mapNotNull { session ->
            if (session.programId != program.id) return@mapNotNull null
            if (session.cycleNumberSnapshot != program.currentCycleNumber) return@mapNotNull null
            val oldIndex = session.cycleSlotIndexSnapshot ?: return@mapNotNull null
            val newIndex = slotRemap[oldIndex] ?: return@mapNotNull null
            if (newIndex == oldIndex) return@mapNotNull null
            session.copy(cycleSlotIndexSnapshot = newIndex)
        }
    }

    private companion object {
        const val TAG = "ReorderProgramCycleDays"
    }
}
