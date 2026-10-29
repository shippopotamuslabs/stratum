package com.stratum.domain.training

import com.stratum.domain.program.Program
import com.stratum.domain.program.schedule.CycleProgressMath
import com.stratum.domain.program.schedule.CycleProjection
import com.stratum.domain.program.schedule.CycleSlot
import com.stratum.domain.program.schedule.resolvedCycleSlots
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.util.UUID
import com.stratum.domain.program.identity.WorkoutId

/**
 * Builds [TrainingSessionKind.REST_DAY] ledger rows for mid-cycle rests that pure math would
 * auto-consume given [sessions] (trainings + already-persisted rests only).
 *
 * Auto-consume is a **write-time** side effect so UI checkboxes match real sessions and unskip
 * can delete them. Read paths ([CycleProgressResolver]) use the ledger only
 * (`autoConsumeMidCycleRests = false`).
 */
fun materializeAutoConsumedRestSessions(
    program: Program,
    sessions: List<TrainingSession>,
    now: Instant = Instant.now(),
): List<TrainingSession> {
    val slots = program.resolvedCycleSlots()
    if (slots.isEmpty()) return emptyList()

    val completedWorkoutIds =
        sessions
            .filter { session ->
                session.programId == program.id &&
                    session.cycleNumberSnapshot == program.currentCycleNumber &&
                    session.status == TrainingSessionStatus.COMPLETED &&
                    session.kind == TrainingSessionKind.PROGRAM_WORKOUT &&
                    session.workoutId != null
            }
            .mapNotNull { it.workoutId }
            .toSet()

    val restIndices = slots.filterIsInstance<CycleSlot.Rest>().map { it.slotIndex }.toSet()
    val completedRestIndices =
        sessions
            .filter { session ->
                session.programId == program.id &&
                    session.cycleNumberSnapshot == program.currentCycleNumber &&
                    session.status == TrainingSessionStatus.COMPLETED &&
                    session.kind == TrainingSessionKind.REST_DAY
            }
            .mapNotNull { session ->
                session.cycleSlotIndexSnapshot?.takeIf { index -> index in restIndices }
            }
            .toSet()

    val progress =
        CycleProgressMath.reconcile(
            slots = slots,
            completedWorkoutIdsThisCycle = completedWorkoutIds,
            completedRestSlotIndicesThisCycle = completedRestIndices,
            autoConsumeMidCycleRests = true,
        )

    val start = program.cycleStartEpochDay
    return progress.autoConsumedRestSlotIndices.sorted().map { slotIndex ->
        val weekday =
            start?.let { anchor -> CycleProjection.projectedWeekday(anchor, slotIndex) }
        TrainingSession.skipped(
            id = TrainingSessionId(UUID.randomUUID().toString()),
            programId = program.id,
            workoutId = null,
            kind = TrainingSessionKind.REST_DAY,
            cycleNumberSnapshot = program.currentCycleNumber,
            weekdaySnapshot = weekday,
            cycleSlotIndexSnapshot = slotIndex,
            at = now,
            programNameSnapshot = program.name,
            workoutNameSnapshot = null,
        )
    }
}

/**
 * [sessions] plus newly materialised auto-rest rows. Use before [com.stratum.domain.program.Program.advanceCycle] on
 * program-workout complete/skip paths so the pointer sees persisted rests.
 */
fun sessionsWithMaterializedAutoRests(
    program: Program,
    sessions: List<TrainingSession>,
    now: Instant = Instant.now(),
): Pair<List<TrainingSession>, List<TrainingSession>> {
    val autoRests = materializeAutoConsumedRestSessions(program, sessions, now)
    return sessions + autoRests to autoRests
}

/**
 * REST_DAY sessions that pure mid-cycle auto-consume no longer justifies after [deletedWorkoutIds]
 * were removed from the ledger.
 *
 * Compares pure-auto rest sets with/without the deleted trainings (rests ignored as inputs) and
 * returns completed REST_DAY rows for slots that drop out of the pure-auto set. Manual rests that
 * were never pure-auto-justified are kept.
 */
fun autoRestSessionsNoLongerJustified(
    program: Program,
    remainingSessions: List<TrainingSession>,
    deletedWorkoutIds: Set<com.stratum.domain.program.identity.WorkoutId>,
): List<TrainingSession> {
    if (deletedWorkoutIds.isEmpty()) return emptyList()
    val slots = program.resolvedCycleSlots()
    if (slots.isEmpty()) return emptyList()

    val remainingTrainings =
        remainingSessions
            .filter { session ->
                session.programId == program.id &&
                    session.cycleNumberSnapshot == program.currentCycleNumber &&
                    session.status == TrainingSessionStatus.COMPLETED &&
                    session.kind == TrainingSessionKind.PROGRAM_WORKOUT &&
                    session.workoutId != null
            }
            .mapNotNull { it.workoutId }
            .toSet()

    val trainingsBefore = remainingTrainings + deletedWorkoutIds
    val pureAutoBefore =
        CycleProgressMath.reconcile(
            slots = slots,
            completedWorkoutIdsThisCycle = trainingsBefore,
            completedRestSlotIndicesThisCycle = emptySet(),
            autoConsumeMidCycleRests = true,
        ).autoConsumedRestSlotIndices
    val pureAutoAfter =
        CycleProgressMath.reconcile(
            slots = slots,
            completedWorkoutIdsThisCycle = remainingTrainings,
            completedRestSlotIndicesThisCycle = emptySet(),
            autoConsumeMidCycleRests = true,
        ).autoConsumedRestSlotIndices
    val dropSlots = pureAutoBefore - pureAutoAfter
    if (dropSlots.isEmpty()) return emptyList()

    val restIndices = slots.filterIsInstance<CycleSlot.Rest>().map { it.slotIndex }.toSet()
    return remainingSessions.filter { session ->
        session.programId == program.id &&
            session.cycleNumberSnapshot == program.currentCycleNumber &&
            session.status == TrainingSessionStatus.COMPLETED &&
            session.kind == TrainingSessionKind.REST_DAY &&
            session.cycleSlotIndexSnapshot != null &&
            session.cycleSlotIndexSnapshot in restIndices &&
            session.cycleSlotIndexSnapshot in dropSlots
    }
}
