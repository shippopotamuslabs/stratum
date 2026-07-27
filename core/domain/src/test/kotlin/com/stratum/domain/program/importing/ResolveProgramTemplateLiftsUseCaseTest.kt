package com.stratum.domain.program.importing

import com.stratum.core.testing.importing.FakeLiftEmbeddingCatalog
import com.stratum.core.testing.importing.FakeLiftNameEmbeddingPort
import com.stratum.core.testing.importing.FakeLiftNameResolverPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.importing.Bm25LiftNameResolver
import com.stratum.domain.importing.HybridLiftNameResolver
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
    fun `all exact catalog names skip embedding prepareBatch`() = runTest {
        val squatId = LiftId("squat")
        val benchId = LiftId("bench")
        val catalog =
            listOf(
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
                Lift(
                    id = benchId,
                    name = "Barbell Bench Press",
                    movementPatterns = emptySet(),
                    primaryMuscles = emptySet(),
                    secondaryMuscles = emptySet(),
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    enabled = true,
                ),
            )
        val liftRepo = FakeLiftRepository(initialLifts = catalog)
        val embeddingPort = FakeLiftNameEmbeddingPort()
        val hybrid =
            HybridLiftNameResolver(
                bm25 = Bm25LiftNameResolver(),
                catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                nameEmbedding = embeddingPort,
            )
        val useCase = ResolveProgramTemplateLiftsUseCase(liftRepo, hybrid)

        val template =
            NormalizedProgramTemplate(
                programName = "Exact",
                cycleLength = 1,
                workouts =
                    listOf(
                        NormalizedProgramTemplateWorkout(
                            name = "Day 1",
                            weekday = null,
                            position = 0,
                            lifts =
                                listOf(
                                    NormalizedProgramTemplateLift(
                                        name = "Barbell Squat",
                                        position = 0,
                                        sets =
                                            listOf(
                                                NormalizedProgramTemplateSet(
                                                    position = 0,
                                                    setTypeLabel = null,
                                                    repMin = 5,
                                                    repMax = 5,
                                                    rirGoal = 2,
                                                    restSeconds = null,
                                                    isTopSet = true,
                                                ),
                                            ),
                                        supersetGroupKey = null,
                                    ),
                                    NormalizedProgramTemplateLift(
                                        name = "Barbell Bench Press",
                                        position = 1,
                                        sets =
                                            listOf(
                                                NormalizedProgramTemplateSet(
                                                    position = 0,
                                                    setTypeLabel = null,
                                                    repMin = 8,
                                                    repMax = 10,
                                                    rirGoal = 2,
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

        assertTrue(embeddingPort.lastRequestedNames.isEmpty())
        assertEquals(squatId, program.workouts.single().workoutLifts[0].liftId)
        assertEquals(benchId, program.workouts.single().workoutLifts[1].liftId)
        assertEquals(2, liftRepo.observeLifts().first().size)
    }

    @Test
    fun `residual embedding rescue matches gray zone program lift to catalog`() = runTest {
        val seatedId = LiftId("seated-leg")
        val ballId = LiftId("ball-leg")
        val catalog =
            listOf(
                Lift(
                    id = ballId,
                    name = "Ball Leg Curl",
                    movementPatterns = emptySet(),
                    primaryMuscles = emptySet(),
                    secondaryMuscles = emptySet(),
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    enabled = true,
                ),
                Lift(
                    id = seatedId,
                    name = "Seated Leg Curl",
                    movementPatterns = emptySet(),
                    primaryMuscles = emptySet(),
                    secondaryMuscles = emptySet(),
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    enabled = true,
                ),
            )
        val liftRepo = FakeLiftRepository(initialLifts = catalog)
        val embeddingPort =
            FakeLiftNameEmbeddingPort(
                vectorsByName = mapOf("Hamstring Curl" to floatArrayOf(1f, 0f)),
            )
        val hybrid =
            HybridLiftNameResolver(
                bm25 = Bm25LiftNameResolver(),
                catalogEmbeddings =
                    FakeLiftEmbeddingCatalog(
                        vectorsByLiftId =
                            mapOf(
                                seatedId to floatArrayOf(0.95f, 0.31f),
                                ballId to floatArrayOf(0.2f, 0.98f),
                            ),
                        dimensions = 2,
                    ),
                nameEmbedding = embeddingPort,
            )
        val useCase = ResolveProgramTemplateLiftsUseCase(liftRepo, hybrid)

        val template =
            NormalizedProgramTemplate(
                programName = "Gray",
                cycleLength = 1,
                workouts =
                    listOf(
                        NormalizedProgramTemplateWorkout(
                            name = "Day 1",
                            weekday = null,
                            position = 0,
                            lifts =
                                listOf(
                                    NormalizedProgramTemplateLift(
                                        name = "Hamstring Curl",
                                        position = 0,
                                        sets =
                                            listOf(
                                                NormalizedProgramTemplateSet(
                                                    position = 0,
                                                    setTypeLabel = null,
                                                    repMin = 10,
                                                    repMax = 12,
                                                    rirGoal = 2,
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

        assertEquals(listOf("Hamstring Curl"), embeddingPort.lastRequestedNames)
        assertEquals(seatedId, program.workouts.single().workoutLifts.single().liftId)
        assertEquals(2, liftRepo.observeLifts().first().size)
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
    fun `defaults all lifts to Double and maps set-type labels`() = runTest {
        // Import has no training-age signal; Linear is opt-in later for compounds only.
        val compoundId = LiftId("bench")
        val catalog =
            listOf(
                Lift(
                    id = compoundId,
                    name = "Barbell Bench Press",
                    movementPatterns = setOf(
                        com.stratum.domain.lift.MovementPattern.HorizontalPress,
                        com.stratum.domain.lift.MovementPattern.ElbowExtension,
                    ),
                    primaryMuscles = emptySet(),
                    secondaryMuscles = emptySet(),
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    enabled = true,
                    fatigueProfile = com.stratum.domain.lift.FatigueProfile.High,
                ),
            )
        val liftRepo = FakeLiftRepository(initialLifts = catalog)
        val resolver = FakeLiftNameResolverPort(matches = mapOf("Barbell Bench Press" to compoundId))
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
                        NormalizedProgramTemplateLift(
                            name = "Barbell Bench Press",
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
                        NormalizedProgramTemplateLift(
                            name = "Myo Move",
                            position = 1,
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
        val lifts = program.workouts[0].workoutLifts
        assertEquals(ProgressionScheme.Double, lifts[0].progressionScheme)
        assertEquals(ProgressionScheme.Double, lifts[1].progressionScheme)
        assertEquals(SetType.MyoRep, lifts[1].sets[0].setType)
        // Null reps default to the safe 8-12 range.
        assertEquals(8, lifts[1].sets[0].repMin)
        assertEquals(12, lifts[1].sets[0].repMax)
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
