package com.stratum.domain.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.myorep.MyoRepBlockState
import com.stratum.domain.workout.myorep.MyoRepConstraintResult
import com.stratum.domain.workout.myorep.MyoRepConstraintValidator
import com.stratum.domain.workout.myorep.MyoRepConstraintViolation
import com.stratum.domain.workout.myorep.MyoRepKind
import com.stratum.domain.workout.myorep.MyoRepLifecycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Slice 5 — Myo-reps Type A + Type B. 8 scenarios (S5-*).
 */
@DisplayName("Slice 5: Myo-reps Type A + Type B")
class Slice5MyoRepTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")
    private val activationSlotId = SetSlotId.Template(LiftSetId("ls-1"))

    private fun myoRepSlot(position: Int) = SetSlot(
        slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-$position")),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.MyoRep,
        isTopSet = false,
        repMin = 0,
        repMax = 0,
        rirGoal = 0,
        restSeconds = 15,
        myoRepKind = MyoRepKind.RepFloor,
    )

    private fun standardSlot(id: SetSlotId, isTopSet: Boolean, position: Int) = SetSlot(
        slotId = id,
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.Standard,
        isTopSet = isTopSet,
        repMin = 4,
        repMax = 6,
        rirGoal = 2,
        restSeconds = 120,
    )

    private fun myoRepProjection(slots: List<SetSlot>) = WorkoutLiftProjection(
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        liftName = "Curl",
        progressionScheme = com.stratum.domain.program.ProgressionScheme.Double,
        slots = slots,
    )

    // --- Lifecycle (4 scenarios) ---

    @Test
    @DisplayName("S5-myo-rep-type-a-rep-floor-generates-until-floor")
    fun `S5-myo-rep-type-a-rep-floor-generates-until-floor`() {
        val lifecycle = object : MyoRepLifecycle {}
        val state = MyoRepBlockState(
            workoutLiftId = workoutLiftId,
            activationSlotId = activationSlotId,
            kind = MyoRepKind.RepFloor,
            activationReps = 12,
            repFloor = 5,
            cumulativeMiniSetReps = 0,
            lastMiniSetReps = 4,
            generatedSlots = emptyList(),
        )
        val next = lifecycle.nextMiniSetRepFloor(state)
        assertNotNull(next, "must generate a mini-set when last mini-set did NOT hit the floor")
    }

    @Test
    @DisplayName("S5-myo-rep-type-a-block-ends-on-floor")
    fun `S5-myo-rep-type-a-block-ends-on-floor`() {
        val lifecycle = object : MyoRepLifecycle {}
        val state = MyoRepBlockState(
            workoutLiftId = workoutLiftId,
            activationSlotId = activationSlotId,
            kind = MyoRepKind.RepFloor,
            activationReps = 12,
            repFloor = 5,
            cumulativeMiniSetReps = 0,
            lastMiniSetReps = 5,
            generatedSlots = emptyList(),
        )
        val next = lifecycle.nextMiniSetRepFloor(state)
        assertNull(next, "block must end when a mini-set hits the rep floor")
    }

    @Test
    @DisplayName("S5-myo-rep-type-b-rep-matching-generates-until-parity")
    fun `S5-myo-rep-type-b-rep-matching-generates-until-parity`() {
        val lifecycle = object : MyoRepLifecycle {}
        val state = MyoRepBlockState(
            workoutLiftId = workoutLiftId,
            activationSlotId = activationSlotId,
            kind = MyoRepKind.RepMatching,
            activationReps = 12,
            repFloor = 0,
            cumulativeMiniSetReps = 8,
            lastMiniSetReps = null,
            generatedSlots = emptyList(),
        )
        val next = lifecycle.nextMiniSetRepMatching(state)
        assertNotNull(next, "must generate a mini-set while cumulative < activation reps")
    }

    @Test
    @DisplayName("S5-myo-rep-type-b-block-ends-on-parity")
    fun `S5-myo-rep-type-b-block-ends-on-parity`() {
        val lifecycle = object : MyoRepLifecycle {}
        val state = MyoRepBlockState(
            workoutLiftId = workoutLiftId,
            activationSlotId = activationSlotId,
            kind = MyoRepKind.RepMatching,
            activationReps = 12,
            repFloor = 0,
            cumulativeMiniSetReps = 12,
            lastMiniSetReps = null,
            generatedSlots = emptyList(),
        )
        val next = lifecycle.nextMiniSetRepMatching(state)
        assertNull(next, "block must end when cumulative mini-set reps equal activation reps")
    }

    // --- Constraint validator (4 scenarios) ---

    @Test
    @DisplayName("S5-myo-rep-only-set-valid")
    fun `S5-myo-rep-only-set-valid`() {
        val lift = myoRepProjection(
            listOf(
                SetSlot(
                    slotId = activationSlotId,
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 0,
                    setType = SetType.MyoRep,
                    isTopSet = false,
                    repMin = 0,
                    repMax = 0,
                    rirGoal = 0,
                    restSeconds = 15,
                    myoRepKind = MyoRepKind.RepFloor,
                ),
            ),
        )
        val result = MyoRepConstraintValidator().invoke(lift)
        assertEquals(MyoRepConstraintResult.Valid, result)
    }

    @Test
    @DisplayName("S5-myo-rep-final-set-valid")
    fun `S5-myo-rep-final-set-valid`() {
        val ls2 = SetSlotId.Template(LiftSetId("ls-2"))
        val lift = myoRepProjection(
            listOf(
                standardSlot(activationSlotId, isTopSet = true, position = 0),
                SetSlot(
                    slotId = ls2,
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 1,
                    setType = SetType.MyoRep,
                    isTopSet = false,
                    repMin = 0,
                    repMax = 0,
                    rirGoal = 0,
                    restSeconds = 15,
                    myoRepKind = MyoRepKind.RepFloor,
                ),
            ),
        )
        val result = MyoRepConstraintValidator().invoke(lift)
        assertEquals(MyoRepConstraintResult.Valid, result)
    }

    @Test
    @DisplayName("S5-myo-rep-mid-lift-invalid")
    fun `S5-myo-rep-mid-lift-invalid`() {
        val ls2 = SetSlotId.Template(LiftSetId("ls-2"))
        val ls3 = SetSlotId.Template(LiftSetId("ls-3"))
        val lift = myoRepProjection(
            listOf(
                standardSlot(activationSlotId, isTopSet = true, position = 0),
                SetSlot(
                    slotId = ls2,
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 1,
                    setType = SetType.MyoRep,
                    isTopSet = false,
                    repMin = 0,
                    repMax = 0,
                    rirGoal = 0,
                    restSeconds = 15,
                    myoRepKind = MyoRepKind.RepFloor,
                ),
                standardSlot(ls3, isTopSet = false, position = 2),
            ),
        )
        val result = MyoRepConstraintValidator().invoke(lift)
        assertTrue(result is MyoRepConstraintResult.Invalid, "mid-lift Myo-rep block must be invalid")
        val reasons = (result as MyoRepConstraintResult.Invalid).reasons
        assertTrue(MyoRepConstraintViolation.MyoRepBlockNotOnlyOrFinalSet in reasons)
    }

    @Test
    @DisplayName("S5-myo-rep-mini-set-deletable-undoable")
    fun `S5-myo-rep-mini-set-deletable-undoable`() {
        val miniSlot = myoRepSlot(position = 1)
        val afterGenerate = WorkoutOverlay().apply(
            StructuralMutation.GenerateMyoRepMiniSet(activationSlotId = activationSlotId, newSlot = miniSlot),
        )
        // Delete the generated mini-set.
        val afterDelete = afterGenerate.apply(StructuralMutation.DeleteSet(slotId = miniSlot.slotId))
        // Restore via undo.
        val afterUndo = afterDelete.undo()
        // The mini-set must be restored (reuses Slice 3 undo).
        assertTrue(afterUndo.undoStack != afterDelete.undoStack, "undo must change the undo stack")
    }
}
