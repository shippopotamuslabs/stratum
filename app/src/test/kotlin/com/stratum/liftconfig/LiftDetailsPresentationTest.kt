package com.stratum.liftconfig

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.SeedEquipmentIds
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiftDetailsPresentationTest {
    @Test
    @DisplayName("Load type hides only for seed bodyweight equipment already on Bodyweight load")
    fun hidesOnlyWhenBodyweightEquipmentAndLoad() {
        assertFalse(showsLoadType(SeedEquipmentIds.Bodyweight, LoadType.Bodyweight))
        assertTrue(showsLoadType(SeedEquipmentIds.Bodyweight, LoadType.Weighted))
        assertTrue(showsLoadType(SeedEquipmentIds.Bodyweight, LoadType.Assisted))
        assertTrue(showsLoadType(SeedEquipmentIds.Default, LoadType.Bodyweight))
        assertTrue(showsLoadType(SeedEquipmentIds.Default, LoadType.Weighted))
        assertTrue(showsLoadType(null, LoadType.Bodyweight))
    }
}
