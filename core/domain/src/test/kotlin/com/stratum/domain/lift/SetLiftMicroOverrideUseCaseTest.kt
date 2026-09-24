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

class SetLiftMicroOverrideUseCaseTest {

    private val liftId = LiftId("lat-pulldown")
    private val tiers = listOf(tier(0.0, null, 15.0))

    private fun subject(repo: FakeLiftRepository): SetLiftMicroOverrideUseCase =
        SetLiftMicroOverrideUseCase(
            repo,
            ValidateMicroIncrementsUseCase(),
            FakeLoggingPort(),
        )

    @Test
    @DisplayName("Given stops when invoked then override persists without touching bands")
    fun persistsOverride() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift()))
        val micro = listOf(stop(5.0), stop(10.0))

        val result = subject(repo)(liftId, tiers, micro)

        assertTrue(result.isSuccess)
        assertEquals(micro, repo.currentLifts.single().microIncrements)
        assertEquals(false, repo.currentLifts.single().usesEquipmentDefaultMicro)
        assertEquals(emptyList<IncrementTier>(), repo.currentLifts.single().incrementTiers)
    }

    @Test
    @DisplayName("Given null when invoked then override clears to inherit")
    fun nullClearsOverride() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift()))
        val subject = subject(repo)
        assertTrue(subject(liftId, tiers, listOf(stop(5.0))).isSuccess)

        val result = subject(liftId, tiers, null)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Weight>(), repo.currentLifts.single().microIncrements)
        assertEquals(true, repo.currentLifts.single().usesEquipmentDefaultMicro)
    }

    @Test
    @DisplayName("Given invalid stops when invoked then override is untouched")
    fun rejectsInvalidStops() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift()))

        val result = subject(repo)(liftId, tiers, listOf(stop(20.0)))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MicroIncrementsInvalidException)
        assertEquals(emptyList<Weight>(), repo.currentLifts.single().microIncrements)
    }

    private fun sampleLift(): Lift =
        Lift(
            id = liftId,
            name = "Lat pulldown",
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )

    private fun tier(lower: Double, upper: Double?, inc: Double): IncrementTier =
        IncrementTier(
            lowerBound = Weight(lower, Weight.WeightUnit.Lb),
            upperBound = upper?.let { Weight(it, Weight.WeightUnit.Lb) },
            increment = Weight(inc, Weight.WeightUnit.Lb),
        )

    private fun stop(amount: Double): Weight = Weight(amount, Weight.WeightUnit.Lb)
}
