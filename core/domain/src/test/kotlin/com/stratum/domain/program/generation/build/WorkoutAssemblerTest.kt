package com.stratum.domain.program.generation.build

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.selection.SelectedSlotLift
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.slots.SlotRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutAssemblerTest {
    private fun selWithPattern(
        slotId: String,
        muscle: String,
        role: SlotRole,
        liftId: String,
        pattern: String,
    ): SelectedSlotLift {
        val s = FilledSlot(
            slotId = slotId,
            role = role,
            pattern = runCatching { MovementPattern.valueOf(pattern) }.getOrNull()!!,
            primaryMuscle = runCatching { MuscleGroup.valueOf(muscle) }.getOrNull()!!,
            repProfile = RpRepProfile.Isolation,
            setCount = 3,
        )
        return SelectedSlotLift(
            slot = s,
            lift = LiftContext(
                id = liftId,
                name = liftId,
                movementPattern = runCatching { MovementPattern.valueOf(pattern) }.getOrNull(),
                movementPatterns = setOfNotNull(runCatching { MovementPattern.valueOf(pattern) }.getOrNull()),
                primaryMuscles = setOfNotNull(runCatching { MuscleGroup.valueOf(muscle) }.getOrNull()),
                secondaryMuscles = emptySet(),
                proven = false,
            ),
            candidates = emptyList(),
        )
    }

    private fun sel(slotId: String, muscle: String, role: SlotRole, liftId: String): SelectedSlotLift =
        selWithPattern(slotId, muscle, role, liftId, "HorizontalPress")

    private fun armSpecContext(): ProgramGenerationContext =
        ProgramGenerationContext(
            trainingAge = "Intermediate",
            lastTrained = null,
            sessionLengthMinutes = null,
            trainingDaysPerWeek = 0,
            weekdays = emptyList(),
            specialization = mapOf(MuscleGroup.Bicep to SpecializationLevel.Specialize, MuscleGroup.Tricep to SpecializationLevel.Specialize),
            liftsById = emptyMap(),
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = emptySet(),
            movementPatternByLiftId = emptyMap(),
            liftEffectivenessByLiftId = emptyMap(),
        )

    private fun emptyUpperPlan(name: String, position: Int, siblingIndex: Int): SessionSlotPlan =
        SessionSlotPlan(
            sessionType = SessionTypeId.Upper,
            name = name,
            slots = emptyList(),
            siblingIndex = siblingIndex,
            position = position,
            filledSlots = mutableListOf(),
        )

    @Test
    @DisplayName("places specialized arm slots before chest and back compounds")
    fun placesSpecializedArmSlotsBeforeCompounds() {
        val context = armSpecContext()
        val selections = listOf(
            sel("h-press", "Chest", SlotRole.Anchor, "bench"),
            sel("v-pull", "Back", SlotRole.SecondaryCompound, "chin"),
            sel("h-pull", "Back", SlotRole.SecondaryCompound, "row"),
            sel("incline", "Chest", SlotRole.SecondaryCompound, "inc"),
            sel("bicep", "Bicep", SlotRole.Isolation, "curl"),
            sel("tricep", "Tricep", SlotRole.Isolation, "tri"),
        )
        val workouts = assembleWorkouts(
            listOf(emptyUpperPlan("Upper A", position = 0, siblingIndex = 0)),
            listOf(selections),
            context,
        )
        val ids = workouts[0].workoutLifts.orEmpty().mapNotNull { it.liftId }
        assertTrue(ids.indexOf("curl") < ids.indexOf("bench"))
        assertTrue(ids.indexOf("tri") < ids.indexOf("bench"))
    }

    @Test
    @DisplayName("interleaves chest and back after specialized arms instead of stacking pulls")
    fun interleavesChestAndBackAfterArms() {
        val context = armSpecContext()
        val fly = selWithPattern("incline", "Chest", SlotRole.SecondaryCompound, "fly", "InclinePress")
        val pull = selWithPattern("v-pull", "Back", SlotRole.SecondaryCompound, "pulldown", "VerticalPull")
        val row = selWithPattern("h-pull", "Back", SlotRole.SecondaryCompound, "row", "HorizontalPull")
        val selections = listOf(
            fly,
            pull,
            row,
            selWithPattern("bicep", "Bicep", SlotRole.Isolation, "curl", "ElbowFlexion"),
            selWithPattern("tricep", "Tricep", SlotRole.Isolation, "tri", "ElbowExtension"),
        )
        val workouts = assembleWorkouts(
            listOf(emptyUpperPlan("Upper B", position = 1, siblingIndex = 1)),
            listOf(selections),
            context,
        )
        val muscles = workouts[0].workoutLifts.orEmpty().map { lift ->
            when (lift.liftId) {
                "fly" -> "Chest"
                "curl" -> "Bicep"
                "tri" -> "Tricep"
                else -> "Back"
            }
        }
        assertEquals(listOf("Bicep", "Tricep"), muscles.take(2))
        assertEquals(listOf("Back", "Chest", "Back"), muscles.drop(2))
    }

    @Test
    @DisplayName("does not stack chest/back compounds back-to-back when alternatives exist")
    fun doesNotStackCompoundsBackToBack() {
        val context = ProgramGenerationContext(
            trainingAge = "Intermediate",
            lastTrained = null,
            sessionLengthMinutes = null,
            trainingDaysPerWeek = 0,
            weekdays = emptyList(),
            specialization = emptyMap(),
            liftsById = emptyMap(),
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = emptySet(),
            movementPatternByLiftId = emptyMap(),
            liftEffectivenessByLiftId = emptyMap(),
        )
        val selections = listOf(
            selWithPattern("h-press", "Chest", SlotRole.Anchor, "bench", "HorizontalPress"),
            selWithPattern("v-pull", "Back", SlotRole.SecondaryCompound, "chin", "VerticalPull"),
            selWithPattern("h-pull", "Back", SlotRole.SecondaryCompound, "row", "HorizontalPull"),
            selWithPattern("incline", "Chest", SlotRole.SecondaryCompound, "inc", "InclinePress"),
        )
        val workouts = assembleWorkouts(
            listOf(emptyUpperPlan("Upper A", position = 0, siblingIndex = 0)),
            listOf(selections),
            context,
        )
        val muscles = workouts[0].workoutLifts.orEmpty().map { lift ->
            if (lift.liftId == "bench" || lift.liftId == "inc") "Chest" else "Back"
        }
        for (i in 1 until muscles.size) {
            assertTrue(!(muscles[i] == "Back" && muscles[i - 1] == "Back")) {
                "stacked back at ${i - 1} and $i: ${muscles.joinToString(" -> ")}"
            }
        }
    }

    @Test
    @DisplayName("does not stack push compounds after tricep isolation when bridging into compounds")
    fun bridgesArmsIntoCompoundsWithPullBeforePush() {
        val context = armSpecContext()
        val selections = listOf(
            selWithPattern("h-press", "Chest", SlotRole.Anchor, "bench", "HorizontalPress"),
            selWithPattern("v-pull", "Back", SlotRole.SecondaryCompound, "pulldown", "VerticalPull"),
            selWithPattern("h-pull", "Back", SlotRole.SecondaryCompound, "row", "HorizontalPull"),
            selWithPattern("bicep", "Bicep", SlotRole.Isolation, "curl", "ElbowFlexion"),
            selWithPattern("tricep", "Tricep", SlotRole.Isolation, "tri", "ElbowExtension"),
        )
        val ordered = orderSelectionsForTest(selections, context)
        val labels = ordered.map { it.lift.id }
        assertEquals(listOf("curl", "tri", "pulldown", "bench", "row"), labels)
    }

    @Test
    @DisplayName("Double progression: only top set stores a rep range; backoffs are 0/0")
    fun doubleProgressionBackoffsOmitRepRange() {
        // Intermediate isolation → Double. setCount = 3 from sel().
        val context = armSpecContext()
        val workouts = assembleWorkouts(
            listOf(emptyUpperPlan("Upper A", position = 0, siblingIndex = 0)),
            listOf(listOf(sel("bicep", "Bicep", SlotRole.Isolation, "curl"))),
            context,
        )
        val sets = workouts.single().workoutLifts.orEmpty().single().workoutLiftSets.orEmpty()
            .sortedBy { it.position }

        assertEquals(3, sets.size)
        assertTrue(sets[0].isTopSet)
        assertTrue(sets[0].repMin > 0)
        assertTrue(sets[0].repMax >= sets[0].repMin)

        sets.drop(1).forEach { backoff ->
            assertTrue(!backoff.isTopSet)
            assertEquals(0, backoff.repMin, "Double backoff must not store a rep range")
            assertEquals(0, backoff.repMax, "Double backoff must not store a rep range")
        }
    }

    @Test
    @DisplayName("Linear progression: every working set stores the same rep range")
    fun linearProgressionSharesRepRangeOnAllSets() {
        // Beginner + axial compound → Linear.
        val context = ProgramGenerationContext(
            trainingAge = "Beginner",
            lastTrained = null,
            sessionLengthMinutes = null,
            trainingDaysPerWeek = 0,
            weekdays = emptyList(),
            specialization = emptyMap(),
            liftsById = emptyMap(),
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = emptySet(),
            movementPatternByLiftId = emptyMap(),
            liftEffectivenessByLiftId = emptyMap(),
        )
        val slot = FilledSlot(
            slotId = "h-press",
            role = SlotRole.Anchor,
            pattern = MovementPattern.HorizontalPress,
            primaryMuscle = MuscleGroup.Chest,
            repProfile = RpRepProfile.LowerMachineOrUpperPress,
            setCount = 3,
        )
        val selection = SelectedSlotLift(
            slot = slot,
            lift = LiftContext(
                id = "bench",
                name = "bench",
                movementPattern = MovementPattern.HorizontalPress,
                movementPatterns = setOf(MovementPattern.HorizontalPress),
                primaryMuscles = setOf(MuscleGroup.Chest),
                secondaryMuscles = emptySet(),
                proven = false,
                kinematicProfile = com.stratum.domain.lift.KinematicProfile.AxialCompound,
            ),
            candidates = emptyList(),
        )
        val workouts = assembleWorkouts(
            listOf(emptyUpperPlan("Full", position = 0, siblingIndex = 0)),
            listOf(listOf(selection)),
            context,
        )
        val sets = workouts.single().workoutLifts.orEmpty().single().workoutLiftSets.orEmpty()
            .sortedBy { it.position }

        assertEquals(3, sets.size)
        // Linear: shared band on every set; index 0 is the load-progression anchor.
        assertTrue(sets[0].isTopSet, "Linear first set is the progression anchor")
        assertTrue(sets.drop(1).none { it.isTopSet })
        val firstRange = sets[0].repMin to sets[0].repMax
        assertTrue(firstRange.first > 0)
        assertTrue(firstRange.second >= firstRange.first)
        sets.forEach { set ->
            assertEquals(firstRange.first, set.repMin)
            assertEquals(firstRange.second, set.repMax)
        }
    }
}
