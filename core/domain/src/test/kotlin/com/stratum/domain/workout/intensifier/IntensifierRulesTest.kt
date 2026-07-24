package com.stratum.domain.workout.intensifier

import com.stratum.domain.lift.LoadType
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.myorep.MyoRepKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Intensifier placement + rest defaults")
class IntensifierRulesTest {

    @Test
    fun `linear bans drop and myo`() {
        val sets = listOf(
            IntensifierPlacementRules.SetSpec(SetType.Standard, isTopSet = true),
            IntensifierPlacementRules.SetSpec(SetType.DropSet, isTopSet = false),
        )
        val reasons = IntensifierPlacementRules.validateTemplate(
            sets = sets,
            scheme = ProgressionScheme.Linear,
        )
        assertTrue(IntensifierPlacementRules.Violation.LinearSchemeBansIntensifiers in reasons)
    }

    @Test
    fun `myo top set with later drops is valid`() {
        val sets = listOf(
            IntensifierPlacementRules.SetSpec(
                SetType.MyoRep,
                isTopSet = true,
                myoRepKind = MyoRepKind.RepFloor,
            ),
            IntensifierPlacementRules.SetSpec(SetType.Standard, isTopSet = false),
            IntensifierPlacementRules.SetSpec(SetType.DropSet, isTopSet = false),
            IntensifierPlacementRules.SetSpec(SetType.DropSet, isTopSet = false),
        )
        val reasons = IntensifierPlacementRules.validateTemplate(
            sets = sets,
            scheme = ProgressionScheme.Double,
        )
        assertTrue(reasons.isEmpty(), "Myo → Standard → Drop → Drop should be valid: $reasons")
    }

    @Test
    fun `myo requires kind`() {
        val sets = listOf(
            IntensifierPlacementRules.SetSpec(SetType.MyoRep, isTopSet = true, myoRepKind = null),
        )
        val reasons = IntensifierPlacementRules.validateTemplate(
            sets = sets,
            scheme = ProgressionScheme.Double,
        )
        assertTrue(IntensifierPlacementRules.Violation.MyoKindRequired in reasons)
    }

    @Test
    fun `myo top set then straight sets is valid`() {
        val sets = listOf(
            IntensifierPlacementRules.SetSpec(
                SetType.MyoRep,
                isTopSet = true,
                myoRepKind = MyoRepKind.RepFloor,
            ),
            IntensifierPlacementRules.SetSpec(SetType.Standard, isTopSet = false),
            IntensifierPlacementRules.SetSpec(SetType.Standard, isTopSet = false),
        )
        val reasons = IntensifierPlacementRules.validateTemplate(
            sets = sets,
            scheme = ProgressionScheme.Double,
        )
        assertTrue(reasons.isEmpty(), "Myo → Standard → Standard should be valid: $reasons")
    }

    @Test
    fun `myo allowed at any position`() {
        assertTrue(
            IntensifierPlacementRules.isMyoAllowed(
                position = 0,
                setCount = 3,
                scheme = ProgressionScheme.Double,
            ),
        )
        assertTrue(
            IntensifierPlacementRules.isMyoAllowed(
                position = 1,
                setCount = 3,
                scheme = ProgressionScheme.Double,
            ),
        )
        assertFalse(
            IntensifierPlacementRules.isMyoAllowed(
                position = 0,
                setCount = 3,
                scheme = ProgressionScheme.Double,
                liftAlreadyHasMyoAtOtherPosition = true,
            ),
        )
        assertFalse(
            IntensifierPlacementRules.isMyoAllowed(
                position = 0,
                setCount = 3,
                scheme = ProgressionScheme.Linear,
            ),
        )
    }

    @Test
    fun `drop allowed after myo when weighted double`() {
        assertTrue(
            IntensifierPlacementRules.isDropAllowed(
                position = 1,
                scheme = ProgressionScheme.Double,
                loadType = LoadType.Weighted,
            ),
        )
        assertFalse(
            IntensifierPlacementRules.isDropAllowed(
                position = 0,
                scheme = ProgressionScheme.Double,
                loadType = LoadType.Weighted,
            ),
        )
        assertFalse(
            IntensifierPlacementRules.isDropAllowed(
                position = 1,
                scheme = ProgressionScheme.Linear,
                loadType = LoadType.Weighted,
            ),
        )
    }

