package com.stratum.domain.program

import com.stratum.domain.workout.myorep.MyoRepKind

/**
 * Shared set-shape rules (STR-168): the one encoding of what a constructible set
 * shape is, cited by the STR-164 inventory rows for [LiftSet],
 * [ProgressionRepRangePolicy.WorkingSetRepSpec],
 * [com.stratum.domain.workout.TemplateSetShape] and
 * [com.stratum.domain.workout.SlotShapeOverride]. Producers construct through
 * these rules; nobody re-derives them.
 *
 * The rep band is `0 <= repMin <= repMax`. The `0/0` shape is legitimate: it is
 * the RIR-only back-off shape under the Double scheme (see
 * [ProgressionRepRangePolicy]), so no `repMin > 0` rule exists here.
 *
 * The RIR range's evidenced bound is non-negativity: generation clamps RIR
 * goals at 0 (see `clampBackOffRir`) and every consumer coerces with
 * `coerceAtLeast(0)`, while no upper bound exists anywhere in the codebase —
 * imported templates carry arbitrary RIR goals — so none is invented here.
 */
fun requireRepBand(repMin: Int, repMax: Int) {
    require(repMin >= 0) { "repMin must be non-negative, was $repMin" }
    require(repMax >= repMin) { "repMax $repMax must be at least repMin $repMin" }
}

/** RIR goals are non-negative; see [requireRepBand] for the range evidence. */
fun requireRirGoal(rirGoal: Int) {
    require(rirGoal >= 0) { "rirGoal must be non-negative, was $rirGoal" }
}

/**
 * A myo-rep kind is present exactly when the set type is [SetType.MyoRep]:
 * a MyoRep set without a kind has no lifecycle interpretation, and a kind on a
 * Standard/Drop set is a stale shape left behind by a set-type edit.
 */
fun requireMyoRepKindConsistency(setType: SetType, myoRepKind: MyoRepKind?) {
    require((setType == SetType.MyoRep) == (myoRepKind != null)) {
        "myoRepKind must be present exactly when setType is MyoRep, was setType=$setType myoRepKind=$myoRepKind"
    }
}
