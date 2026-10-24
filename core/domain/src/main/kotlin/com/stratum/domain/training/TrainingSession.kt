package com.stratum.domain.training

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

enum class TrainingSessionStatus {
    IN_PROGRESS,
    COMPLETED,
}

enum class TrainingSessionKind {
    PROGRAM_WORKOUT,
    REST_DAY,
    AD_HOC,
}

enum class TrainingSessionOrigin {
    IMPORT,
    LIVE,
}

/**
 * One training session (program workout, rest skip, ad-hoc, or import).
 *
 * History is permanent: [programId] / [workoutId] are soft references cleared when the
 * program template is deleted (not Room FKs — template rewrite must not unlink live sessions).
 * Display names are snapshotted at **session create** only so history UI does not need the live
 * program hierarchy or a probe of [LoggedSet] rows.
 *
 * Lifecycle and `endedAt` semantics live on this type (STR-166); use cases orchestrate and
 * must not re-decide them with raw `copy()` state edits or local endedAt fallbacks:
 *
 * - Transitions: [complete] finishes a live session; the [started] / [skipped] / [imported]
 *   factories are the only session-creation shapes; [retime] moves a completed session's
 *   window. Abandon and unskip are deletions of rows, not states — no transition exists for
 *   them on purpose, and their use cases keep only the row-selection logic.
 * - A null [endedAt] has exactly two meanings, each with one named behavior:
 *   on a COMPLETED session it means "finished, end time unknown" (imported history) and
 *   [completedAt] falls back to [startedAt]; on an IN_PROGRESS session it means "not ended
 *   yet" and [liveEnd] falls back to the current time for elapsed-time math. A caller that
 *   owns a different fallback (Health Connect segment bounds) uses [endOr] so that meaning
 *   stays named and tested here instead of being re-invented at the call site.
 *
 * The one constructor invariant is the genuinely impossible time ordering: [endedAt] may
 * not be before [startedAt] when both are present (equal is legal — skip/rest ledger rows
 * start and end at the same instant). Status/`endedAt` combinations are NOT invariant-checked:
 * a COMPLETED session with a null end is the legitimate imported shape above.
 */
