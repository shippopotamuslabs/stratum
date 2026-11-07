package com.stratum.application.lift

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern

class SetLiftDefaultRestUseCaseTest {

    @Test
    @DisplayName("Given catalog lift when set rest then override is stored")
    fun setsOverrideOnCatalogLift() = runTest {
        val lift = sampleLift(isCustom = false)
        val repo = FakeLiftRepository(listOf(lift))
        val useCase = SetLiftDefaultRestUseCase(repo)

        val result = useCase(lift.id, 150)

        assertEquals(SetLiftDefaultRestResult.Success, result)
        assertEquals(150, repo.currentLifts.single().defaultRestSeconds)
    }

    @Test
    @DisplayName("Given out-of-range rest when set then fails")
    fun rejectsOutOfRange() = runTest {
        val lift = sampleLift(isCustom = true)
        val repo = FakeLiftRepository(listOf(lift))
        val useCase = SetLiftDefaultRestUseCase(repo)

        val result = useCase(lift.id, 999)

        assertEquals(SetLiftDefaultRestResult.InvalidRest(999), result)
        assertEquals(null, repo.currentLifts.single().defaultRestSeconds)
    }

    @Test
    @DisplayName("Given missing lift when set rest then LiftNotFound")
    fun missingLiftFails() = runTest {
        val repo = FakeLiftRepository()
        val useCase = SetLiftDefaultRestUseCase(repo)

        val result = useCase(LiftId("missing"), 150)

        assertEquals(SetLiftDefaultRestResult.LiftNotFound(LiftId("missing")), result)
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
