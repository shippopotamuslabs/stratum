package com.stratum.liftconfig

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

/**
 * Shared increments-editor content rendered by [EquipmentIncrementsRoute].
 *
 * The shared-equipment editor ([EquipmentIncrementsUiState]) embeds this instead of
 * duplicating the fields, so new shell fields flow to the equipment route without
 * mapper edits. Screen-specific chrome (owner id/name, banners, affected lifts)
 * stays on the owning UiState.
 */
data class IncrementsEditorData(
    val tiers: List<IncrementTierDraft> = emptyList(),
    /**
     * Adder stop drafts (display-unit strings); empty = no adder. Owned by the
     * caller: the equipment editor manages them (see its draft flow).
     */
    val microStops: List<String> = emptyList(),
    val validationMessage: String? = null,
    /**
     * Band indexes whose steps miss a proposal's logged sample weights (yellow,
     * non-blocking — saving stays allowed). Empty for all non-proposal entry points.
     */
    val warnedTierIndexes: Set<Int> = emptySet(),
    val isSaving: Boolean = false,
    val saveSucceeded: Boolean = false,
    /** Short unit label for field chrome (`lb` / `kg`) from Settings. */
    val unitLabel: String = "lb",
    /** Launch-flag gate for the AI scan prefill; hidden entry point when false. */
    val scanAvailable: Boolean = false,
    /**
     * Sealed scan request state (idle, in flight, failed, scanned). User-visible
     * text mapping happens at the composable boundary.
     */
    val scanUi: IncrementScanState = IncrementScanState.Idle,
    /** Caller-supplied header content (e.g. the equipment name field); null renders nothing. */
    val headerContent: (@Composable ColumnScope.() -> Unit)? = null,
)
