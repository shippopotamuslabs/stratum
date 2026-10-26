package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.requireOptionalRirGoal
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

data class LoggedSet(
    val id: LoggedSetId,
    val sessionId: TrainingSessionId,
    val exerciseNameRaw: String,
    val liftId: LiftId?,
    /** Equipment the set was performed with; null for legacy/unresolved rows. */
    val equipmentId: EquipmentId? = null,
    val setIndex: Int,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
    val completedAt: Instant,
    val liftContext: LoggedLiftContext,
    val isPersonalRecord: Boolean = false,
    val estimatedOneRm: Double? = null,
    /** Incomplete extra reps after [reps]; null/0 means none logged. Not used in progression. */
    val partialReps: Int? = null,
) {
    init {
        // Guardrails (STR-168): negative setIndex and out-of-range RIR are
        // impossible states. reps <= 0 and weight.amount == 0 are NOT banned:
        // zero-rep imported sets and zero-weight bodyweight sets are legitimate
        // states whose meaning is owned by the predicates below.
        require(setIndex >= 0) { "setIndex must be non-negative, was $setIndex" }
        requireOptionalRirGoal(rir)
    }

    /**
     * Partial reps as the type defines them: null when none were logged
     * (a stored null, 0, or negative all mean "none"). Producers pass raw
     * values into construction; this is the one place the normalization rule
     * lives (STR-168). A stored-field rewrite cannot do this instead — a data
     * class constructor may only declare properties, and `copy()` must be
     * able to carry the value — so the type owns the meaning as named
     * behavior, the same shape STR-166 used for `TrainingSession.endedAt`.
     */
    val normalizedPartialReps: Int?
        get() = partialReps.normalizedPartialReps()

    /**
     * Whether this set may feed insight math (1RM and friends): reps > 0 and a
     * non-negative weight, with bodyweight sets legitimately weighing 0. This
     * is the rule `ImportedHistorySanitizer` used to re-encode; the sanitizer
     * still decides which imported sets to drop, but it consumes this
     * predicate instead of restating it (STR-168).
     */
    val isInsightCreditable: Boolean
        get() = reps > 0 && weight.amount >= 0

    /**
     * The PR-eligibility inputs that are the model's own rule: the set is
     * resolved to a catalog lift and has at least one completed rep. Scoring
     * itself (e1RM math, bodyweight handling) stays in `PersonalRecordScoring`.
     */
    val isPersonalRecordEligible: Boolean
        get() = liftId != null && reps > 0
}

/** Treats null and non-positive values as "no partials logged." */
fun Int?.normalizedPartialReps(): Int? = this?.takeIf { it > 0 }
