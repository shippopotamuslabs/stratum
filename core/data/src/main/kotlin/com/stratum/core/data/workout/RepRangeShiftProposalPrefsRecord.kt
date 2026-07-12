package com.stratum.core.data.workout

import kotlinx.serialization.Serializable

/**
 * Typed DataStore root for dismissed rep-range shift proposal fingerprints.
 * Not Room — proposal UI state must not invent schema (schema lock).
 */
@Serializable
data class RepRangeShiftProposalPrefsRecord(
    /** Master switch for post-workout rep-range shift cards (default on). */
    val enabled: Boolean = true,
    val dismissedFingerprints: List<String> = emptyList(),
)
