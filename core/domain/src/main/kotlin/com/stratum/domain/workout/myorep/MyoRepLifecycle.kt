package com.stratum.domain.workout.myorep

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.WorkoutLiftProjection
import com.stratum.domain.workout.intensifier.RestDefaultForTransition

/**
 * Runtime state of a Myo-rep mini-generation decision.
 *
 * **RepFloor:** [activationSlotId] is the floor activation; [generatedSlots] are minis after it;
 * [lastMiniSetReps] drives the floor end-condition. [cumulativeClusterReps] is unused.
 *
 * **RepMatching:** minis belong to a **match backoff** cluster, not the activation.
 * [clusterAnchorSlotId] is the match backoff; [cumulativeClusterReps] =
 * backoff logged reps + mini reps in that cluster; stop when ≥ [activationReps].
 * [restSeconds] is unused for match minis (always [RestDefaultForTransition.MYO_MINI_DEFAULT_REST_SECONDS]).
 */
data class MyoRepBlockState(
    val workoutLiftId: WorkoutLiftId,
    val activationSlotId: SetSlotId,
    val kind: MyoRepKind,
    val activationReps: Int,
    val repFloor: Int,
    /**
     * Floor path: sum of mini reps (historical field name kept for callers).
     * Match path: prefer [cumulativeClusterReps].
     */
    val cumulativeMiniSetReps: Int,
    val lastMiniSetReps: Int?,
    val generatedSlots: List<SetSlot>,
    val liftId: LiftId,
    val activationPosition: Int,
    val restSeconds: Int = RestDefaultForTransition.MYO_MINI_DEFAULT_REST_SECONDS,
    /**
     * Match: the match-backoff slot that owns this mini chain.
     * Floor: null (parent key falls back to [activationSlotId]).
     */
    val clusterAnchorSlotId: SetSlotId? = null,
    val clusterAnchorPosition: Int = activationPosition,
    /**
     * Match: backoff performed reps + minis in this cluster.
     * Floor: unused (0).
     */
    val cumulativeClusterReps: Int = 0,
)

/**
 * Adaptive end-condition mini-set generation.
 * - Type A (rep floor): mini-sets until one hits reps ≤ floor (default 5).
 * - Type B (rep matching): mini-sets under a **match backoff** until
 *   (backoff reps + mini reps) ≥ activation reps. No mini after the activation itself;
 *   no rep floor on match minis.
 *
 * Implementations are stateless: [nextMiniSet] returns the next mini-set [SetSlot] or
 * `null` when the block's end condition is met.
 */
interface MyoRepLifecycle {
    /**
     * Type A — generate while the previous mini-set did NOT reach the rep floor.
     * [MyoRepBlockState.lastMiniSetReps] null means no mini logged yet → generate first mini.
     */
    fun nextMiniSetRepFloor(state: MyoRepBlockState): SetSlot? {
        val last = state.lastMiniSetReps
        if (last != null && last <= state.repFloor) return null
        return miniSet(state)
    }

    /**
     * Type B — generate while cluster total (backoff + minis) is below activation reps.
     * No automatic first mini: if the backoff alone already matched, returns null.
     * No rep floor — 1-rep minis are allowed until parity.
     */
    fun nextMiniSetRepMatching(state: MyoRepBlockState): SetSlot? {
        if (state.activationReps <= 0) return null
        if (state.cumulativeClusterReps >= state.activationReps) return null
        return miniSet(state)
    }

    fun nextMiniSet(state: MyoRepBlockState): SetSlot? =
        when (state.kind) {
            MyoRepKind.RepFloor -> nextMiniSetRepFloor(state)
            MyoRepKind.RepMatching -> nextMiniSetRepMatching(state)
        }

