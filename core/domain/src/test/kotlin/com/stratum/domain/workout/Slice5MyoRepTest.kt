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
 * Slice 5 — Myo-reps Type A + Type B.
 * Floor: minis until last ≤ floor.
 * Match: per-backoff cluster total (backoff + minis) ≥ activation reps; no mini on activation.
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

    private fun floorState(
        lastMiniSetReps: Int?,
        generated: List<SetSlot> = emptyList(),
        repFloor: Int = 5,
    ) = MyoRepBlockState(
        workoutLiftId = workoutLiftId,
        activationSlotId = activationSlotId,
        kind = MyoRepKind.RepFloor,
        activationReps = 12,
        repFloor = repFloor,
        cumulativeMiniSetReps = 0,
        lastMiniSetReps = lastMiniSetReps,
        generatedSlots = generated,
        liftId = liftId,
        activationPosition = 0,
    )

    private fun matchState(
        clusterTotal: Int,
        generated: List<SetSlot>,
        activationReps: Int = 12,
        clusterAnchorSlotId: SetSlotId = SetSlotId.Template(LiftSetId("ls-bo-1")),
        clusterAnchorPosition: Int = 1,
    ) = MyoRepBlockState(
        workoutLiftId = workoutLiftId,
        activationSlotId = activationSlotId,
        kind = MyoRepKind.RepMatching,
        activationReps = activationReps,
        repFloor = 0,
        cumulativeMiniSetReps = 0,
        lastMiniSetReps = null,
        generatedSlots = generated,
        liftId = liftId,
        activationPosition = 0,
        clusterAnchorSlotId = clusterAnchorSlotId,
        clusterAnchorPosition = clusterAnchorPosition,
        cumulativeClusterReps = clusterTotal,
    )

    @Test
    @DisplayName("S5-myo-rep-type-a-rep-floor-generates-until-floor")
    fun `S5-myo-rep-type-a-rep-floor-generates-until-floor`() {
        val lifecycle = object : MyoRepLifecycle {}
        // 6 > floor 5 → still generating
        val next = lifecycle.nextMiniSetRepFloor(floorState(lastMiniSetReps = 6))
        assertNotNull(next, "must generate a mini-set when last mini-set is above the floor")
    }

    @Test
    @DisplayName("S5-myo-rep-type-a-block-ends-on-floor")
    fun `S5-myo-rep-type-a-block-ends-on-floor`() {
        val lifecycle = object : MyoRepLifecycle {}
        assertNull(lifecycle.nextMiniSetRepFloor(floorState(lastMiniSetReps = 5)), "≤ floor ends block")
        assertNull(lifecycle.nextMiniSetRepFloor(floorState(lastMiniSetReps = 4)), "below floor ends block")
    }

    @Test
    @DisplayName("S5-myo-rep-type-a-first-mini-after-activation")
    fun `S5-myo-rep-type-a-first-mini-after-activation`() {
        val lifecycle = object : MyoRepLifecycle {}
        val next = lifecycle.nextMiniSetRepFloor(floorState(lastMiniSetReps = null))
        assertNotNull(next, "must spawn first mini after activation even if activation was hard")
    }

    @Test
    @DisplayName("S5-myo-mini-inherits-activation-rest-seconds")
    fun `S5-myo-mini-inherits-activation-rest-seconds`() {
        val lifecycle = object : MyoRepLifecycle {}
        val next = lifecycle.nextMiniSetRepFloor(
            floorState(lastMiniSetReps = null).copy(restSeconds = 30),
        )
        assertNotNull(next)
        assertEquals(30, next!!.restSeconds)
    }

    @Test
    @DisplayName("S5-myo-rep-type-b-rep-matching-generates-until-parity")
    fun `S5-myo-rep-type-b-rep-matching-generates-until-parity`() {
        val lifecycle = object : MyoRepLifecycle {}
        // Backoff did 5 + one mini 3 = 8 < 12 → still short
        val next = lifecycle.nextMiniSetRepMatching(
            matchState(clusterTotal = 8, generated = listOf(myoRepSlot(1))),
        )
        assertNotNull(next, "must generate a mini while cluster total < activation reps")
    }

    @Test
    @DisplayName("S5-myo-rep-type-b-block-ends-on-parity")
    fun `S5-myo-rep-type-b-block-ends-on-parity`() {
        val lifecycle = object : MyoRepLifecycle {}
        val next = lifecycle.nextMiniSetRepMatching(
            matchState(clusterTotal = 12, generated = listOf(myoRepSlot(1), myoRepSlot(2))),
        )
        assertNull(next, "block must end when cluster total >= activation reps")
    }

    @Test
    @DisplayName("S5-myo-rep-type-b-overshoot-ends")
    fun `S5-myo-rep-type-b-overshoot-ends`() {
        val lifecycle = object : MyoRepLifecycle {}
        val next = lifecycle.nextMiniSetRepMatching(
            matchState(clusterTotal = 15, generated = listOf(myoRepSlot(1))),
        )
        assertNull(next, "overshoot (>=) ends the block")
    }

    @Test
    @DisplayName("S5-myo-rep-type-b-backoff-alone-hits-target-no-mini")
    fun `S5-myo-rep-type-b-backoff-alone-hits-target-no-mini`() {
        val lifecycle = object : MyoRepLifecycle {}
        val next = lifecycle.nextMiniSetRepMatching(
            matchState(clusterTotal = 12, generated = emptyList()),
        )
        assertNull(next, "backoff alone matching activation must not spawn a mini")
    }

    @Test
    @DisplayName("S5-myo-rep-type-b-backoff-short-spawns-first-mini")
    fun `S5-myo-rep-type-b-backoff-short-spawns-first-mini`() {
        val lifecycle = object : MyoRepLifecycle {}
        val next = lifecycle.nextMiniSetRepMatching(
            matchState(clusterTotal = 7, generated = emptyList(), activationReps = 12),
        )
        assertNotNull(next, "short backoff must spawn first mini")
        // Parent key is the match backoff, not the activation.
        val id = (next!!.slotId as SetSlotId.Overlay).instanceId.value
        assertTrue(id.startsWith("mini-ls-bo-1-"), "mini parents to backoff: $id")
    }

    @Test
    @DisplayName("S5-myo-rep-type-b-one-rep-minis-allowed")
    fun `S5-myo-rep-type-b-one-rep-minis-allowed`() {
        val lifecycle = object : MyoRepLifecycle {}
        // 11 of 12 — still short by 1; no floor end-condition on match.
        val next = lifecycle.nextMiniSetRepMatching(
            matchState(clusterTotal = 11, generated = listOf(myoRepSlot(1), myoRepSlot(2))),
        )
        assertNotNull(next, "match has no rep floor — continue until parity")
    }

    @Test
    @DisplayName("S5-myo-rep-type-b-match-minis-use-15s-rest")
    fun `S5-myo-rep-type-b-match-minis-use-15s-rest`() {
        val lifecycle = object : MyoRepLifecycle {}
        val next = lifecycle.nextMiniSetRepMatching(
            matchState(clusterTotal = 5, generated = emptyList()).copy(restSeconds = 180),
        )
        assertNotNull(next)
        assertEquals(15, next!!.restSeconds)
    }

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
                    isTopSet = true,
                    repMin = 8,
                    repMax = 12,
                    rirGoal = 1,
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
    @DisplayName("S5-myo-rep-multi-mini-suffix-valid")
    fun `S5-myo-rep-multi-mini-suffix-valid`() {
        val activation = SetSlot(
            slotId = activationSlotId,
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = 0,
            setType = SetType.MyoRep,
            isTopSet = true,
            repMin = 10,
            repMax = 15,
            rirGoal = 1,
            restSeconds = 120,
            myoRepKind = MyoRepKind.RepFloor,
        )
        val lift = myoRepProjection(listOf(activation, myoRepSlot(1), myoRepSlot(2)))
        assertEquals(MyoRepConstraintResult.Valid, MyoRepConstraintValidator().invoke(lift))
    }

    @Test
    @DisplayName("S5-myo-rep-then-straight-sets-valid")
    fun `S5-myo-rep-then-straight-sets-valid`() {
        // Masochist pattern: Myo activation first, then two straight sets.
        val ls2 = SetSlotId.Template(LiftSetId("ls-2"))
        val ls3 = SetSlotId.Template(LiftSetId("ls-3"))
        val lift = myoRepProjection(
            listOf(
                SetSlot(
                    slotId = activationSlotId,
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 0,
                    setType = SetType.MyoRep,
                    isTopSet = true,
                    repMin = 8,
                    repMax = 12,
                    rirGoal = 1,
                    restSeconds = 120,
                    myoRepKind = MyoRepKind.RepFloor,
                ),
                standardSlot(ls2, isTopSet = false, position = 1),
                standardSlot(ls3, isTopSet = false, position = 2),
            ),
        )
        assertEquals(MyoRepConstraintResult.Valid, MyoRepConstraintValidator().invoke(lift))
    }

    @Test
    @DisplayName("S5-myo-rep-mid-lift-with-following-standards-valid")
    fun `S5-myo-rep-mid-lift-with-following-standards-valid`() {
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
                    repMin = 8,
                    repMax = 12,
                    rirGoal = 1,
                    restSeconds = 15,
                    myoRepKind = MyoRepKind.RepFloor,
                ),
                standardSlot(ls3, isTopSet = false, position = 2),
            ),
        )
        assertEquals(MyoRepConstraintResult.Valid, MyoRepConstraintValidator().invoke(lift))
    }

    @Test
    @DisplayName("S5-myo-rep-split-blocks-invalid")
    fun `S5-myo-rep-split-blocks-invalid`() {
        val ls2 = SetSlotId.Template(LiftSetId("ls-2"))
        val ls3 = SetSlotId.Template(LiftSetId("ls-3"))
        val lift = myoRepProjection(
            listOf(
                SetSlot(
                    slotId = activationSlotId,
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 0,
                    setType = SetType.MyoRep,
                    isTopSet = true,
                    repMin = 8,
                    repMax = 12,
                    rirGoal = 1,
                    restSeconds = 120,
                    myoRepKind = MyoRepKind.RepFloor,
                ),
                standardSlot(ls2, isTopSet = false, position = 1),
                SetSlot(
                    slotId = ls3,
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 2,
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
        assertTrue(result is MyoRepConstraintResult.Invalid, "split Myo blocks must be invalid")
        val reasons = (result as MyoRepConstraintResult.Invalid).reasons
        assertTrue(MyoRepConstraintViolation.MyoRepNotContiguous in reasons)
    }

    @Test
    @DisplayName("S5-myo-rep-mini-set-deletable-undoable")
    fun `S5-myo-rep-mini-set-deletable-undoable`() {
        val miniSlot = myoRepSlot(position = 1)
        val afterGenerate = WorkoutOverlay().apply(
            StructuralMutation.GenerateMyoRepMiniSet(activationSlotId = activationSlotId, newSlot = miniSlot),
        )
        val afterDelete = afterGenerate.apply(StructuralMutation.DeleteSet(slotId = miniSlot.slotId))
        val afterUndo = afterDelete.undo()
        assertTrue(afterUndo.undoStack != afterDelete.undoStack, "undo must change the undo stack")
    }
}
