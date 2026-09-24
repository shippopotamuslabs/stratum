package com.stratum.domain.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SetLiftIncrementTiersUseCaseTest {

    private val liftId = LiftId("bench-press")

    private fun subject(repo: FakeLiftRepository): SetLiftIncrementTiersUseCase =
        SetLiftIncrementTiersUseCase(
            repo,
            ValidateIncrementTiersUseCase(),
            FakeLoggingPort(),
        )

    @Test
    @DisplayName("Given valid tiers when invoked then they are persisted")
    fun persistsValidTiers() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift()))
        val useCase = subject(repo)
        val tiers =
            listOf(
                tier(0.0, 100.0, 5.0),
                tier(100.0, null, 10.0),
            )

        val result = useCase(liftId, tiers)

        assertTrue(result.isSuccess)
        assertEquals(tiers, repo.currentLifts.single().incrementTiers)
    }

    @Test
    @DisplayName("Given invalid tiers when invoked then repository is not written")
    fun rejectsInvalidTiers() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift()))
        val useCase = subject(repo)
        val tiers =
            listOf(
                tier(0.0, 40.0, 5.0),
                tier(50.0, null, 10.0), // gap
            )

        val result = useCase(liftId, tiers)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IncrementTiersInvalidException)
        assertEquals(emptyList<IncrementTier>(), repo.currentLifts.single().incrementTiers)
    }

    @Test
    @DisplayName("Given empty tiers when invoked then tiers are cleared")
    fun emptyTiersClear() = runTest {
        val existing = listOf(tier(0.0, null, 5.0))
        val repo = FakeLiftRepository(listOf(sampleLift(incrementTiers = existing)))
        val useCase = subject(repo)

        val result = useCase(liftId, emptyList())

        assertTrue(result.isSuccess)
        assertEquals(emptyList<IncrementTier>(), repo.currentLifts.single().incrementTiers)
    }

    private fun sampleLift(incrementTiers: List<IncrementTier> = emptyList()): Lift =
        Lift(
            id = liftId,
            name = "Bench Press",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = incrementTiers,
            presetTags = emptySet(),
            enabled = true,
        )

    private fun tier(lower: Double, upper: Double?, inc: Double): IncrementTier =
        IncrementTier(
            lowerBound = Weight(lower, Weight.WeightUnit.Lb),
            upperBound = upper?.let { Weight(it, Weight.WeightUnit.Lb) },
            increment = Weight(inc, Weight.WeightUnit.Lb),
        )
}
