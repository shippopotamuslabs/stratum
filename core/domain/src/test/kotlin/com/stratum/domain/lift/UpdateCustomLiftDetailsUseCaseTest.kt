package com.stratum.domain.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UpdateCustomLiftDetailsUseCaseTest {

    private val liftId = LiftId("my-cable-fly")

    @Test
    @DisplayName("Given custom lift when details updated then repository reflects them")
    fun updatesCustomDetails() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = true)))
        val useCase = UpdateCustomLiftDetailsUseCase(repo, FakeLoggingPort())

        val result =
            useCase(
                id = liftId,
                details =
                    CustomLiftDetails(
                        movementPatterns = setOf(MovementPattern.ShoulderAdduction),
                        muscleLengthBias = MuscleLengthBias.Lengthened,
                        kinematicProfile = KinematicProfile.Isolation,
                        loadType = LoadType.Weighted,
                        equipment = "  cable  ",
                    ),
            )

        assertTrue(result.isSuccess)
        val lift = repo.currentLifts.single()
        assertEquals(setOf(MovementPattern.ShoulderAdduction), lift.movementPatterns)
        assertEquals(MuscleLengthBias.Lengthened, lift.muscleLengthBias)
        assertEquals("cable", lift.equipment)
    }

    @Test
    @DisplayName("Given catalog lift when details updated then fails without writing")
    fun rejectsCatalogLift() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = false)))
        val useCase = UpdateCustomLiftDetailsUseCase(repo, FakeLoggingPort())

        val result =
            useCase(
                id = liftId,
                details =
                    CustomLiftDetails(
                        movementPatterns = setOf(MovementPattern.HipHinge),
                        muscleLengthBias = MuscleLengthBias.Shortened,
                        kinematicProfile = KinematicProfile.AxialCompound,
                        loadType = LoadType.Bodyweight,
                        equipment = "barbell",
                    ),
            )

        assertTrue(result.isFailure)
        val lift = repo.currentLifts.single()
        assertEquals(setOf(MovementPattern.HorizontalPress), lift.movementPatterns)
        assertNull(lift.equipment)
    }

    @Test
    @DisplayName("Given empty movement patterns when updated then fails")
    fun rejectsEmptyPatterns() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = true)))
        val useCase = UpdateCustomLiftDetailsUseCase(repo, FakeLoggingPort())

        val result =
            useCase(
                id = liftId,
                details =
                    CustomLiftDetails(
                        movementPatterns = emptySet(),
                        muscleLengthBias = MuscleLengthBias.MidRange,
                        kinematicProfile = KinematicProfile.Isolation,
                        loadType = LoadType.Weighted,
                        equipment = null,
                    ),
            )

        assertTrue(result.isFailure)
    }

    private fun sampleLift(isCustom: Boolean): Lift =
        Lift(
            id = liftId,
            name = "My Cable Fly",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = isCustom,
        )
}
