package com.stratum.domain.workout.intensifier

import com.stratum.domain.lift.LoadType
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.myorep.MyoRepKind

/**
 * Pure placement / authoring rules for drop sets and myo-reps (intensifier sets).
 *
 * Linear scheme bans both intensifiers.
 *
 * **Drop sets** must immediately follow a Standard set (or another Drop in a multi-drop
 * chain). They cannot follow MyoRep, cannot be first, and require a weighted load type.
 *
 * **Myo activations** may sit at any index, including after drops and after other myo
 * activations (multiple clusters per lift are allowed). Live mini-sets are session-only
 * suffixes and are not authored as template slots.
 */
object IntensifierPlacementRules {

    enum class Violation {
        LinearSchemeBansIntensifiers,
        DropSetNotFollowingAnotherSet,
        /** Drop's previous set must be Standard or Drop — never MyoRep. */
        DropSetMustFollowStandardOrDrop,
        DropSetCannotBeTopSet,
        DropSetBodyweightOrAssisted,
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

        val hasDrop = sets.any { it.setType == SetType.DropSet }
        val hasMyo = sets.any { it.setType == SetType.MyoRep }

        if (scheme == ProgressionScheme.Linear && (hasDrop || hasMyo)) {
            reasons += Violation.LinearSchemeBansIntensifiers
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
                    val previous = sets.getOrNull(index - 1)?.setType
                    if (index > 0 && !isValidDropPredecessor(previous)) {
                        reasons += Violation.DropSetMustFollowStandardOrDrop
                    }
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

    /**
     * Whether a Drop set may be placed at [position] given the type of the previous set.
     * [previousType] is null when [position] is 0 (or unknown).
     */
    fun isDropAllowed(
        position: Int,
        previousType: SetType?,
        scheme: ProgressionScheme,
        loadType: LoadType,
    ): Boolean =
        scheme != ProgressionScheme.Linear &&
            loadType == LoadType.Weighted &&
            position > 0 &&
            isValidDropPredecessor(previousType)

    /**
     * Whether a Myo activation may be placed at [position] on a lift with [setCount] sets.
     * Multiple myo activations per lift are allowed (back-to-back clusters, after drops, etc.).
     */
    fun isMyoAllowed(
        position: Int,
        setCount: Int,
        scheme: ProgressionScheme,
    ): Boolean =
        scheme != ProgressionScheme.Linear &&
            setCount >= 1 &&
            position in 0 until setCount

    /** Drop may trail a Standard working set or continue a multi-drop chain. */
    fun isValidDropPredecessor(previousType: SetType?): Boolean =
        previousType == SetType.Standard || previousType == SetType.DropSet
}