    @Test
    fun `multiple myo activations invalid`() {
        val sets = listOf(
            IntensifierPlacementRules.SetSpec(
                SetType.MyoRep,
                isTopSet = true,
                myoRepKind = MyoRepKind.RepFloor,
            ),
            IntensifierPlacementRules.SetSpec(
                SetType.MyoRep,
                isTopSet = false,
                myoRepKind = MyoRepKind.RepMatching,
            ),
        )
        val reasons = IntensifierPlacementRules.validateTemplate(
            sets = sets,
            scheme = ProgressionScheme.Double,
        )
        assertTrue(IntensifierPlacementRules.Violation.MyoMultipleActivations in reasons)
    }

    @Test
    fun `bodyweight bans drop`() {
        val sets = listOf(
            IntensifierPlacementRules.SetSpec(SetType.Standard, isTopSet = true),
            IntensifierPlacementRules.SetSpec(SetType.DropSet, isTopSet = false),
        )
        val reasons = IntensifierPlacementRules.validateTemplate(
            sets = sets,
            scheme = ProgressionScheme.Double,
            loadType = LoadType.Bodyweight,
        )
        assertTrue(IntensifierPlacementRules.Violation.DropSetBodyweightOrAssisted in reasons)
    }

    @Test
    fun `rest into drop is zero`() {
        assertEquals(
            0,
            RestDefaultForTransition.restAfter(SetType.DropSet, liftDefaultRestSeconds = 180),
        )
    }

    @Test
    fun `rest after drop before standard uses lift default`() {
        assertEquals(
            180,
            RestDefaultForTransition.restAfter(SetType.Standard, liftDefaultRestSeconds = 180),
        )
        assertEquals(
            120,
            RestDefaultForTransition.restAfter(nextType = null, liftDefaultRestSeconds = 120),
        )
    }

    @Test
    fun `redefault S-D-S-D pattern`() {
        val rests = RestDefaultForTransition.redefaultAll(
            setTypes = listOf(SetType.Standard, SetType.DropSet, SetType.Standard, SetType.DropSet),
            liftDefaultRestSeconds = 180,
        )
        assertEquals(listOf(0, 180, 0, 180), rests)
    }

    @Test
    fun `myo sets default to short rest`() {
        assertEquals(
            RestDefaultForTransition.MYO_MINI_DEFAULT_REST_SECONDS,
            RestDefaultForTransition.restAfter(
                nextType = SetType.Standard,
                liftDefaultRestSeconds = 180,
                currentType = SetType.MyoRep,
            ),
        )
        assertEquals(
            RestDefaultForTransition.MYO_MINI_DEFAULT_REST_SECONDS,
            RestDefaultForTransition.restAfter(
                nextType = null,
                liftDefaultRestSeconds = 120,
                currentType = SetType.MyoRep,
            ),
        )
    }

    @Test
    fun `myo into drop still forces zero rest`() {
        assertEquals(
            0,
            RestDefaultForTransition.restAfter(
                nextType = SetType.DropSet,
                liftDefaultRestSeconds = 120,
                currentType = SetType.MyoRep,
            ),
        )
    }

    @Test
    fun `redefault myo top then standards uses 15s on myo only`() {
        val rests = RestDefaultForTransition.redefaultAll(
            setTypes = listOf(SetType.MyoRep, SetType.Standard, SetType.Standard),
            liftDefaultRestSeconds = 120,
        )
        assertEquals(
            listOf(
                RestDefaultForTransition.MYO_MINI_DEFAULT_REST_SECONDS,
                120,
                120,
            ),
            rests,
        )
    }

    @Test
    fun `show rest control false into drop`() {
        assertFalse(RestDefaultForTransition.showRestControl(SetType.DropSet))
        assertTrue(RestDefaultForTransition.showRestControl(SetType.Standard))
    }
}
