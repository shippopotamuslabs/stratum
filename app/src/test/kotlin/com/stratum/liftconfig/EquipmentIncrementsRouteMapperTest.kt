package com.stratum.liftconfig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The equipment editor reuses the shared increments shell. The mapper must
 * pass the shared [IncrementsEditorData] through untouched (so new shell
 * fields, e.g. the AI scan gate, flow without mapper edits) while applying
 * equipment-specific presets.
 */
class EquipmentIncrementsRouteMapperTest {

    @Test
    @DisplayName("Given equipment state when mapped then the shared editor passes through")
    fun forwardsSharedEditorUntouched() {
        val editor =
            IncrementsEditorData(
                tiers = listOf(IncrementTierDraft("0", "40", "2.5", openEnded = false)),
                microStops = listOf("2.5"),
                unitLabel = "lb",
                scanAvailable = true,
                scanUi =
                    IncrementScanState.Scanned(
                        tierDrafts =
                            listOf(IncrementTierDraft("0", "40", "2.5", openEnded = false)),
                        microStopDrafts = listOf("2.5"),
                    ),
            )
        val equipmentState =
            EquipmentIncrementsUiState(
                equipmentId = "cable",
                equipmentName = "Cables",
                editor = editor,
            )

        val shell = equipmentState.toLiftIncrementsUiState(fallbackTitle = "Equipment")

        assertSame(editor, shell.editor)
        assertEquals("cable", shell.liftId)
        assertEquals("Cables", shell.liftName)
        assertNull(shell.inheritanceBanner)
        assertFalse(shell.showResetToEquipment)
    }

    @Test
    @DisplayName("Given a blank equipment name when mapped then the fallback title applies")
    fun fallbackTitleApplies() {
        val shell =
            EquipmentIncrementsUiState(equipmentId = "cable")
                .toLiftIncrementsUiState(fallbackTitle = "Equipment")

        assertEquals("Equipment", shell.liftName)
        assertEquals(IncrementsEditorData(), shell.editor)
    }
}
