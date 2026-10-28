package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    @DisplayName("Alignment predicate accepts exactly the fixed points of alignedWithEquipment")
    fun alignmentPredicateMatchesFixedPoints() {
        assertTrue(LoadType.Bodyweight.isAlignedWithEquipment(SeedEquipmentIds.Bodyweight))
        assertTrue(LoadType.Weighted.isAlignedWithEquipment(SeedEquipmentIds.FixedBarbell))
        assertTrue(LoadType.Assisted.isAlignedWithEquipment(SeedEquipmentIds.Cable))
        assertTrue(LoadType.Weighted.isAlignedWithEquipment(null))
        assertTrue(LoadType.Bodyweight.isAlignedWithEquipment(null))
        assertFalse(LoadType.Weighted.isAlignedWithEquipment(SeedEquipmentIds.Bodyweight))
        assertFalse(LoadType.Assisted.isAlignedWithEquipment(SeedEquipmentIds.Bodyweight))
        assertFalse(LoadType.Bodyweight.isAlignedWithEquipment(SeedEquipmentIds.FixedBarbell))
    }

    @Test
    @DisplayName("Require throws on a contradictory pair and passes on consistent pairs")
    fun requireAlignmentThrowsOnContradiction() {
        assertThrows(IllegalArgumentException::class.java) {
            requireLoadTypeEquipmentAlignment(LoadType.Weighted, SeedEquipmentIds.Bodyweight)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireLoadTypeEquipmentAlignment(LoadType.Bodyweight, SeedEquipmentIds.Default)
        }
        // Consistent pairs pass silently.
        requireLoadTypeEquipmentAlignment(LoadType.Bodyweight, SeedEquipmentIds.Bodyweight)
        requireLoadTypeEquipmentAlignment(LoadType.Weighted, SeedEquipmentIds.FixedBarbell)
        requireLoadTypeEquipmentAlignment(LoadType.Assisted, SeedEquipmentIds.Cable)
        requireLoadTypeEquipmentAlignment(LoadType.Weighted, null)
    }
}
