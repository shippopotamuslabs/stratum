package com.stratum.liftconfig

/**
 * Shared increments-editor content rendered by [LiftIncrementsScreen].
 *
 * Both the per-lift editor ([LiftIncrementsUiState]) and the shared-equipment
 * editor ([EquipmentIncrementsUiState]) embed this instead of duplicating the
 * fields, so new shell fields flow to the equipment route without mapper
 * edits. Screen-specific chrome (owner id/name, banners, affected lifts)
 * stays on the owning UiState.
 */
data class IncrementsEditorData(
    val tiers: List<IncrementTierDraft> = emptyList(),
    /**
     * Adder stop drafts (display-unit strings); empty = no adder. Owned by the
     * caller: the equipment editor manages them (see its draft flow); the lift
     * editor leaves the default until it wires drafts.
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
)
