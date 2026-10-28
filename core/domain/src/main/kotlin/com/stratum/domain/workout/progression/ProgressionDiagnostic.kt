package com.stratum.domain.workout.progression

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId

/**
 * True when [this] estimated 1RM can anchor load math. `EstimatedOneRm.repsForWeight`
 * requires a positive e1RM; a zero-load history set derives an e1RM of 0.0, which
 * carries no load information even though the set itself is legitimate stored data.
 * The comparison also rejects NaN.
 */
internal fun Double.isUsableEstimatedOneRm(): Boolean = this > 0.0

/**
 * A non-fatal progression-math event recorded while computing a snapshot (STR-250).
 *
 * Diagnostics are values, not logs: `:core:domain` never logs (CODE_STYLE_RULES.md §1),
 * so the snapshot use case returns them alongside the snapshot and the caller at the
 * application boundary reports each one exactly once through its logging port.
 * Diagnostics never reach the UI.
 */
sealed interface ProgressionDiagnostic {
    /**
     * A history performance could not anchor load math because its estimated 1RM is
     * not positive — a zero-load history set on a weighted-typed lift — so the step
     * that needed the baseline was skipped and the candidate weight was left
     * unchanged for that step.
     */
    data class BaselineSkipped(
        val liftId: LiftId,
        val slotPosition: Int,
        val historyWeight: Weight,
        val historyFailureReps: Int,
        val site: Site,
    ) : ProgressionDiagnostic {
        /** Which load-math step lost its baseline. */
        enum class Site {
            /** Covered-slot tightening in [resolveStraightSetSharedWeight]. */
            Tightening,

            /** Partial-tail anchor in [resolveStraightSetSharedWeight]. */
            PartialTailAnchor,

            /** Different-load reprojection of a slot baseline (engine path). */
            Reprojection,
        }
    }
}
