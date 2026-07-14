package com.stratum.domain.program.importing

import com.stratum.core.testing.importing.FakeLiftNameResolverPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResolveProgramTemplateLiftsUseCaseTest {

    @Test
    fun `matched lifts use resolved catalog ids and unmatched lifts create custom lifts`() = runTest {
        val squatId = LiftId("squat")
        val catalog = listOf(
            Lift(
                id = squatId,
                name = "Barbell Squat",
                movementPatterns = emptySet(),
                primaryMuscles = emptySet(),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
            ),
        )
        val liftRepo = FakeLiftRepository(initialLifts = catalog)
        val resolver = FakeLiftNameResolverPort(matches = mapOf("Barbell Squat" to squatId))
        val useCase = ResolveProgramTemplateLiftsUseCase(liftRepo, resolver)

        val template = NormalizedProgramTemplate(
            programName = "Test",
            cycleLength = 2,
            workouts = listOf(
                NormalizedProgramTemplateWorkout(
                    name = "Day 1",
                    weekday = null,
                    position = 0,
                    lifts = listOf(
                        NormalizedProgramTemplateLift(
                            name = "Barbell Squat",
                            position = 0,
                            sets = listOf(
                                NormalizedProgramTemplateSet(
                                    position = 0,
                                    setTypeLabel = "Standard Set",
                                    repMin = 5,
                                    repMax = 5,
                                    rirGoal = 2,
                                    restSeconds = 180,
                                    isTopSet = true,
                                ),
                            ),
                            supersetGroupKey = null,
                        ),
                        // Unmatched → must create a custom lift.
                        NormalizedProgramTemplateLift(
                            name = "Mystery Lift",
                            position = 1,
                            sets = listOf(
                                NormalizedProgramTemplateSet(
                                    position = 0,
                                    setTypeLabel = null,
                                    repMin = 8,
                                    repMax = 12,
                                    rirGoal = null,
                                    restSeconds = null,
                                    isTopSet = true,
                                ),
                            ),
                            supersetGroupKey = null,
                        ),
                    ),
                ),
            ),
        )

        val program = useCase.invoke(template)

        assertEquals("Test", program.name)
        // Template cycleLength is provider meta only — slot space is train+rest (train-only → 1).
        assertEquals(1, program.cycleLength)
        assertEquals(1, program.workouts.size)
        val workout = program.workouts[0]
        assertEquals("Day 1", workout.name)
        assertEquals(2, workout.workoutLifts.size)

        // Matched lift keeps the catalog id.
        assertEquals(squatId, workout.workoutLifts[0].liftId)
        // Custom lift was created with a distinct id.
        val customLiftId = workout.workoutLifts[1].liftId
        assertNotNull(customLiftId)
        assertTrue(customLiftId != squatId)

        // One custom lift was added to the catalog (1 seed + 1 created).
        val catalogAfter = liftRepo.observeLifts().first()
        assertEquals(2, catalogAfter.size)
        assertTrue(catalogAfter.any { it.id == customLiftId && it.isCustom })
    }

    @Test
    fun `superset group keys within a workout share a WorkoutSupersetGroupId`() = runTest {
        val liftRepo = FakeLiftRepository()
        val resolver = FakeLiftNameResolverPort()
        val useCase = ResolveProgramTemplateLiftsUseCase(liftRepo, resolver)

        val template = NormalizedProgramTemplate(
            programName = null,
            cycleLength = null,
            workouts = listOf(
                NormalizedProgramTemplateWorkout(
                    name = "Day 1",
                    weekday = null,
                    position = 0,
                    lifts = listOf(
                        supersetLift("Curl A", "1"),
                        supersetLift("Curl B", "1"),
                        supersetLift("Triceps A", "2"),
                    ),
                ),
            ),
        )

        val program = useCase.invoke(template)
        val lifts = program.workouts[0].workoutLifts
        assertEquals(3, lifts.size)
        // Same key → same group id; different key → different group id.
        assertEquals(lifts[0].supersetGroupId, lifts[1].supersetGroupId)
        assertNotNull(lifts[2].supersetGroupId)
        assertTrue(lifts[0].supersetGroupId != lifts[2].supersetGroupId)
    }

    @Test
    fun `defaults progression scheme to Linear and maps set-type labels`() = runTest {
        val liftRepo = FakeLiftRepository()
        val useCase = ResolveProgramTemplateLiftsUseCase(liftRepo, FakeLiftNameResolverPort())

        val template = NormalizedProgramTemplate(
            programName = null,
            cycleLength = null,
            workouts = listOf(
                NormalizedProgramTemplateWorkout(
                    name = "Day 1",
                    weekday = null,
                    position = 0,
                    lifts = listOf(
                        NormalizedProgramTemplateLift(
                            name = "Myo Move",
                            position = 0,
                            sets = listOf(
                                NormalizedProgramTemplateSet(
                                    position = 0,
                                    setTypeLabel = "Myo Set",
                                    repMin = null,
                                    repMax = null,
                                    rirGoal = null,
                                    restSeconds = null,
                                    isTopSet = true,
                                ),
                            ),
                            supersetGroupKey = null,
                        ),
                    ),
                ),
            ),
        )

        val program = useCase.invoke(template)
        val lift = program.workouts[0].workoutLifts[0]
        assertEquals(ProgressionScheme.Linear, lift.progressionScheme)
        assertEquals(SetType.MyoRep, lift.sets[0].setType)
        // Null reps default to the safe 8-12 range.
        assertEquals(8, lift.sets[0].repMin)
        assertEquals(12, lift.sets[0].repMax)
    }

    @Test
    fun `cycle length defaults to workout count when null or zero`() = runTest {
        val liftRepo = FakeLiftRepository()
        val useCase = ResolveProgramTemplateLiftsUseCase(liftRepo, FakeLiftNameResolverPort())

        val template = NormalizedProgramTemplate(
            programName = null,
            cycleLength = null,
            workouts = listOf(
                workout("Day 1", listOf(lift("A"))),
                workout("Day 2", listOf(lift("B"))),
                workout("Day 3", listOf(lift("C"))),
            ),
        )

        val program = useCase.invoke(template)
        assertEquals(3, program.cycleLength)
    }

    private fun supersetLift(name: String, key: String) = NormalizedProgramTemplateLift(
        name = name,
        position = 0,
        sets = listOf(
            NormalizedProgramTemplateSet(0, null, 8, 12, 2, 90, true),
        ),
        supersetGroupKey = key,
    )

    private fun workout(name: String, lifts: List<NormalizedProgramTemplateLift>) =
        NormalizedProgramTemplateWorkout(name, null, 0, lifts)

    private fun lift(name: String) = NormalizedProgramTemplateLift(
        name = name,
        position = 0,
        sets = listOf(NormalizedProgramTemplateSet(0, null, 5, 5, 2, 120, true)),
        supersetGroupKey = null,
    )
}
