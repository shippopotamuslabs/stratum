package com.stratum.domain.workout.intensifier

import com.stratum.domain.lift.LoadType
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.myorep.MyoRepKind

/**
 * Pure placement / authoring rules for drop sets and myo-reps (intensifier sets).
 *
 * Linear scheme bans both intensifiers. Drops cannot be top/first and require a weighted
 * load type (they need an anchor set above them). Myo activation may sit at any template
 * position (including the top set) and may be followed by straight sets and/or drop sets;
 * only **one** MyoRep activation is allowed per lift. Mini-sets are live-only suffixes of
 * the activation and are not authored as template slots.
 *
 * Myo and drop may coexist on the same lift (e.g. myo top set + later drop backoffs).
 */
object IntensifierPlacementRules {

    enum class Violation {
        LinearSchemeBansIntensifiers,
        DropSetNotFollowingAnotherSet,
        DropSetCannotBeTopSet,
        DropSetBodyweightOrAssisted,
        MyoMultipleActivations,
        MyoKindRequired,
        MyoKindMustBeNullForNonMyo,
    }

    data class SetSpec(
        val setType: SetType,
        val isTopSet: Boolean,
        val myoRepKind: MyoRepKind? = null,
    )

    /**
     * Validates template-level placement for [sets] under [scheme] / [loadType].
     * [sets] are ordered by position (0-based). Generated live minis are not included.
     */
    fun validateTemplate(
        sets: List<SetSpec>,
        scheme: ProgressionScheme,
        loadType: LoadType = LoadType.Weighted,
    ): Set<Violation> {
        if (sets.isEmpty()) return emptySet()
        val reasons = linkedSetOf<Violation>()

        val myoCount = sets.count { it.setType == SetType.MyoRep }
        val hasDrop = sets.any { it.setType == SetType.DropSet }
        val hasMyo = myoCount > 0

        if (scheme == ProgressionScheme.Linear && (hasDrop || hasMyo)) {
            reasons += Violation.LinearSchemeBansIntensifiers
        }
        if (myoCount > 1) {
            reasons += Violation.MyoMultipleActivations
        }
        if (hasDrop && (loadType == LoadType.Bodyweight || loadType == LoadType.Assisted)) {
            reasons += Violation.DropSetBodyweightOrAssisted
        }

        sets.forEachIndexed { index, set ->
            when (set.setType) {
                SetType.DropSet -> {
                    if (index == 0) reasons += Violation.DropSetNotFollowingAnotherSet
                    if (set.isTopSet) reasons += Violation.DropSetCannotBeTopSet
                    if (set.myoRepKind != null) reasons += Violation.MyoKindMustBeNullForNonMyo
                }
                SetType.MyoRep -> {
                    if (set.myoRepKind == null) reasons += Violation.MyoKindRequired
                }
                SetType.Standard -> {
                    if (set.myoRepKind != null) reasons += Violation.MyoKindMustBeNullForNonMyo
                }
            }
        }
        return reasons
    }

    fun isDropAllowed(
        position: Int,
        scheme: ProgressionScheme,
        loadType: LoadType,
    ): Boolean =
        scheme != ProgressionScheme.Linear &&
            loadType == LoadType.Weighted &&
            position > 0

    /**
     * Whether a Myo activation may be placed at [position] on a lift with [setCount] sets.
     * Position is unrestricted (top / mid / last) as long as the lift does not already have
     * a myo at another position and is not on Linear.
     */
    fun isMyoAllowed(
        position: Int,
        setCount: Int,
        scheme: ProgressionScheme,
        liftAlreadyHasMyoAtOtherPosition: Boolean = false,
    ): Boolean =
        scheme != ProgressionScheme.Linear &&
            !liftAlreadyHasMyoAtOtherPosition &&
            setCount >= 1 &&
            position in 0 until setCount
}
