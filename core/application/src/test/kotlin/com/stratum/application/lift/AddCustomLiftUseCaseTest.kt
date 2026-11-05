package com.stratum.application.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.identity.LiftId
import kotlin.coroutines.cancellation.CancellationException
import org.junit.jupiter.api.Assertions.assertThrows
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias

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

        assertTrue(result is AddCustomLiftResult.Success)
        val lift = repo.currentLifts.single { it.id == (result as AddCustomLiftResult.Success).liftId }
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

        assertTrue(result is AddCustomLiftResult.InvalidName)
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

        assertTrue(result is AddCustomLiftResult.InvalidField)
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

        assertTrue(result is AddCustomLiftResult.InvalidField)
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

        assertEquals(
            AddCustomLiftResult.InvalidField(
                CustomLiftFieldValidation.Invalid(
                    "Rest must be between 0 and 600 seconds",
                ),
            ),
            result,
        )
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

        assertTrue(result is AddCustomLiftResult.Success)
        assertNull(repo.currentLifts.single().equipmentName)
        assertNull(repo.currentLifts.single().equipmentId)
    }

    @Test
    @DisplayName("Given a failing repository write when added then PersistenceFailed and one error log")
    fun persistenceFailureOnWrite() = runTest {
        val repo = FakeLiftRepository()
        val logging = FakeLoggingPort()
        val useCase =
            AddCustomLiftUseCase(
                ThrowingAddLiftRepository(repo, IllegalStateException("write failed")),
                logging,
            )

        val result =
            useCase(
                CustomLiftDefinition(
                    name = "Mystery Move",
                    movementPatterns = setOf(MovementPattern.SquatPattern),
                    primaryMuscles = setOf(MuscleGroup.Quad),
                ),
            )

        assertEquals(AddCustomLiftResult.PersistenceFailed, result)
        assertEquals(1, logging.errors.size)
        assertTrue(repo.currentLifts.isEmpty())
    }

    @Test
    @DisplayName("Given a cancelling repository write when added then cancellation propagates")
    fun cancellationPropagates() {
        val repo = FakeLiftRepository()
        val useCase =
            AddCustomLiftUseCase(
                ThrowingAddLiftRepository(repo, CancellationException("cancelled")),
                FakeLoggingPort(),
            )

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase(
                    CustomLiftDefinition(
                        name = "Mystery Move",
                        movementPatterns = setOf(MovementPattern.SquatPattern),
                        primaryMuscles = setOf(MuscleGroup.Quad),
                    ),
                )
            }
        }
    }

    /** Delegates to [delegate] except [addCustomLift], which throws [failure]. */
    private class ThrowingAddLiftRepository(
        delegate: FakeLiftRepository,
        private val failure: Throwable,
    ) : LiftRepository by delegate {
        override suspend fun addCustomLift(definition: CustomLiftDefinition): LiftId =
            throw failure
    }
}
