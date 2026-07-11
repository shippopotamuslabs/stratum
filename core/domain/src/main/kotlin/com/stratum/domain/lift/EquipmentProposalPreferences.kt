package com.stratum.domain.lift

/**
 * Persists user deferrals for post-workout [EquipmentIncrementProposal] cards.
 *
 * Keys are stable fingerprints from [equipmentIncrementProposalFingerprint] — not display
 * strings. Implementations use DataStore (not Room) so proposal UI state never invents schema.
 *
 * Implementations should cap stored fingerprints (LRU / take-last) so "Not now" cannot grow
 * unbounded across years of training.
 */
interface EquipmentProposalPreferences {
    /** True when the user dismissed / deferred this fingerprint ("Not now"). */
    suspend fun isDismissed(fingerprint: String): Boolean

    /** Persist a permanent dismiss for [fingerprint] until signals change (new fingerprint). */
    suspend fun dismiss(fingerprint: String)

    /** Clears a dismiss (tests / rare "reset" paths). No-op when not dismissed. */
    suspend fun clearDismiss(fingerprint: String)
}

/** Soft cap for dismissed fingerprints retained in DataStore (oldest dropped first). */
const val MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS: Int = 100
