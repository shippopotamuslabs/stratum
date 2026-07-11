package com.stratum.core.testing.onboarding

import com.stratum.domain.lift.EquipmentProposalPreferences

/**
 * In-memory [EquipmentProposalPreferences] for domain analyze / complete-screen tests.
 */
class FakeEquipmentProposalPreferences : EquipmentProposalPreferences {
    private val dismissed = mutableSetOf<String>()

    /** Test hook: current dismissed fingerprints. */
    val dismissedFingerprints: Set<String>
        get() = dismissed.toSet()

    fun seedDismissed(vararg fingerprints: String) {
        dismissed.clear()
        dismissed.addAll(fingerprints)
    }

    override suspend fun isDismissed(fingerprint: String): Boolean =
        fingerprint in dismissed

    override suspend fun dismiss(fingerprint: String) {
        if (fingerprint.isNotBlank()) dismissed.add(fingerprint)
    }

    override suspend fun clearDismiss(fingerprint: String) {
        dismissed.remove(fingerprint)
    }
}
