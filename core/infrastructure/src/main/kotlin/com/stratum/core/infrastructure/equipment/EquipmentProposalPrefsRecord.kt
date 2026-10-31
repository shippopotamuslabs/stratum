package com.stratum.core.infrastructure.equipment

import kotlinx.serialization.Serializable

/**
 * Typed DataStore root for dismissed equipment-increment proposal fingerprints.
 * Not Room — proposal UI state must not invent schema (schema lock).
 */
@Serializable
data class EquipmentProposalPrefsRecord(
    /** Master switch for post-workout equipment-increment cards (default on). */
    val enabled: Boolean = true,
    val dismissedFingerprints: List<String> = emptyList(),
)
