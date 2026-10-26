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
 *
 * Two directions live here side by side: the `require*` functions are the
 * throwing path for trusted construction, and the `clamp*` / healed-input
 * functions are the untrusted-input path for external data (template and
 * history imports), so a rule change moves both directions together.
 */
fun requireRepBand(repMin: Int, repMax: Int) {
    require(repMin >= 0) { "repMin must be non-negative, was $repMin" }
    require(repMax >= repMin) { "repMax $repMax must be at least repMin $repMin" }
}

/** RIR goals are non-negative; see [requireRepBand] for the range evidence. */
fun requireRirGoal(rirGoal: Int) {
    require(rirGoal >= 0) { "rirGoal must be non-negative, was $rirGoal" }
}

/** The logged-set form of [requireRirGoal]: a present RIR is non-negative. */
fun requireOptionalRirGoal(rir: Int?) {
    require(rir == null || rir >= 0) { "rir must be non-negative when present, was $rir" }
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

/**
 * The full template set shape in one call — band, RIR goal, and myo-kind
 * consistency — for the shapes that carry all three ([LiftSet],
 * [com.stratum.domain.workout.TemplateSetShape],
 * [com.stratum.domain.workout.SlotShapeOverride]). Partial shapes (e.g.
 * [ProgressionRepRangePolicy.WorkingSetRepSpec], which has no RIR or kind)
 * keep using the granular rules above.
 */
fun requireSetShape(
    repMin: Int,
    repMax: Int,
    rirGoal: Int,
    setType: SetType,
    myoRepKind: MyoRepKind?,
) {
    requireRepBand(repMin, repMax)
    requireRirGoal(rirGoal)
    requireMyoRepKindConsistency(setType, myoRepKind)
}

/** Untrusted-input band healing: the min floors at 0 and the max rises to meet it. */
fun clampRepBand(repMin: Int, repMax: Int): Pair<Int, Int> {
    val min = repMin.coerceAtLeast(0)
    return min to repMax.coerceAtLeast(min)
}

/** Untrusted-input RIR healing: negative goals floor at 0. */
fun clampRirGoal(rirGoal: Int): Int = rirGoal.coerceAtLeast(0)

/** Untrusted-input rest healing: a missing or non-positive rest takes [fallback]. */
fun healedRestSeconds(restSeconds: Int?, fallback: Int): Int =
    restSeconds?.takeIf { it > 0 } ?: fallback