data class TrainingSession(
    val id: TrainingSessionId,
    /** Live program id when known; null after the program template is deleted. */
    val programId: ProgramId?,
    /** Live workout id when known; null for rest/ad-hoc or after soft-unlink on program delete. */
    val workoutId: WorkoutId?,
    val status: TrainingSessionStatus,
    val kind: TrainingSessionKind,
    val origin: TrainingSessionOrigin,
    val cycleNumberSnapshot: Int,
    val weekdaySnapshot: Weekday?,
    /** Cycle space slot index at log time (train or rest). Null for legacy/ad-hoc rows. */
    val cycleSlotIndexSnapshot: Int? = null,
    val startedAt: Instant,
    val endedAt: Instant?,
    val sourceApp: String?,
    /** Program display name at session create (survives program delete). */
    val programNameSnapshot: String? = null,
    /**
     * Workout display name at session create (survives template delete).
     * Null for rest-day ledger rows; live ad-hoc empty starts use a stable label
     * (e.g. "Empty Workout") from [StartTrainingSessionUseCase].
     */
    val workoutNameSnapshot: String? = null,
) {
    init {
        require(endedAt == null || !endedAt.isBefore(startedAt)) {
            "Training session ${id.value} cannot end ($endedAt) before it starts ($startedAt)"
        }
    }

    /**
     * The completion timestamp of a finished session: [endedAt] when known, otherwise
     * [startedAt] (imported history that finished at an unknown end time). This is the
     * single owner of the "completed, end unknown" meaning — history bucketing, detail,
     * and modeling-watermark checks all read this instead of a local fallback.
     */
    val completedAt: Instant
        get() = endedAt ?: startedAt

    /**
     * The end to use for elapsed-time math on a session that may still be live:
     * [endedAt] when present, otherwise [now] ("not ended yet"). Distinct from
     * [completedAt]: a finished session with an unknown end reports its start there,
     * while a live session's elapsed end is the current time here.
     */
    fun liveEnd(now: Instant = Instant.now()): Instant = endedAt ?: now

    /**
     * [endedAt] when present, otherwise the caller-supplied [fallback]. For boundaries
     * whose fallback is a third meaning owned by the caller (e.g. Health Connect uses the
     * session's [liveEnd] as the segment-bound fallback) — the fallback is passed in
     * explicitly rather than re-decided from the field at the call site.
     */
    fun endOr(fallback: Instant): Instant = endedAt ?: fallback

    /** Returns this session, or throws [TrainingSessionNotInProgressException]. */
    fun requireInProgress(): TrainingSession {
        if (status != TrainingSessionStatus.IN_PROGRESS) {
            throw TrainingSessionNotInProgressException(id, status)
        }
        return this
    }

    /** Returns this session, or throws [IllegalArgumentException] unless COMPLETED. */
    fun requireCompleted(): TrainingSession {
        require(status == TrainingSessionStatus.COMPLETED) {
            "Training session ${id.value} is not completed (status=$status)"
        }
        return this
    }

    /**
     * Finishes this live session at [at]. Throws [TrainingSessionNotInProgressException]
     * unless IN_PROGRESS, and [IllegalArgumentException] when [at] is before [startedAt].
     */
    fun complete(at: Instant = Instant.now()): TrainingSession {
        requireInProgress()
        require(!at.isBefore(startedAt)) {
            "Training session ${id.value} cannot end ($at) before it starts ($startedAt)"
        }
        return copy(status = TrainingSessionStatus.COMPLETED, endedAt = at)
    }

    /**
     * Moves a completed session's window to [newStartedAt]–[newEndedAt] (history time edit).
     * The start-after-finish rule lives here: throws [IllegalArgumentException] with the
     * user-facing message when [newStartedAt] is after [newEndedAt], and unless COMPLETED.
     */
    fun retime(newStartedAt: Instant, newEndedAt: Instant): TrainingSession {
        requireCompleted()
        require(!newStartedAt.isAfter(newEndedAt)) {
            "Start time ($newStartedAt) cannot be after finish time ($newEndedAt)"
        }
        return copy(startedAt = newStartedAt, endedAt = newEndedAt)
    }

    companion object {
        /** Creates a live IN_PROGRESS session starting at [startedAt] (end not yet known). */
        fun started(
            id: TrainingSessionId,
            programId: ProgramId?,
            workoutId: WorkoutId?,
            kind: TrainingSessionKind,
            cycleNumberSnapshot: Int,
            weekdaySnapshot: Weekday?,
            cycleSlotIndexSnapshot: Int? = null,
            startedAt: Instant,
            programNameSnapshot: String? = null,
            workoutNameSnapshot: String? = null,
        ): TrainingSession =
            TrainingSession(
                id = id,
                programId = programId,
                workoutId = workoutId,
                status = TrainingSessionStatus.IN_PROGRESS,
                kind = kind,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = cycleNumberSnapshot,
                weekdaySnapshot = weekdaySnapshot,
                cycleSlotIndexSnapshot = cycleSlotIndexSnapshot,
                startedAt = startedAt,
                endedAt = null,
                sourceApp = null,
                programNameSnapshot = programNameSnapshot,
                workoutNameSnapshot = workoutNameSnapshot,
            )

        /**
         * Creates a COMPLETED skip ledger row (workout skip, rest-day skip, or a materialized
         * auto-consumed rest): the slot is consumed at [at], so the session starts and ends
         * at the same instant.
         */
        fun skipped(
            id: TrainingSessionId,
            programId: ProgramId?,
            workoutId: WorkoutId?,
            kind: TrainingSessionKind,
            cycleNumberSnapshot: Int,
            weekdaySnapshot: Weekday?,
            cycleSlotIndexSnapshot: Int? = null,
            at: Instant,
            programNameSnapshot: String? = null,
            workoutNameSnapshot: String? = null,
        ): TrainingSession =
            TrainingSession(
                id = id,
                programId = programId,
                workoutId = workoutId,
                status = TrainingSessionStatus.COMPLETED,
                kind = kind,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = cycleNumberSnapshot,
                weekdaySnapshot = weekdaySnapshot,
                cycleSlotIndexSnapshot = cycleSlotIndexSnapshot,
                startedAt = at,
                endedAt = at,
                sourceApp = null,
                programNameSnapshot = programNameSnapshot,
                workoutNameSnapshot = workoutNameSnapshot,
            )

        /**
         * Creates a COMPLETED imported-history session. A null [endedAt] is legitimate
         * ("finished, end time unknown" — see [completedAt]); a present [endedAt] before
         * [startedAt] is rejected by the constructor invariant.
         */
        fun imported(
            id: TrainingSessionId,
            programId: ProgramId?,
            startedAt: Instant,
            endedAt: Instant?,
            sourceApp: String?,
            programNameSnapshot: String? = null,
        ): TrainingSession =
            TrainingSession(
                id = id,
                programId = programId,
                workoutId = null,
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.AD_HOC,
                origin = TrainingSessionOrigin.IMPORT,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAt = startedAt,
                endedAt = endedAt,
                sourceApp = sourceApp,
                programNameSnapshot = programNameSnapshot,
                workoutNameSnapshot = null,
            )
    }
}
