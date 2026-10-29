package com.stratum.application.lift

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.lift.EquipmentIncrementProposal
import com.stratum.domain.lift.equipmentIncrementProposalFingerprint

/**
 * Persists feature enablement and per-card deferrals for post-workout
 * [EquipmentIncrementProposal] cards.
 *
 * Dismiss keys are stable fingerprints from [equipmentIncrementProposalFingerprint] — not
 * display strings. Implementations use DataStore (not Room) so proposal UI state never invents
 * schema.
 *
 * Implementations should cap stored fingerprints (LRU / take-last) so "Not now" cannot grow
 * unbounded across years of training.
 */
interface EquipmentProposalPreferences {
    /** Observes whether post-workout equipment-increment cards are enabled (default true). */
    fun observeEnabled(): Flow<Boolean>

    /** Current enablement snapshot for analyze paths. */
    suspend fun isEnabled(): Boolean

    /** User master switch in Settings — when false, analyze emits nothing. */
    suspend fun setEnabled(enabled: Boolean)

    /** True when the user dismissed / deferred this fingerprint ("Not now"). */
    suspend fun isDismissed(fingerprint: String): Boolean

    /** Persist a permanent dismiss for [fingerprint] until signals change (new fingerprint). */
    suspend fun dismiss(fingerprint: String)

    /** Clears a dismiss (tests / rare "reset" paths). No-op when not dismissed. */
    suspend fun clearDismiss(fingerprint: String)
}

/** Soft cap for dismissed fingerprints retained in DataStore (oldest dropped first). */
const val MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS: Int = 100
