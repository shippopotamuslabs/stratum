package com.stratum.application.lift

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.CustomLiftDetails
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias

class UpdateCustomLiftDetailsUseCaseTest {

    private val liftId = LiftId("my-cable-fly")
    private val cableId = EquipmentId("cable")

    @Test
    @DisplayName("Given custom lift when details updated then repository reflects them")
    fun updatesCustomDetails() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = true)))
        repo.equipmentNamesById = mapOf(cableId to "Cables")
        val useCase = UpdateCustomLiftDetailsUseCase(repo)

        val result =
            useCase(
                id = liftId,
                details =
                    CustomLiftDetails(
                        movementPatterns = setOf(MovementPattern.ShoulderAdduction),
                        muscleLengthBias = MuscleLengthBias.Lengthened,
                        fatigueProfile = FatigueProfile.Low,
                        loadType = LoadType.Weighted,
                        equipmentId = cableId,
                    ),
            )

        assertEquals(UpdateCustomLiftDetailsResult.Success, result)
        val lift = repo.currentLifts.single()
        assertEquals(setOf(MovementPattern.ShoulderAdduction), lift.movementPatterns)
        assertEquals(MuscleLengthBias.Lengthened, lift.muscleLengthBias)
        assertEquals(cableId, lift.equipmentId)
        assertEquals("Cables", lift.equipmentName)
    }

    @Test
    @DisplayName("Given catalog lift when details updated then fails without writing")
    fun rejectsCatalogLift() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = false)))
        val useCase = UpdateCustomLiftDetailsUseCase(repo)

        val result =
            useCase(
                id = liftId,
                details =
                    CustomLiftDetails(
                        movementPatterns = setOf(MovementPattern.HipHinge),
                        muscleLengthBias = MuscleLengthBias.Shortened,
                        fatigueProfile = FatigueProfile.High,
                        loadType = LoadType.Bodyweight,
                        equipmentId = EquipmentId("double-weight-plates"),
                    ),
            )

        assertEquals(UpdateCustomLiftDetailsResult.CatalogLiftNotEditable(liftId), result)
        val lift = repo.currentLifts.single()
        assertEquals(setOf(MovementPattern.HorizontalPress), lift.movementPatterns)
        assertNull(lift.equipmentId)
    }

    @Test
    @DisplayName("Given empty movement patterns when updated then fails")
    fun rejectsEmptyPatterns() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = true)))
        val useCase = UpdateCustomLiftDetailsUseCase(repo)

        val result =
            useCase(
                id = liftId,
                details =
                    CustomLiftDetails(
                        movementPatterns = emptySet(),
                        muscleLengthBias = MuscleLengthBias.MidRange,
                        fatigueProfile = FatigueProfile.Low,
                        loadType = LoadType.Weighted,
                        equipmentId = null,
                    ),
            )

        assertEquals(
            UpdateCustomLiftDetailsResult.InvalidField(
                CustomLiftFieldValidation.Invalid(
                    CustomLiftFieldRules.MOVEMENT_PATTERNS_REQUIRED_MESSAGE,
                ),
            ),
            result,
        )
    }

    @Test
    @DisplayName("Given missing lift when details updated then LiftNotFound")
    fun missingLiftFails() = runTest {
        val repo = FakeLiftRepository()
        val useCase = UpdateCustomLiftDetailsUseCase(repo)

        val result =
            useCase(
                id = liftId,
                details =
                    CustomLiftDetails(
                        movementPatterns = setOf(MovementPattern.HipHinge),
                        muscleLengthBias = MuscleLengthBias.MidRange,
                        fatigueProfile = FatigueProfile.Low,
                        loadType = LoadType.Weighted,
                        equipmentId = null,
                    ),
            )

        assertEquals(UpdateCustomLiftDetailsResult.LiftNotFound(liftId), result)
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
