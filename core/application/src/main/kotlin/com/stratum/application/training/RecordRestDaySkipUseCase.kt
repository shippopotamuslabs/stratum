package com.stratum.application.training

import com.stratum.domain.logging.LoggingPort
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
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of recording a rest-day skip (STR-243).
 *
 * The guard branches carry the typed facts ([NotRestSlot] carries the slot
 * index, [NoUnconsumedRestSlot] the weekday). The originating throwable of a
 * failed write goes once to the diagnostics channel ([LoggingPort.error]) at
 * the use-case boundary — this use case previously returned its failures
 * silently; it now logs them once at the boundary, per the C4 precedent.
 * Coroutine cancellation is not a branch: it propagates.
 */
sealed interface RecordRestDaySkipResult {
    data class Success(val session: TrainingSession) : RecordRestDaySkipResult

    data object NoActiveProgram : RecordRestDaySkipResult

    data class NotRestSlot(val slotIndex: Int) : RecordRestDaySkipResult

    data class NoUnconsumedRestSlot(val weekday: Weekday) : RecordRestDaySkipResult

    /** The skip write failed (repository / store I/O). */
    data object PersistenceFailed : RecordRestDaySkipResult
}

/**
 * Records a completed rest slot for the current cycle and advances cycle progress (rests count
 * toward cycle completion). Prefer [invoke] with [slotIndex]; the [Weekday] overload is a
 * display-oriented fallback that matches a rest slot by projected weekday when possible.
 */
class RecordRestDaySkipUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(slotIndex: Int): RecordRestDaySkipResult {
        val program =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loggingPort.error(TAG, "Failed to record rest day skip: ${t.message}", t)
                return RecordRestDaySkipResult.PersistenceFailed
            }
                ?: return RecordRestDaySkipResult.NoActiveProgram
        val slots = program.resolvedCycleSlots()
        val restSlot =
            slots.filterIsInstance<CycleSlot.Rest>().firstOrNull { it.slotIndex == slotIndex }
                ?: return RecordRestDaySkipResult.NotRestSlot(slotIndex)
        val projectedWeekday =
            program.cycleStartEpochDay?.let { start ->
                CycleProjection.projectedWeekday(start, restSlot.slotIndex)
            }
        return try {
            RecordRestDaySkipResult.Success(
                persistRestSession(
                    programId = program.id,
                    programName = program.name,
                    cycleNumber = program.currentCycleNumber,
                    slotIndex = restSlot.slotIndex,
                    weekday = projectedWeekday,
                    sessionsBefore = trainingLogRepository.getAllSessions(),
                    program = program,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to record rest day skip: ${t.message}", t)
            RecordRestDaySkipResult.PersistenceFailed
        }
    }

    /**
     * Match first unconsumed rest whose projected weekday equals [weekday]. Requires a matching
     * rest slot with a non-null [TrainingSession.cycleSlotIndexSnapshot] so rest counts toward
     * cycle progress and can be unskipped.
     */
    suspend operator fun invoke(weekday: Weekday): RecordRestDaySkipResult {
        val program =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loggingPort.error(TAG, "Failed to record rest day skip: ${t.message}", t)
                return RecordRestDaySkipResult.PersistenceFailed
            }
                ?: return RecordRestDaySkipResult.NoActiveProgram
        return try {
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
                    ?: return RecordRestDaySkipResult.NoUnconsumedRestSlot(weekday)
            RecordRestDaySkipResult.Success(
                persistRestSession(
                    programId = program.id,
                    programName = program.name,
                    cycleNumber = program.currentCycleNumber,
                    slotIndex = matched.slotIndex,
                    weekday = weekday,
                    sessionsBefore = sessions,
                    program = program,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to record rest day skip: ${t.message}", t)
            RecordRestDaySkipResult.PersistenceFailed
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

    private companion object {
        const val TAG = "RecordRestDaySkip"
    }
}
