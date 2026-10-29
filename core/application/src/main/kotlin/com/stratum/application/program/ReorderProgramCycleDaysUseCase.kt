package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.CycleProgressResolver
import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import javax.inject.Inject
import com.stratum.domain.program.Program
import com.stratum.domain.program.ReorderDayItem
import com.stratum.domain.program.reorderCycleDays
import com.stratum.domain.program.slotIndexRemapAfterReorder
import com.stratum.domain.program.toReorderDayItems

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
    ): Result<Unit> {
        val program =
            programRepository.getProgramById(programId)
                ?: return Result.failure(IllegalArgumentException("Program not found: ${programId.value}"))
        val reordered = program.reorderCycleDays(orderedDayIds)
        if (reordered == program) return Result.success(Unit)

        return runCatchingLogged(loggingPort, TAG, "Failed to reorder program cycle days") {
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
