package com.stratum.application.training

import com.stratum.domain.onboarding.Weekday
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.schedule.CycleProjection
import com.stratum.domain.program.schedule.CycleSlot
import com.stratum.domain.program.schedule.resolvedCycleSlots
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.stratum.domain.training.CycleProgressResolver
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.sessionsWithMaterializedAutoRests
import com.stratum.domain.program.Program
import com.stratum.domain.program.identity.ProgramId

/**
 * Records a completed rest slot for the current cycle and advances cycle progress (rests count
 * toward cycle completion). Prefer [invoke] with [slotIndex]; the [Weekday] overload is a
 * display-oriented fallback that matches a rest slot by projected weekday when possible.
 */
class RecordRestDaySkipUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(slotIndex: Int): Result<TrainingSession> {
        val program =
            programRepository.getProgram()
                ?: return Result.failure(IllegalStateException("No active program found to record rest day skip"))
        val slots = program.resolvedCycleSlots()
        val restSlot =
            slots.filterIsInstance<CycleSlot.Rest>().firstOrNull { it.slotIndex == slotIndex }
                ?: return Result.failure(IllegalArgumentException("Slot $slotIndex is not a rest slot on program ${program.id.value}"))
        val projectedWeekday =
            program.cycleStartEpochDay?.let { start ->
                CycleProjection.projectedWeekday(start, restSlot.slotIndex)
            }
        return runCatching {
            persistRestSession(
                programId = program.id,
                programName = program.name,
                cycleNumber = program.currentCycleNumber,
                slotIndex = restSlot.slotIndex,
                weekday = projectedWeekday,
                sessionsBefore = trainingLogRepository.getAllSessions(),
                program = program,
            )
        }
    }

    /**
     * Match first unconsumed rest whose projected weekday equals [weekday]. Requires a matching
     * rest slot with a non-null [TrainingSession.cycleSlotIndexSnapshot] so rest counts toward
     * cycle progress and can be unskipped.
     */
    suspend operator fun invoke(weekday: Weekday): Result<TrainingSession> {
        val program =
            programRepository.getProgram()
                ?: return Result.failure(IllegalStateException("No active program found to record rest day skip"))
        val sessions = trainingLogRepository.getAllSessions()
        val progress = CycleProgressResolver.reconcileSlotProgress(program, sessions)
        val slots = program.resolvedCycleSlots()
        val restCandidates =
            slots.filterIsInstance<CycleSlot.Rest>().filter { rest ->
                rest.slotIndex !in progress.consumedSlotIndices
            }
        val start = program.cycleStartEpochDay
        val matched =
            if (start != null) {
                restCandidates.firstOrNull { rest ->
                    CycleProjection.projectedWeekday(start, rest.slotIndex) == weekday
                }
            } else {
                null
            } ?: restCandidates.firstOrNull()
                ?: return Result.failure(NoSuchElementException("No unconsumed rest slot to mark for $weekday"))
        return runCatching {
            persistRestSession(
                programId = program.id,
                programName = program.name,
                cycleNumber = program.currentCycleNumber,
                slotIndex = matched.slotIndex,
                weekday = weekday,
                sessionsBefore = sessions,
                program = program,
            )
        }
    }

    private suspend fun persistRestSession(
        programId: com.stratum.domain.program.identity.ProgramId,
        programName: String,
        cycleNumber: Int,
        slotIndex: Int,
        weekday: Weekday?,
        sessionsBefore: List<TrainingSession>,
        program: com.stratum.domain.program.Program,
    ): TrainingSession {
        val now = Instant.now()
        val session =
            TrainingSession.skipped(
                id = TrainingSessionId(UUID.randomUUID().toString()),
                programId = programId,
                workoutId = null,
                kind = TrainingSessionKind.REST_DAY,
                cycleNumberSnapshot = cycleNumber,
                weekdaySnapshot = weekday,
                cycleSlotIndexSnapshot = slotIndex,
                at = now,
                programNameSnapshot = programName,
                workoutNameSnapshot = null,
            )
        val baseSessions = sessionsBefore + session
        val (ledger, autoRests) =
            sessionsWithMaterializedAutoRests(
                program = program,
                sessions = baseSessions,
                now = now,
            )
        val effects = program.advanceCycle(ledger)
        trainingLogRepository.persistSessionsWithCycleEffects(
            sessions = listOf(session) + autoRests,
            cyclePointerUpdate = effects.pointerUpdate,
            cycleCompletedEvent = effects.cycleCompletedEvent,
        )
        return session
    }
}
