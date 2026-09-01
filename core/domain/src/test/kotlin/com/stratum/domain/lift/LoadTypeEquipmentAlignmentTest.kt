package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoadTypeEquipmentAlignmentTest {
    @Test
    @DisplayName("Bodyweight equipment forces LoadType.Bodyweight")
    fun bodyweightEquipmentForcesBodyweightLoad() {
        assertEquals(
            LoadType.Bodyweight,
            LoadType.Weighted.alignedWithEquipment(SeedEquipmentIds.Bodyweight),
        )
        assertEquals(
            LoadType.Bodyweight,
            LoadType.Assisted.alignedWithEquipment(SeedEquipmentIds.Bodyweight),
        )
    }

    @Test
    @DisplayName("Leaving bodyweight equipment restores Weighted from Bodyweight")
    fun leavingBodyweightRestoresWeighted() {
        assertEquals(
            LoadType.Weighted,
            LoadType.Bodyweight.alignedWithEquipment(SeedEquipmentIds.Default),
        )
        assertEquals(
            LoadType.Weighted,
            LoadType.Bodyweight.alignedWithEquipment(SeedEquipmentIds.Cable),
        )
    }

    @Test
    @DisplayName("Assisted is preserved when assigning non-bodyweight equipment")
    fun assistedPreservedOnWeightedEquipment() {
        assertEquals(
            LoadType.Assisted,
            LoadType.Assisted.alignedWithEquipment(SeedEquipmentIds.PinLoadedMachine),
        )
    }

    @Test
    @DisplayName("Null equipment leaves load type unchanged")
    fun nullEquipmentLeavesLoadType() {
        assertEquals(LoadType.Weighted, LoadType.Weighted.alignedWithEquipment(null))
        assertEquals(LoadType.Bodyweight, LoadType.Bodyweight.alignedWithEquipment(null))
        assertEquals(LoadType.Assisted, LoadType.Assisted.alignedWithEquipment(null))
    }
}
