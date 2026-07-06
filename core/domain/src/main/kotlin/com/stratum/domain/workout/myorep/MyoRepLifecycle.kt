package com.stratum.domain.workout.myorep

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId

/**
 * Runtime state of a Myo-rep block (D6).
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
)

/**
 * Adaptive end-condition mini-set generation (D6).
 * - Type A (rep floor): generate until a mini-set hits the rep floor.
 * - Type B (rep matching): generate until cumulative mini-set reps equal activation reps.
 *
 * Implementations are stateless: [nextMiniSetRepFloor] / [nextMiniSetRepMatching] return
 * the next mini-set [SetSlot] or `null` when the block's end condition is met.
 */
interface MyoRepLifecycle {
    /**
     * Type A — generate the next mini-set while the previous mini-set did NOT reach the
     * rep floor. Returns `null` once a mini-set hits the floor (block ends).
     */
    fun nextMiniSetRepFloor(state: MyoRepBlockState): SetSlot? {
        val last = state.lastMiniSetReps ?: return null
        if (last >= state.repFloor) return null
        return miniSet(state)
    }

    /**
     * Type B — generate the next mini-set while cumulative mini-set reps are below the
     * activation-set reps. Returns `null` once parity is reached (block ends).
     */
    fun nextMiniSetRepMatching(state: MyoRepBlockState): SetSlot? {
        if (state.cumulativeMiniSetReps >= state.activationReps) return null
        return miniSet(state)
    }

    private fun miniSet(state: MyoRepBlockState): SetSlot =
        SetSlot(
            slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-${state.generatedSlots.size + 1}")),
            workoutLiftId = state.workoutLiftId,
            // MyoRepBlockState carries no liftId; the mini-set inherits the activation
            // slot's lift at the merge layer. A neutral id satisfies the non-blank invariant.
            liftId = LiftId(MYO_REP_LIFT_SENTINEL),
            position = state.generatedSlots.size + 1,
            setType = SetType.MyoRep,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 0,
            restSeconds = MYO_REP_MINI_SET_REST_SECONDS,
            myoRepKind = state.kind,
        )

    private companion object {
        const val MYO_REP_MINI_SET_REST_SECONDS = 15
        const val MYO_REP_LIFT_SENTINEL = "myo-rep-mini-set"
    }
}

/** Constraint violation for an invalid Myo-rep block placement (D6). */
enum class MyoRepConstraintViolation { MyoRepBlockNotOnlyOrFinalSet }

sealed interface MyoRepConstraintResult {
    data object Valid : MyoRepConstraintResult
    data class Invalid(val reasons: Set<MyoRepConstraintViolation>) : MyoRepConstraintResult
}

/**
 * Enforces the only-set / final-set constraint (D6). A Myo-rep block must be the ONLY
 * set or the FINAL set of its lift; a Myo-rep slot anywhere but the last position is
 * invalid.
 */
class MyoRepConstraintValidator @javax.inject.Inject constructor() {
    operator fun invoke(lift: com.stratum.domain.workout.WorkoutLiftProjection): MyoRepConstraintResult {
        val slots = lift.slots
        val lastIndex = slots.lastIndex
        val misplaced = slots.withIndex().any { (index, slot) ->
            slot.setType == SetType.MyoRep && index != lastIndex
        }
        return if (misplaced) {
            MyoRepConstraintResult.Invalid(setOf(MyoRepConstraintViolation.MyoRepBlockNotOnlyOrFinalSet))
        } else {
            MyoRepConstraintResult.Valid
        }
    }
}
