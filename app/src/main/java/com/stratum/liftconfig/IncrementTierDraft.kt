package com.stratum.liftconfig

/**
 * Editable tier row in the increment editors. Raw strings; [IncrementTierDraftOps] converts
 * to domain increment tiers on save.
 *
 * (STR-86: moved here from the deleted per-lift `LiftIncrementsViewModel` — the draft model
 * is package-wide, shared by the equipment editor, band editor, tiling, and scan prefill.)
 */
data class IncrementTierDraft(
    val lowerBound: String,
    val upperBound: String?,
    val increment: String,
    val openEnded: Boolean,
)
