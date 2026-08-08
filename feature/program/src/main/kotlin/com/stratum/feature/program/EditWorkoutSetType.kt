package com.stratum.feature.program

import com.stratum.domain.lift.LoadType
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.intensifier.IntensifierPlacementRules
import com.stratum.domain.workout.intensifier.RestDefaultForTransition
import com.stratum.domain.workout.myorep.MyoRepKind
import com.stratum.domain.workout.myorep.MyoRepLifecycle
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetKind
import com.stratum.feature.program.ui.EditSetState
import java.util.UUID

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

internal fun EditSetKind.isMyoMatch(): Boolean = this == EditSetKind.MyoRepMatch

/**
 * True when [setIndex] is the first of a contiguous MyoRepMatch run (match activation).
 */
internal fun isEditMatchActivation(sets: List<EditSetState>, setIndex: Int): Boolean {
    if (setIndex !in sets.indices) return false
    if (sets[setIndex].setKind != EditSetKind.MyoRepMatch) return false
    val prev = sets.getOrNull(setIndex - 1) ?: return true
    return prev.setKind != EditSetKind.MyoRepMatch
}

/** Contiguous match-backoff rows immediately after a match activation. */
internal fun editMatchBackoffsAfter(sets: List<EditSetState>, activationIndex: Int): List<EditSetState> {
    if (!isEditMatchActivation(sets, activationIndex)) return emptyList()
    return sets.drop(activationIndex + 1).takeWhile { it.setKind == EditSetKind.MyoRepMatch }
}

internal fun editMatchBackoffCount(sets: List<EditSetState>, activationIndex: Int): Int =
    editMatchBackoffsAfter(sets, activationIndex).size

/**
 * Sets shown in the edit table: match backoffs are collapsed into the activation's count field.
 */
internal fun visibleEditSets(sets: List<EditSetState>): List<EditSetState> =
    sets.filterIndexed { index, _ -> !isEditMatchBackoff(sets, index) }

internal fun isEditMatchBackoff(sets: List<EditSetState>, setIndex: Int): Boolean {
    if (setIndex !in sets.indices) return false
    if (sets[setIndex].setKind != EditSetKind.MyoRepMatch) return false
    return !isEditMatchActivation(sets, setIndex)
}

/**
 * Whether [kind] can be selected for [setIndex] (0-based) on [lift].
 *
 * Match backoffs are not user-selectable (managed via backoff count on the activation).
 * Mirrors [IntensifierPlacementRules] for Standard/Drop/Myo activations.
 */
internal fun canSelectSetKind(
    lift: EditLiftState,
    setIndex: Int,
    kind: EditSetKind,
    loadType: LoadType = LoadType.Weighted,
): Boolean {
    if (kind == EditSetKind.Standard) return true
    if (setIndex !in lift.sets.indices) return false
    // Collapsed match backoffs are not re-typed via the chip.
    if (isEditMatchBackoff(lift.sets, setIndex)) return false

    return when (kind) {
        EditSetKind.DropSet -> {
            val previousType = lift.sets.getOrNull(setIndex - 1)?.setKind?.toDomainSetType()
            IntensifierPlacementRules.isDropAllowed(
                position = setIndex,
                previousType = previousType,
                scheme = lift.progressionScheme,
                loadType = loadType,
            )
        }
        EditSetKind.MyoRepFloor, EditSetKind.MyoRepMatch ->
            IntensifierPlacementRules.isMyoAllowed(
                position = setIndex,
                setCount = lift.sets.size,
                scheme = lift.progressionScheme,
            )
        EditSetKind.Standard -> true
    }
}

/**
 * Applies [newKind] to set at [setIndex], re-defaults rests, and materializes / strips
 * match backoffs when entering or leaving MyoRepMatch.
 */
internal fun applySetKindToLift(
    lift: EditLiftState,
    setIndex: Int,
    newKind: EditSetKind,
    liftDefaultRestSeconds: Int,
): EditLiftState {
    if (setIndex !in lift.sets.indices) return lift
    if (!canSelectSetKind(lift, setIndex, newKind)) return lift

    val current = lift.sets[setIndex]
    val previousKind = current.setKind

    // Leaving MM activation → drop its match backoffs first.
    var workingSets = lift.sets.toMutableList()
    if (previousKind == EditSetKind.MyoRepMatch &&
        isEditMatchActivation(workingSets, setIndex) &&
        newKind != EditSetKind.MyoRepMatch
    ) {
        val backoffs = editMatchBackoffsAfter(workingSets, setIndex)
        val backoffIds = backoffs.map { it.setId }.toSet()
        workingSets = workingSets.filterNot { it.setId in backoffIds }.toMutableList()
    }

    val targetIndex = workingSets.indexOfFirst { it.setId == current.setId }
    if (targetIndex < 0) return lift

    workingSets[targetIndex] = when (newKind) {
        EditSetKind.Standard -> workingSets[targetIndex].copy(setKind = newKind)
        EditSetKind.DropSet -> workingSets[targetIndex].copy(
            setKind = newKind,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 0,
        )
        EditSetKind.MyoRepFloor, EditSetKind.MyoRepMatch -> workingSets[targetIndex].copy(
            setKind = newKind,
            isTopSet = targetIndex == 0 || workingSets[targetIndex].isTopSet,
            rirGoal = if (workingSets[targetIndex].rirGoal <= 0) 1 else workingSets[targetIndex].rirGoal,
            repMin = if (workingSets[targetIndex].repMin <= 0 && targetIndex == 0) 8 else workingSets[targetIndex].repMin,
            repMax = if (workingSets[targetIndex].repMax <= 0 && targetIndex == 0) 12 else workingSets[targetIndex].repMax,
        )
    }

    // Entering MM (or re-asserting) with no backoffs yet → default count.
    if (newKind == EditSetKind.MyoRepMatch) {
        val existing = editMatchBackoffCount(workingSets, targetIndex)
        if (existing == 0 && previousKind != EditSetKind.MyoRepMatch) {
            workingSets = setMatchBackoffCountInSets(
                sets = workingSets,
                activationIndex = targetIndex,
                count = MyoRepLifecycle.DEFAULT_MATCH_BACKOFF_COUNT,
                liftDefaultRestSeconds = liftDefaultRestSeconds,
            ).toMutableList()
        }
    }

    return finalizeLiftSets(lift, workingSets, liftDefaultRestSeconds)
}