    private fun miniSet(state: MyoRepBlockState): SetSlot {
        // Match minis parent to the backoff anchor so multi-backoff clusters re-home correctly.
        val parentId = state.clusterAnchorSlotId ?: state.activationSlotId
        val parentKey = when (val id = parentId) {
            is SetSlotId.Template -> id.liftSetId.value
            is SetSlotId.Overlay -> id.instanceId.value
        }
        val parentPosition =
            if (state.clusterAnchorSlotId != null) {
                state.clusterAnchorPosition
            } else {
                state.activationPosition
            }
        val ordinal = state.generatedSlots.size + 1
        val miniRest =
            when (state.kind) {
                MyoRepKind.RepMatching ->
                    RestDefaultForTransition.MYO_MINI_DEFAULT_REST_SECONDS
                MyoRepKind.RepFloor ->
                    state.restSeconds.coerceAtLeast(0)
            }
        return SetSlot(
            // Live path may re-stamp uniqueness; parent key is required for multi-cluster order.
            slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-$parentKey-$ordinal")),
            workoutLiftId = state.workoutLiftId,
            liftId = state.liftId,
            position = parentPosition + state.generatedSlots.size + 1,
            setType = SetType.MyoRep,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 0,
            restSeconds = miniRest,
            myoRepKind = state.kind,
        )
    }

    companion object {
        const val DEFAULT_REP_FLOOR: Int = 5
        /** Default number of match backoff template sets when authoring MM. */
        const val DEFAULT_MATCH_BACKOFF_COUNT: Int = 2
        /** At least one match backoff is required for a match block. */
        const val MIN_MATCH_BACKOFF_COUNT: Int = 1
        const val MAX_MATCH_BACKOFF_COUNT: Int = 6
    }
}

/** Default [MyoRepLifecycle] implementation for DI. */
class DefaultMyoRepLifecycle @javax.inject.Inject constructor() : MyoRepLifecycle

/** Constraint violation for an invalid Myo-rep block placement. */
enum class MyoRepConstraintViolation {
    /**
     * Kept for older tests/callers: a second disconnected MyoRep block after non-myo work
     * (e.g. Myo → Standard → Myo). Prefer [MyoRepNotContiguous] for gaps inside the span.
     */
    MyoRepBlockNotOnlyOrFinalSet,
    /** @see MyoRepBlockNotOnlyOrFinalSet — historical alias used when the block was suffix-only. */
    MyoRepNotContiguousSuffix,
    /** MyoRep slots do not form a single contiguous span (activation + live minis). */
    MyoRepNotContiguous,
}

sealed interface MyoRepConstraintResult {
    data object Valid : MyoRepConstraintResult
    data class Invalid(val reasons: Set<MyoRepConstraintViolation>) : MyoRepConstraintResult
}

/**
 * Enforces that Myo-rep slots form a **single contiguous block** (activation ± match
 * backoffs ± generated minis). The block may appear at any position in the lift — top,
 * middle, or end — and straight sets may follow (Myo → Standard → Standard is valid).
 *
 * Invalid when MyoRep slots are split by non-myo sets (two separate myo blocks).
 */
class MyoRepConstraintValidator @javax.inject.Inject constructor() {
    operator fun invoke(lift: WorkoutLiftProjection): MyoRepConstraintResult {
        val slots = lift.slots
        if (slots.isEmpty()) return MyoRepConstraintResult.Valid

        val myoIndices = slots.withIndex()
            .filter { it.value.setType == SetType.MyoRep }
            .map { it.index }

        if (myoIndices.isEmpty()) return MyoRepConstraintResult.Valid

        val firstMyo = myoIndices.first()
        val lastMyo = myoIndices.last()
        val reasons = linkedSetOf<MyoRepConstraintViolation>()

        // Contiguous block: every index in [firstMyo, lastMyo] must be MyoRep.
        for (i in firstMyo..lastMyo) {
            if (slots[i].setType != SetType.MyoRep) {
                reasons += MyoRepConstraintViolation.MyoRepNotContiguous
                reasons += MyoRepConstraintViolation.MyoRepNotContiguousSuffix
                reasons += MyoRepConstraintViolation.MyoRepBlockNotOnlyOrFinalSet
                break
            }
        }

        return if (reasons.isEmpty()) {
            MyoRepConstraintResult.Valid
        } else {
            MyoRepConstraintResult.Invalid(reasons)
        }
    }
}
