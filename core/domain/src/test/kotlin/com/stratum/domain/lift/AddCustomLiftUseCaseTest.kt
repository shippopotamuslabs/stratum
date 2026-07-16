package com.stratum.domain.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AddCustomLiftUseCaseTest {

    @Test
    @DisplayName("Given a valid definition when added then catalog contains the custom lift")
    fun addsValidCustomLift() = runTest {
        val repo = FakeLiftRepository()
        val useCase = AddCustomLiftUseCase(repo, FakeLoggingPort())

        val result =
            useCase(
                CustomLiftDefinition(
                    name = "  Landmine Press  ",
                    movementPatterns = setOf(MovementPattern.HorizontalPress),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    secondaryMuscles = setOf(MuscleGroup.Tricep, MuscleGroup.Chest),
                    equipmentName = "  landmine  ",
                    muscleLengthBias = MuscleLengthBias.Lengthened,
                    fatigueProfile = FatigueProfile.Medium,
                    loadType = LoadType.Weighted,
                    defaultRestSeconds = 150,
                ),
            )

        assertTrue(result.isSuccess)
        val lift = repo.currentLifts.single { it.id == result.getOrThrow() }
        assertEquals("Landmine Press", lift.name)
        assertTrue(lift.isCustom)
        assertTrue(lift.enabled)
        assertEquals(setOf(MovementPattern.HorizontalPress), lift.movementPatterns)
        assertEquals(setOf(MuscleGroup.Chest), lift.primaryMuscles)
        assertEquals(setOf(MuscleGroup.Tricep), lift.secondaryMuscles)
        assertEquals("landmine", lift.equipmentName)
        assertEquals(MuscleLengthBias.Lengthened, lift.muscleLengthBias)
        assertEquals(FatigueProfile.Medium, lift.fatigueProfile)
        assertEquals(LoadType.Weighted, lift.loadType)
        assertEquals(150, lift.defaultRestSeconds)
    }

    @Test
    @DisplayName("Given blank name when added then fails without writing")
    fun rejectsBlankName() = runTest {
        val repo = FakeLiftRepository()
        val useCase = AddCustomLiftUseCase(repo, FakeLoggingPort())

        val result =
            useCase(
                CustomLiftDefinition(
                    name = "   ",
                    movementPatterns = setOf(MovementPattern.SquatPattern),
                    primaryMuscles = setOf(MuscleGroup.Quad),
                ),
            )

        assertTrue(result.isFailure)
        assertTrue(repo.currentLifts.isEmpty())
    }

    @Test
    @DisplayName("Given empty movement patterns when added then fails")
    fun rejectsEmptyPatterns() = runTest {
        val repo = FakeLiftRepository()
        val useCase = AddCustomLiftUseCase(repo, FakeLoggingPort())

        val result =
            useCase(
                CustomLiftDefinition(
                    name = "Mystery Move",
                    movementPatterns = emptySet(),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                ),
            )

        assertTrue(result.isFailure)
        assertTrue(repo.currentLifts.isEmpty())
    }

    @Test
    @DisplayName("Given empty primary muscles when added then fails")
    fun rejectsEmptyPrimaryMuscles() = runTest {
        val repo = FakeLiftRepository()
        val useCase = AddCustomLiftUseCase(repo, FakeLoggingPort())

        val result =
            useCase(
                CustomLiftDefinition(
                    name = "Mystery Move",
                    movementPatterns = setOf(MovementPattern.SquatPattern),
                    primaryMuscles = emptySet(),
                ),
            )

        assertTrue(result.isFailure)
        assertTrue(repo.currentLifts.isEmpty())
    }

    @Test
    @DisplayName("Given rest outside allowed range when added then fails")
    fun rejectsInvalidRest() = runTest {
        val repo = FakeLiftRepository()
        val useCase = AddCustomLiftUseCase(repo, FakeLoggingPort())

        val result =
            useCase(
                CustomLiftDefinition(
                    name = "Mystery Move",
                    movementPatterns = setOf(MovementPattern.SquatPattern),
                    primaryMuscles = setOf(MuscleGroup.Quad),
                    defaultRestSeconds = 9999,
                ),
            )

        assertTrue(result.isFailure)
        assertTrue(repo.currentLifts.isEmpty())
    }

    @Test
    @DisplayName("Given blank equipment name when added then equipment is null")
    fun blankEquipmentBecomesNull() = runTest {
        val repo = FakeLiftRepository()
        val useCase = AddCustomLiftUseCase(repo, FakeLoggingPort())

        val result =
            useCase(
                CustomLiftDefinition(
                    name = "Cable Fly",
                    movementPatterns = setOf(MovementPattern.ShoulderAdduction),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    equipmentName = "   ",
                ),
            )

        assertTrue(result.isSuccess)
        assertNull(repo.currentLifts.single().equipmentName)
        assertNull(repo.currentLifts.single().equipmentId)
    }
}
