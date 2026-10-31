package com.stratum.application.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId

/**
 * Outcome of [SwapWorkoutLiftUseCase]: the swap itself reports success/failure
 * through the surrounding `Result`, while degradations that the swap tolerates
 * ride along as [diagnostics].
 *
 * Placement note (CODE-STYLE-GOSPEL §11): this type is owned by the application
 * layer, not the domain. The degradation it records is detected in use-case
 * coordination — a pinned-notes repository read failing while scoping row-held
 * state to the incoming (slot, lift) pair — not in domain math, so the domain
 * stays free of this operational concern. Diagnostics are drained exactly once
 * at the feature boundary and never reach UI state.
 */
data class SwapWorkoutLiftOutcome(
    val diagnostics: List<SwapWorkoutLiftDiagnostic> = emptyList(),
)

/**
 * A non-fatal swap degradation recorded as data (STR-255).
 *
 * Diagnostics carry what was degraded and why — the identifying context plus the
 * failure as data — and are constructed, never logged, below the boundary.
 * Coroutine cancellation is not a diagnostic: it propagates.
 */
sealed interface SwapWorkoutLiftDiagnostic {
    /**
     * The incoming pair's pinned notes could not be read, so the swap proceeded
     * with empty notes on a premise that may be wrong ("no notes" is then
     * indistinguishable from "notes unreadable" without this record).
     */
    data class PinnedNotesUnreadable(
        val workoutLiftId: WorkoutLiftId,
        val newLiftId: LiftId,
        val cause: Throwable,
    ) : SwapWorkoutLiftDiagnostic
}
