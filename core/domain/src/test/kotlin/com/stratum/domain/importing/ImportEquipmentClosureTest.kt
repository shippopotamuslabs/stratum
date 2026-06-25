package com.stratum.domain.importing

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.identity.LiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportEquipmentClosureTest {
    @Test
    fun `tier B includes lifts whose equipment is a subset of confirmed equipment`() {
        val catalog = mapOf(
            LiftId("cable-row") to setOf(GymEquipment.CableStation, GymEquipment.SeatedCableRowStation),
            LiftId("cable-fly") to setOf(GymEquipment.CableStation, GymEquipment.CableCrossover),
            LiftId("leg-press") to setOf(GymEquipment.LegPress),
            LiftId("bench") to setOf(GymEquipment.Barbell, GymEquipment.FlatBench, GymEquipment.PowerRack),
        )
        val confirmed = setOf(GymEquipment.CableStation, GymEquipment.SeatedCableRowStation, GymEquipment.CableCrossover)

        val tierB = ImportEquipmentClosure.tierBLiftIds(catalog, confirmed, tierAIds = emptySet())

        assertTrue(LiftId("cable-row") in tierB)
        assertTrue(LiftId("cable-fly") in tierB)
        assertFalse(LiftId("leg-press") in tierB)
        assertFalse(LiftId("bench") in tierB)
    }

    @Test
    fun `tier B excludes Tier A ids even when equipment matches`() {
        val id = LiftId("cable-row")
        val catalog = mapOf(id to setOf(GymEquipment.CableStation))
        val confirmed = setOf(GymEquipment.CableStation)

        val tierB = ImportEquipmentClosure.tierBLiftIds(catalog, confirmed, tierAIds = setOf(id))

        assertFalse(id in tierB)
    }

    @Test
    fun `tier B is empty when confirmed equipment is empty`() {
        val catalog = mapOf(LiftId("x") to setOf(GymEquipment.Barbell))
        assertEquals(emptySet<LiftId>(), ImportEquipmentClosure.tierBLiftIds(catalog, emptySet(), emptySet()))
    }

    @Test
    fun `tier B skips lifts with no declared equipment`() {
        val catalog = mapOf(
            LiftId("tagged") to setOf(GymEquipment.Barbell),
            LiftId("untagged") to emptySet<GymEquipment>(),
        )
        val tierB = ImportEquipmentClosure.tierBLiftIds(catalog, setOf(GymEquipment.Barbell), emptySet())
        assertTrue(LiftId("tagged") in tierB)
        assertFalse(LiftId("untagged") in tierB)
    }

    @Test
    fun `derive gym returns Commercial when any commercial indicator is confirmed`() {
        assertEquals(
            GymPreset.CommercialGym,
            ImportEquipmentClosure.deriveGymFromEquipment(setOf(GymEquipment.Barbell, GymEquipment.LegPress)),
        )
    }

    @Test
    fun `derive gym returns Garage when no commercial indicator is confirmed`() {
        assertEquals(
            GymPreset.GarageGym,
            ImportEquipmentClosure.deriveGymFromEquipment(setOf(GymEquipment.Barbell, GymEquipment.Dumbbell, GymEquipment.FlatBench)),
        )
    }

    @Test
    fun `sparse fallback returns derived gym when tier B count is below threshold`() {
        val gym = ImportEquipmentClosure.sparseImportFallbackGym(
            tierBCount = ImportEquipmentClosure.MIN_TIER_B_FOR_EXPANSION - 1,
            confirmedEquipment = setOf(GymEquipment.CableStation),
        )
        assertEquals(GymPreset.CommercialGym, gym)
    }

    @Test
    fun `sparse fallback returns null when tier B count meets threshold`() {
        assertNull(
            ImportEquipmentClosure.sparseImportFallbackGym(
                tierBCount = ImportEquipmentClosure.MIN_TIER_B_FOR_EXPANSION,
                confirmedEquipment = setOf(GymEquipment.CableStation),
            ),
        )
    }
}
