package com.stratum.liftconfig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The equipment editor reuses the shared increments shell. The mapper must
 * forward the AI scan gate and request state, otherwise the scan entry stays
 * hidden even with the launch flag on.
 */
class EquipmentIncrementsRouteMapperTest {

    @Test
    @DisplayName("Given scan available when mapped then shell shows the scan entry")
    fun forwardsScanGateAndState() {
        val scanned =
            IncrementScanState.Scanned(
                tierDrafts = listOf(IncrementTierDraft("0", "40", "2.5", openEnded = false)),
                microStopDrafts = listOf("2.5"),
            )
        val equipmentState =
            EquipmentIncrementsUiState(
                equipmentId = "cable",
                equipmentName = "Cables",
                tiers = listOf(IncrementTierDraft("0", "40", "2.5", openEnded = false)),
                microStops = listOf("2.5"),
                unitLabel = "lb",
                scanAvailable = true,
                scanUi = scanned,
            )

        val shell = equipmentState.toLiftIncrementsUiState(fallbackTitle = "Equipment")

        assertTrue(shell.scanAvailable)
        assertEquals(scanned, shell.scanUi)
        assertEquals("cable", shell.liftId)
        assertEquals("Cables", shell.liftName)
        assertEquals(equipmentState.tiers, shell.tiers)
        assertEquals(equipmentState.microStops, shell.microStops)
    }

    @Test
    @DisplayName("Given scan unavailable when mapped then shell hides the scan entry")
    fun hidesScanEntryWhenFlagOff() {
        val equipmentState =
            EquipmentIncrementsUiState(
                equipmentId = "cable",
                equipmentName = "",
                scanAvailable = false,
                scanUi = IncrementScanState.Idle,
            )

        val shell = equipmentState.toLiftIncrementsUiState(fallbackTitle = "Equipment")

        assertFalse(shell.scanAvailable)
        assertEquals(IncrementScanState.Idle, shell.scanUi)
        assertEquals("Equipment", shell.liftName)
    }
}
