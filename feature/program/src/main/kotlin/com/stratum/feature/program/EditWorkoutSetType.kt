package com.stratum.feature.program

import com.stratum.domain.lift.LoadType
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.intensifier.RestDefaultForTransition
import com.stratum.domain.workout.myorep.MyoRepKind
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetKind
import com.stratum.feature.program.ui.EditSetState

internal fun EditSetKind.toDomainSetType(): SetType =
    when (this) {
        EditSetKind.Standard -> SetType.Standard
        EditSetKind.MyoRepFloor, EditSetKind.MyoRepMatch -> SetType.MyoRep
        EditSetKind.DropSet -> SetType.DropSet
    }

internal fun EditSetKind.toMyoRepKind(): MyoRepKind? =
    when (this) {
        EditSetKind.MyoRepFloor -> MyoRepKind.RepFloor
        EditSetKind.MyoRepMatch -> MyoRepKind.RepMatching
        EditSetKind.Standard, EditSetKind.DropSet -> null
    }

internal fun domainToEditSetKind(setType: SetType, myoRepKind: MyoRepKind?): EditSetKind =
    when (setType) {
        SetType.Standard -> EditSetKind.Standard
        SetType.DropSet -> EditSetKind.DropSet
        SetType.MyoRep -> when (myoRepKind) {
            MyoRepKind.RepMatching -> EditSetKind.MyoRepMatch
            MyoRepKind.RepFloor, null -> EditSetKind.MyoRepFloor
        }
    }

internal fun EditSetKind.chipLabel(): String =
    when (this) {
        EditSetKind.Standard -> "S"
        EditSetKind.MyoRepFloor -> "M"
        EditSetKind.MyoRepMatch -> "MM"
        EditSetKind.DropSet -> "D"
    }

private fun EditSetKind.isMyo(): Boolean =
    this == EditSetKind.MyoRepFloor || this == EditSetKind.MyoRepMatch

/**
 * Whether [kind] can be selected for [setIndex] (0-based) on [lift].
 *
 * Myo activation may sit at any index (including the top set) and may be followed by
 * straight sets and/or drop sets. Only one myo activation per lift. Drop sets need an
 * anchor set above them (index > 0) and a weighted load type. Myo and drop may coexist
 * (e.g. myo top set + later drop backoffs). Linear bans both intensifiers.
 */
internal fun canSelectSetKind(
    lift: EditLiftState,
    setIndex: Int,
    kind: EditSetKind,
    loadType: LoadType = LoadType.Weighted,
): Boolean {
    if (kind == EditSetKind.Standard) return true
    if (lift.progressionScheme == ProgressionScheme.Linear) return false

    val others = lift.sets.withIndex().filter { it.index != setIndex }.map { it.value }
    val othersHaveMyo = others.any { it.setKind.isMyo() }

    return when (kind) {
        EditSetKind.DropSet ->
            loadType == LoadType.Weighted && setIndex > 0
        EditSetKind.MyoRepFloor, EditSetKind.MyoRepMatch ->
            !othersHaveMyo
        EditSetKind.Standard -> true
    }
}

/**
 * Applies [newKind] to set at [setIndex], re-defaults rests for the lift, and
 * adjusts top-set / rep range / RIR defaults for intensifiers.
 */
internal fun applySetKindToLift(
    lift: EditLiftState,
    setIndex: Int,
    newKind: EditSetKind,
    liftDefaultRestSeconds: Int,
): EditLiftState {
    if (setIndex !in lift.sets.indices) return lift
    if (!canSelectSetKind(lift, setIndex, newKind)) return lift

    val updatedSets = lift.sets.mapIndexed { index, set ->
        if (index != setIndex) return@mapIndexed set
        when (newKind) {
            EditSetKind.Standard -> set.copy(setKind = newKind)
            EditSetKind.DropSet -> set.copy(
                setKind = newKind,
                isTopSet = false,
                repMin = 0,
                repMax = 0,
                rirGoal = 0,
            )
            EditSetKind.MyoRepFloor, EditSetKind.MyoRepMatch -> set.copy(
                setKind = newKind,
                // Activation as top when it is set #1.
                isTopSet = index == 0 || set.isTopSet,
                rirGoal = if (set.rirGoal <= 0) 1 else set.rirGoal,
                repMin = if (set.repMin <= 0 && index == 0) 8 else set.repMin,
                repMax = if (set.repMax <= 0 && index == 0) 12 else set.repMax,
            )
        }
    }

    val types = updatedSets.map { it.setKind.toDomainSetType() }
    val rests = RestDefaultForTransition.redefaultAll(types, liftDefaultRestSeconds)
    val withRest = updatedSets.mapIndexed { i, s -> s.copy(restSeconds = rests[i]) }

    val normalized = applyProgressionSchemeToSets(withRest, lift.progressionScheme)
    val kindsPreserved = normalized.mapIndexed { i, s ->
        s.copy(setKind = withRest.getOrNull(i)?.setKind ?: s.setKind)
    }
    return lift.copy(sets = renumberSets(kindsPreserved))
}

internal fun showRestForSet(lift: EditLiftState, setIndex: Int): Boolean {
    val next = lift.sets.getOrNull(setIndex + 1)?.setKind?.toDomainSetType()
    return RestDefaultForTransition.showRestControl(next)
}
