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
 * Runtime state of a Myo-rep block.
 *
 * [restSeconds] is the programmed rest on the activation set (template or override).
 * Minis inherit it so the live rest timer matches Edit Workout.
 */
data class MyoRepBlockState(
    val workoutLiftId: WorkoutLiftId,
    val activationSlotId: SetSlotId,
    val kind: MyoRepKind,
    val activationReps: Int,
    val repFloor: Int,
    val cumulativeMiniSetReps: Int,
    val lastMiniSetReps: Int?,
    val generatedSlots: List<SetSlot>,
    val liftId: LiftId,
    val activationPosition: Int,
    val restSeconds: Int = RestDefaultForTransition.MYO_MINI_DEFAULT_REST_SECONDS,
)

/**
 * Adaptive end-condition mini-set generation.
 * - Type A (rep floor): mini-sets until one hits reps ≤ floor (default 5).
 * - Type B (rep matching): mini-sets until cumulative mini-set reps ≥ activation reps.
 *
 * Implementations are stateless: [nextMiniSet] returns the next mini-set [SetSlot] or
 * `null` when the block's end condition is met. After activation completes, callers
 * should generate the first mini even when activation reps ≤ floor.
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
     * Type B — generate while cumulative mini-set reps are below activation reps.
     * Always generate the first mini after activation; stop when cumulative ≥ activation
     * after at least one mini exists.
     */
    fun nextMiniSetRepMatching(state: MyoRepBlockState): SetSlot? {
        if (state.generatedSlots.isEmpty()) return miniSet(state)
        if (state.cumulativeMiniSetReps >= state.activationReps) return null
        return miniSet(state)
    }

    fun nextMiniSet(state: MyoRepBlockState): SetSlot? =
        when (state.kind) {
            MyoRepKind.RepFloor -> nextMiniSetRepFloor(state)
            MyoRepKind.RepMatching -> nextMiniSetRepMatching(state)
        }

    private fun miniSet(state: MyoRepBlockState): SetSlot {
        val activationKey = when (val id = state.activationSlotId) {
            is SetSlotId.Template -> id.liftSetId.value
            is SetSlotId.Overlay -> id.instanceId.value
        }
        val ordinal = state.generatedSlots.size + 1
        return SetSlot(
            // Live path may re-stamp uniqueness; activation key is required for multi-cluster order.
            slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-$activationKey-$ordinal")),
            workoutLiftId = state.workoutLiftId,
            liftId = state.liftId,
            position = state.activationPosition + state.generatedSlots.size + 1,
            setType = SetType.MyoRep,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 0,
            // Inherit programmed activation rest (Edit Workout / overrides); default is 15s.
            restSeconds = state.restSeconds.coerceAtLeast(0),
            myoRepKind = state.kind,
        )
    }

    companion object {
        const val DEFAULT_REP_FLOOR: Int = 5
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
 * Enforces that Myo-rep slots form a **single contiguous block** (activation ± generated
 * minis). The block may appear at any position in the lift — top, middle, or end — and
 * straight sets may follow (Myo → Standard → Standard is valid).
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
