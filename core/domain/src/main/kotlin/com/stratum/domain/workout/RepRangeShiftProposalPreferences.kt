package com.stratum.domain.workout

import kotlinx.coroutines.flow.Flow

/**
 * Persists feature enablement and per-card deferrals for post-workout [RepRangeShiftProposal]
 * cards.
 *
 * Dismiss keys are stable fingerprints from [repRangeShiftProposalFingerprint] — not display
 * strings. Implementations use DataStore (not Room) so proposal UI state never invents schema.
 */
interface RepRangeShiftProposalPreferences {
    /** Observes whether post-workout rep-range shift cards are enabled (default true). */
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