/**
 * Sets the number of match backoff template rows after the MM activation [setId].
 * Count is clamped to [MyoRepLifecycle.MIN_MATCH_BACKOFF_COUNT]..[MyoRepLifecycle.MAX_MATCH_BACKOFF_COUNT].
 */
internal fun applyMatchBackoffCount(
    lift: EditLiftState,
    activationSetId: String,
    count: Int,
    liftDefaultRestSeconds: Int,
): EditLiftState {
    val activationIndex = lift.sets.indexOfFirst { it.setId == activationSetId }
    if (activationIndex < 0) return lift
    if (!isEditMatchActivation(lift.sets, activationIndex)) return lift
    val sets = setMatchBackoffCountInSets(
        sets = lift.sets,
        activationIndex = activationIndex,
        count = count.coerceIn(
            MyoRepLifecycle.MIN_MATCH_BACKOFF_COUNT,
            MyoRepLifecycle.MAX_MATCH_BACKOFF_COUNT,
        ),
        liftDefaultRestSeconds = liftDefaultRestSeconds,
    )
    return finalizeLiftSets(lift, sets, liftDefaultRestSeconds)
}

/**
 * Cascade-remove targets when deleting [setId]: match activation includes its backoffs.
 */
internal fun removeSetCascadeIds(sets: List<EditSetState>, setId: String): Set<String> {
    val index = sets.indexOfFirst { it.setId == setId }
    if (index < 0) return emptySet()
    val ids = mutableSetOf(setId)
    if (isEditMatchActivation(sets, index)) {
        ids += editMatchBackoffsAfter(sets, index).map { it.setId }
    }
    return ids
}

private fun setMatchBackoffCountInSets(
    sets: List<EditSetState>,
    activationIndex: Int,
    count: Int,
    liftDefaultRestSeconds: Int,
): List<EditSetState> {
    val activation = sets[activationIndex]
    val existing = editMatchBackoffsAfter(sets, activationIndex)
    val existingIds = existing.map { it.setId }.toSet()
    val withoutBackoffs = sets.filterNot { it.setId in existingIds }.toMutableList()
    // Activation may have shifted if we filtered before it (shouldn't).
    val actIdx = withoutBackoffs.indexOfFirst { it.setId == activation.setId }
    if (actIdx < 0) return sets

    val rir = if (activation.rirGoal > 0) activation.rirGoal else 1
    val newBackoffs = (0 until count).map { i ->
        existing.getOrNull(i) ?: EditSetState(
            setId = UUID.randomUUID().toString(),
            setNumber = 0,
            repMin = 0,
            repMax = 0,
            restSeconds = liftDefaultRestSeconds,
            rirGoal = rir,
            isTopSet = false,
            setKind = EditSetKind.MyoRepMatch,
        )
    }
    withoutBackoffs.addAll(actIdx + 1, newBackoffs)
    return withoutBackoffs
}

private fun finalizeLiftSets(
    lift: EditLiftState,
    sets: List<EditSetState>,
    liftDefaultRestSeconds: Int,
): EditLiftState {
    val types = sets.map { it.setKind.toDomainSetType() }
    val kinds = sets.map { it.setKind.toMyoRepKind() }
    val rests = RestDefaultForTransition.redefaultAll(
        setTypes = types,
        liftDefaultRestSeconds = liftDefaultRestSeconds,
        myoKinds = kinds,
    )
    val withRest = sets.mapIndexed { i, s -> s.copy(restSeconds = rests[i]) }
    // Scheme normalize preserves match-backoff RIR-only shape (see applyProgressionSchemeToSets).
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

/** Rest chip uses mini styling only for floor myo, not match. */
internal fun isMiniRestKind(kind: EditSetKind): Boolean =
    kind == EditSetKind.MyoRepFloor
