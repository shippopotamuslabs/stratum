package com.stratum.application.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.CatalogNames
import com.stratum.domain.lift.EQUIPMENT_NAME_REQUIRED_MESSAGE
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.defaultSingleTiers

class AddCustomEquipmentUseCaseTest {

    private val lb = Weight.WeightUnit.Lb
    private val tiers =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb)))

    private lateinit var equipmentRepo: FakeEquipmentRepository
    private lateinit var subject: AddCustomEquipmentUseCase

    @BeforeEach
    fun setup() {
        equipmentRepo = FakeEquipmentRepository()
        subject =
            AddCustomEquipmentUseCase(
                repository = equipmentRepo,
                loggingPort = FakeLoggingPort(),
            )
    }

    @Test
    @DisplayName("Given stops when added then the custom equipment carries the knob")
    fun addCustomCarriesMicro() = runTest {
        val stops = listOf(Weight(2.5, lb), Weight(5.0, lb))

        val result = subject("Pin stack", tiers, stops)

        assertTrue(result is AddCustomEquipmentResult.Success)
        assertEquals(
            stops,
            equipmentRepo.getEquipment((result as AddCustomEquipmentResult.Success).equipmentId)
                ?.microIncrements,
        )
    }

    @Test
    @DisplayName("Given no stops when added then the custom equipment has no knob")
    fun addCustomDefaultsToNoMicro() = runTest {
        val result = subject("Plain stack", tiers)

        assertTrue(result is AddCustomEquipmentResult.Success)
        assertEquals(
            emptyList<Weight>(),
            equipmentRepo.getEquipment((result as AddCustomEquipmentResult.Success).equipmentId)
                ?.microIncrements,
        )
    }

    @Test
    @DisplayName("Given stop above the largest band when added then InvalidMicro and no write")
    fun addCustomRejectsInvalidMicro() = runTest {
        val before = equipmentRepo.current.size
        val result = subject("Pin stack", tiers, listOf(Weight(20.0, lb)))

        assertTrue(result is AddCustomEquipmentResult.InvalidMicro)
        assertEquals(before, equipmentRepo.current.size)
    }

    @Test
    @DisplayName(
        "Given stops legal for the default band when added without tiers then success",
    )
    fun addCustomWithoutTiersValidatesAgainstDefaultBand() = runTest {
        // Empty tiers persist as the default single 5 lb band, so 2.5 must validate.
        val result = subject("Pin stack", emptyList(), listOf(Weight(2.5, lb)))

        assertTrue(result is AddCustomEquipmentResult.Success)
        val stored =
            equipmentRepo.getEquipment((result as AddCustomEquipmentResult.Success).equipmentId)!!
        assertEquals(listOf(Weight(2.5, lb)), stored.microIncrements)
        assertEquals(defaultSingleTiers(), stored.incrementTiers)
    }

    @Test
    @DisplayName(
        "Given stops above the default band when added without tiers then InvalidMicro and no write",
    )
    fun addCustomWithoutTiersRejectsStopsAboveDefaultBand() = runTest {
        val before = equipmentRepo.current.size
        val result = subject("Pin stack", emptyList(), listOf(Weight(20.0, lb)))

        assertTrue(result is AddCustomEquipmentResult.InvalidMicro)
        assertEquals(before, equipmentRepo.current.size)
    }

    @Test
    @DisplayName("Given a blank name when added then InvalidName and no write (STR-243)")
    fun addCustomRejectsBlankName() = runTest {
        val before = equipmentRepo.current.size
        val result = subject("   ", tiers)

        assertEquals(
            AddCustomEquipmentResult.InvalidName(
                CatalogNames.Validation.Invalid(EQUIPMENT_NAME_REQUIRED_MESSAGE),
            ),
            result,
        )
        assertEquals(before, equipmentRepo.current.size)
    }

    @Test
    @DisplayName("Given gapped tiers when added then InvalidTiers and no write (STR-243)")
    fun addCustomRejectsInvalidTiers() = runTest {
        val before = equipmentRepo.current.size
        val gapped =
            listOf(
                IncrementTier(Weight(0.0, lb), Weight(40.0, lb), Weight(5.0, lb)),
                IncrementTier(Weight(50.0, lb), null, Weight(10.0, lb)),
            )
        val result = subject("Pin stack", gapped)

        assertTrue(result is AddCustomEquipmentResult.InvalidTiers)
        assertEquals(before, equipmentRepo.current.size)
    }

    @Test
    @DisplayName("Given a repository failure when added then PersistenceFailed (STR-243)")
    fun addCustomPersistenceFailure() = runTest {
        equipmentRepo.failNextWrite = true
        val result = subject("Pin stack", tiers)

        assertEquals(AddCustomEquipmentResult.PersistenceFailed, result)
    }
}
