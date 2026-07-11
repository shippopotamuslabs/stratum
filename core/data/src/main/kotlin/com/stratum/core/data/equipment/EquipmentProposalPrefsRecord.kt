package com.stratum.core.data.equipment

import kotlinx.serialization.Serializable

/**
 * Typed DataStore root for dismissed equipment-increment proposal fingerprints.
 * Not Room — proposal UI state must not invent schema (schema lock).
 */
@Serializable
data class EquipmentProposalPrefsRecord(
    val dismissedFingerprints: List<String> = emptyList(),
)
