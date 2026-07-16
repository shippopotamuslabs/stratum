package com.stratum.domain.program.generation.universe

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * FPR-S4-002 / FPR-S4-M001 — golden universe keys use Accessory, reject Isolation.
 *
 * Pins the maintenance golden key set from [SlotUniverseGoldenTest] without rewriting
 * the full parameterized golden file during TDD Red (Implementation updates goldens).
 */
class SlotUniverseAccessoryGoldenContractTest {

    @Test
    @DisplayName("FPR-S4-002 universe golden keys use Accessory role segment")
    fun fprS4002_goldenKeysUseAccessory() {
        val keys = SlotUniverseGoldenTest.MAINTENANCE_KEYS
        assertTrue("ElbowExtension|Tricep|Accessory|false|Any" in keys)
        assertTrue("ElbowFlexion|Bicep|Accessory|false|Any" in keys)
        assertTrue("KneeExtension|Quad|Accessory|false|Any" in keys)
        assertTrue("KneeFlexion|Hamstring|Accessory|false|Any" in keys)
        assertTrue("ShoulderAbduction|LateralDeltoid|Accessory|false|Any" in keys)
        assertFalse(
            keys.any { "|Isolation|" in it },
            "golden set must not contain |Isolation| role segment",
        )
    }

    @Test
    @DisplayName("FPR-S4-M001 golden equality fails if Isolation keys remain")
    fun fprS4M001_isolationKeysRejected() {
        val golden = SlotUniverseGoldenTest.MAINTENANCE_KEYS
        val polluted =
            golden + "ElbowFlexion|Bicep|Isolation|false|Any"
        assertFalse(golden == polluted)
        assertFalse(
            "ElbowFlexion|Bicep|Isolation|false|Any" in golden,
            "production golden set must not contain Isolation key",
        )
    }
}
