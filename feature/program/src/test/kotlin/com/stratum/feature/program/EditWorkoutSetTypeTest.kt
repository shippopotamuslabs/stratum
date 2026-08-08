package com.stratum.feature.program

import com.stratum.domain.lift.LoadType
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.workout.intensifier.RestDefaultForTransition
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetKind
import com.stratum.feature.program.ui.EditSetState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Edit workout set-kind placement")
class EditWorkoutSetTypeTest {

    @Test
    fun `myo allowed on top set when double progression`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.Standard, EditSetKind.Standard, EditSetKind.Standard),
        )
        assertTrue(canSelectSetKind(lift, setIndex = 0, kind = EditSetKind.MyoRepFloor))
        assertTrue(canSelectSetKind(lift, setIndex = 0, kind = EditSetKind.MyoRepMatch))
        assertTrue(canSelectSetKind(lift, setIndex = 1, kind = EditSetKind.MyoRepFloor))
        assertTrue(canSelectSetKind(lift, setIndex = 2, kind = EditSetKind.MyoRepFloor))
    }

    @Test
    fun `myo banned on linear`() {
        val lift = lift(
            scheme = ProgressionScheme.Linear,
            kinds = listOf(EditSetKind.Standard, EditSetKind.Standard),
        )
        assertFalse(canSelectSetKind(lift, setIndex = 0, kind = EditSetKind.MyoRepFloor))
        assertFalse(canSelectSetKind(lift, setIndex = 1, kind = EditSetKind.DropSet))
    }

    @Test
    fun `myo and drop allowed on isolation double`() {
        // Accessories always Double (D8); intensifiers must not be gated on isCompound toggle.
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.Standard, EditSetKind.Standard, EditSetKind.Standard),
            isCompound = false,
        )
        assertTrue(canSelectSetKind(lift, setIndex = 0, kind = EditSetKind.MyoRepFloor))
        assertTrue(canSelectSetKind(lift, setIndex = 1, kind = EditSetKind.DropSet))
        assertTrue(canSelectSetKind(lift, setIndex = 2, kind = EditSetKind.MyoRepMatch))
    }

    @Test
    fun `multiple myo activations per lift are allowed`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.MyoRepFloor, EditSetKind.Standard, EditSetKind.Standard),
        )
        assertTrue(canSelectSetKind(lift, setIndex = 1, kind = EditSetKind.MyoRepMatch))
        assertTrue(canSelectSetKind(lift, setIndex = 2, kind = EditSetKind.MyoRepFloor))
        // Switching kind on the existing myo row is still allowed.
        assertTrue(canSelectSetKind(lift, setIndex = 0, kind = EditSetKind.MyoRepMatch))
    }

    @Test
    fun `drop after standard allowed including after myo then standard`() {
        // Myo top set + straight work; drop needs a Standard (or Drop) predecessor.
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(
                EditSetKind.MyoRepFloor,
                EditSetKind.Standard,
                EditSetKind.Standard,
                EditSetKind.Standard,
            ),
        )
        assertFalse(canSelectSetKind(lift, setIndex = 0, kind = EditSetKind.DropSet))
        // Index 1 follows Myo → not allowed.
        assertFalse(canSelectSetKind(lift, setIndex = 1, kind = EditSetKind.DropSet))
        // Index 2+ follow Standard → allowed.
        assertTrue(canSelectSetKind(lift, setIndex = 2, kind = EditSetKind.DropSet))
        assertTrue(canSelectSetKind(lift, setIndex = 3, kind = EditSetKind.DropSet))
    }

    @Test
    fun `drop immediately after myo banned`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.MyoRepFloor, EditSetKind.Standard, EditSetKind.Standard),
        )
        assertFalse(canSelectSetKind(lift, setIndex = 1, kind = EditSetKind.DropSet))
    }

    @Test
    fun `drop chain after standard allowed`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(
                EditSetKind.Standard,
                EditSetKind.DropSet,
                EditSetKind.Standard,
            ),
        )
        // Continue multi-drop after existing Drop.
        assertTrue(canSelectSetKind(lift, setIndex = 2, kind = EditSetKind.DropSet))
    }

    @Test
    fun `myo allowed when lift already has drop sets`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.Standard, EditSetKind.DropSet, EditSetKind.Standard),
        )
        assertTrue(canSelectSetKind(lift, setIndex = 0, kind = EditSetKind.MyoRepFloor))
        assertTrue(canSelectSetKind(lift, setIndex = 1, kind = EditSetKind.MyoRepMatch))
        assertTrue(canSelectSetKind(lift, setIndex = 2, kind = EditSetKind.MyoRepMatch))
    }

    @Test
    fun `drop banned on first set and bodyweight`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.Standard, EditSetKind.Standard),
        )
        assertFalse(canSelectSetKind(lift, setIndex = 0, kind = EditSetKind.DropSet))
        assertTrue(canSelectSetKind(lift, setIndex = 1, kind = EditSetKind.DropSet))
        assertFalse(
            canSelectSetKind(
                lift,
                setIndex = 1,
                kind = EditSetKind.DropSet,
                loadType = LoadType.Bodyweight,
            ),
        )
    }

    @Test
    fun `applying myo kind defaults rest to 15 seconds`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.Standard, EditSetKind.Standard, EditSetKind.Standard),
        )
        val updated = applySetKindToLift(
            lift = lift,
            setIndex = 0,
            newKind = EditSetKind.MyoRepFloor,
            liftDefaultRestSeconds = 120,
        )
        assertEquals(EditSetKind.MyoRepFloor, updated.sets[0].setKind)
        assertEquals(
            RestDefaultForTransition.MYO_MINI_DEFAULT_REST_SECONDS,
            updated.sets[0].restSeconds,
        )
        // Non-myo rows keep the lift default rest.
        assertEquals(120, updated.sets[1].restSeconds)
        assertEquals(120, updated.sets[2].restSeconds)
    }

    @Test
    fun `applying myo match kind uses main rest and default backoffs`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.Standard, EditSetKind.Standard),
        )
        val updated = applySetKindToLift(
            lift = lift,
            setIndex = 0,
            newKind = EditSetKind.MyoRepMatch,
            liftDefaultRestSeconds = 180,
        )
        assertEquals(EditSetKind.MyoRepMatch, updated.sets[0].setKind)
        // Activation + 2 default match backoffs + original remaining standard
        assertEquals(1 + 2 + 1, updated.sets.size)
        assertEquals(180, updated.sets[0].restSeconds, "match uses main rest")
        assertTrue(isEditMatchActivation(updated.sets, 0))
        assertEquals(2, editMatchBackoffCount(updated.sets, 0))
        assertTrue(isEditMatchBackoff(updated.sets, 1))
        assertTrue(isEditMatchBackoff(updated.sets, 2))
        // Original second standard shifted after match backoffs
        assertEquals(EditSetKind.Standard, updated.sets[3].setKind)
    }

    @Test
    fun `match backoff count can increase and decrease`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.Standard),
        )
        val withMatch = applySetKindToLift(
            lift = lift,
            setIndex = 0,
            newKind = EditSetKind.MyoRepMatch,
            liftDefaultRestSeconds = 120,
        )
        val three = applyMatchBackoffCount(
            lift = withMatch,
            activationSetId = withMatch.sets[0].setId,
            count = 3,
            liftDefaultRestSeconds = 120,
        )
        assertEquals(3, editMatchBackoffCount(three.sets, 0))
        val one = applyMatchBackoffCount(
            lift = three,
            activationSetId = three.sets[0].setId,
            count = 1,
            liftDefaultRestSeconds = 120,
        )
        assertEquals(1, editMatchBackoffCount(one.sets, 0))
        assertEquals(2, one.sets.size) // activation + 1 backoff
        // Min clamp is 1 — cannot drop to zero match backoffs.
        val zeroAttempt = applyMatchBackoffCount(
            lift = one,
            activationSetId = one.sets[0].setId,
            count = 0,
            liftDefaultRestSeconds = 120,
        )
        assertEquals(1, editMatchBackoffCount(zeroAttempt.sets, 0))
    }

    @Test
    fun `demoting match strips backoffs`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.Standard, EditSetKind.Standard),
        )
        val withMatch = applySetKindToLift(
            lift = lift,
            setIndex = 0,
            newKind = EditSetKind.MyoRepMatch,
            liftDefaultRestSeconds = 120,
        )
        val demoted = applySetKindToLift(
            lift = withMatch,
            setIndex = 0,
            newKind = EditSetKind.Standard,
            liftDefaultRestSeconds = 120,
        )
        assertEquals(EditSetKind.Standard, demoted.sets[0].setKind)
        assertTrue(demoted.sets.none { isEditMatchBackoff(demoted.sets, demoted.sets.indexOf(it)) })
        assertEquals(2, demoted.sets.size)
    }

    @Test
    fun `remove match activation cascades backoffs`() {
        val lift = lift(
            scheme = ProgressionScheme.Double,
            kinds = listOf(EditSetKind.Standard, EditSetKind.Standard),
        )
        val withMatch = applySetKindToLift(
            lift = lift,
            setIndex = 0,
            newKind = EditSetKind.MyoRepMatch,
            liftDefaultRestSeconds = 120,
        )
        val cascade = removeSetCascadeIds(withMatch.sets, withMatch.sets[0].setId)
        assertEquals(3, cascade.size) // activation + 2 backoffs
    }

    private fun lift(
        scheme: ProgressionScheme,
        kinds: List<EditSetKind>,
        isCompound: Boolean = true,
    ): EditLiftState =
        EditLiftState(
            workoutLiftId = "wl-1",
            liftId = "lift-1",
            name = "Bench",
            position = 0,
            isCompound = isCompound,
            progressionScheme = scheme,
            sets =
                kinds.mapIndexed { index, kind ->
                    EditSetState(
                        setId = "s-$index",
                        setNumber = index + 1,
                        repMin = 8,
                        repMax = 12,
                        restSeconds = 120,
                        rirGoal = 2,
                        isTopSet = index == 0,
                        setKind = kind,
                    )
                },
        )
}
