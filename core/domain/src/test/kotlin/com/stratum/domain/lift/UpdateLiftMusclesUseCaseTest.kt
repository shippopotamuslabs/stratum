package com.stratum.domain.lift

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UpdateLiftMusclesUseCaseTest {

    private val liftId = LiftId("bench-press")

    @Test
    @DisplayName("Given custom lift when muscles updated then repository reflects them")
    fun updatesMusclesOnCustom() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = true)))
        val useCase = UpdateLiftMusclesUseCase(repo)

        val result =
            useCase(
                id = liftId,
                primaryMuscles = setOf(MuscleGroup.Chest, MuscleGroup.Tricep),
                secondaryMuscles = setOf(MuscleGroup.AnteriorDeltoid),
            )

        assertTrue(result.isSuccess)
        val lift = repo.currentLifts.single()
        assertEquals(setOf(MuscleGroup.Chest, MuscleGroup.Tricep), lift.primaryMuscles)
        assertEquals(setOf(MuscleGroup.AnteriorDeltoid), lift.secondaryMuscles)
    }

    @Test
    @DisplayName("Given catalog lift when muscles updated then fails without writing")
    fun rejectsCatalogLift() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = false)))
        val useCase = UpdateLiftMusclesUseCase(repo)

        val result =
            useCase(
                id = liftId,
                primaryMuscles = setOf(MuscleGroup.Back),
                secondaryMuscles = emptySet(),
            )

        assertTrue(result.isFailure)
        assertEquals(setOf(MuscleGroup.Chest), repo.currentLifts.single().primaryMuscles)
    }

    @Test
    @DisplayName("Given empty primary when updated then fails without writing")
    fun rejectsEmptyPrimary() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = true)))
        val useCase = UpdateLiftMusclesUseCase(repo)

        val result =
            useCase(
                id = liftId,
                primaryMuscles = emptySet(),
                secondaryMuscles = setOf(MuscleGroup.Chest),
            )

        assertTrue(result.isFailure)
        assertEquals(setOf(MuscleGroup.Chest), repo.currentLifts.single().primaryMuscles)
    }

    @Test
    @DisplayName("Given secondary overlapping primary when updated then secondary is de-duped")
    fun deDupesSecondary() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = true)))
        val useCase = UpdateLiftMusclesUseCase(repo)

        val result =
            useCase(
                id = liftId,
                primaryMuscles = setOf(MuscleGroup.Chest),
                secondaryMuscles = setOf(MuscleGroup.Chest, MuscleGroup.Tricep),
            )

        assertTrue(result.isSuccess)
        val lift = repo.currentLifts.single()
        assertEquals(setOf(MuscleGroup.Chest), lift.primaryMuscles)
        assertEquals(setOf(MuscleGroup.Tricep), lift.secondaryMuscles)
    }

    private fun sampleLift(isCustom: Boolean): Lift =
        Lift(
            id = liftId,
            name = "Bench Press",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = isCustom,
        )
}
