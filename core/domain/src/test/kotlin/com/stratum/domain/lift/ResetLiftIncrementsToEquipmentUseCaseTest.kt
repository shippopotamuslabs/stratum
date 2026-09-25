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

class ResetLiftIncrementsToEquipmentUseCaseTest {

    private val lb = Weight.WeightUnit.Lb
    private val liftId = LiftId("lat-pulldown")

    @Test
    @DisplayName("Given band and micro overrides when invoked then both clear atomically")
    fun resetClearsBothOverrides() = runTest {
        val repo =
            FakeLiftRepository(
                listOf(
                    Lift(
                        id = liftId,
                        name = "Lat pulldown",
                        movementPatterns = setOf(MovementPattern.VerticalPull),
                        primaryMuscles = setOf(MuscleGroup.Back),
                        secondaryMuscles = emptySet(),
                        incrementTiers =
                            listOf(
                                IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb)),
                            ),
                        presetTags = emptySet(),
                        usesEquipmentDefaultIncrements = false,
                        microIncrements = listOf(Weight(1.25, lb)),
                        usesEquipmentDefaultMicro = false,
                        enabled = true,
                    ),
                ),
            )
        val subject = ResetLiftIncrementsToEquipmentUseCase(repo, FakeLoggingPort())

        val result = subject(liftId)

        assertTrue(result.isSuccess)
        val stored = repo.currentLifts.single()
        assertEquals(emptyList<IncrementTier>(), stored.incrementTiers)
        assertTrue(stored.usesEquipmentDefaultIncrements)
        assertEquals(emptyList<Weight>(), stored.microIncrements)
        assertTrue(stored.usesEquipmentDefaultMicro)
    }

    @Test
    @DisplayName("Given an already-inheriting lift when invoked then success leaves it inheriting")
    fun resetOnInheritingLiftIsNoopSuccess() = runTest {
        val repo =
            FakeLiftRepository(
                listOf(
                    Lift(
                        id = liftId,
                        name = "Lat pulldown",
                        movementPatterns = setOf(MovementPattern.VerticalPull),
                        primaryMuscles = setOf(MuscleGroup.Back),
                        secondaryMuscles = emptySet(),
                        incrementTiers = emptyList(),
                        presetTags = emptySet(),
                        usesEquipmentDefaultIncrements = true,
                        enabled = true,
                    ),
                ),
            )
        val subject = ResetLiftIncrementsToEquipmentUseCase(repo, FakeLoggingPort())

        val result = subject(liftId)

        assertTrue(result.isSuccess)
        val stored = repo.currentLifts.single()
        assertTrue(stored.usesEquipmentDefaultIncrements)
        assertTrue(stored.usesEquipmentDefaultMicro)
    }
}
