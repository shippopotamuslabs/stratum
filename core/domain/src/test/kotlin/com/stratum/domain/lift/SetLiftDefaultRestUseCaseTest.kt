package com.stratum.domain.lift

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SetLiftDefaultRestUseCaseTest {

    @Test
    @DisplayName("Given catalog lift when set rest then override is stored")
    fun setsOverrideOnCatalogLift() = runTest {
        val lift = sampleLift(isCustom = false)
        val repo = FakeLiftRepository(listOf(lift))
        val useCase = SetLiftDefaultRestUseCase(repo)

        val result = useCase(lift.id, 150)

        assertTrue(result.isSuccess)
        assertEquals(150, repo.currentLifts.single().defaultRestSeconds)
    }

    @Test
    @DisplayName("Given out-of-range rest when set then fails")
    fun rejectsOutOfRange() = runTest {
        val lift = sampleLift(isCustom = true)
        val repo = FakeLiftRepository(listOf(lift))
        val useCase = SetLiftDefaultRestUseCase(repo)

        val result = useCase(lift.id, 999)

        assertTrue(result.isFailure)
        assertEquals(null, repo.currentLifts.single().defaultRestSeconds)
    }

    private fun sampleLift(isCustom: Boolean): Lift =
        Lift(
            id = LiftId("lift-1"),
            name = "Sample",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = isCustom,
        )
}
