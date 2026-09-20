package com.stratum.domain.lift.identity

/**
 * Type-safe identifier for a single row in the per-lift catalog note list
 * (multi-note successor of the legacy `lifts.pinned_note` column).
 */
@JvmInline
value class LiftCatalogNoteId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "LiftCatalogNoteId cannot be blank" }
    }
}
