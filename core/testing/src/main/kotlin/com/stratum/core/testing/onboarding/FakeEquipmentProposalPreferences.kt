package com.stratum.core.testing.onboarding

import com.stratum.application.lift.EquipmentProposalPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [EquipmentProposalPreferences] for domain analyze / complete-screen tests.
 */
class FakeEquipmentProposalPreferences(
    enabled: Boolean = true,
) : EquipmentProposalPreferences {
    private val dismissed = mutableSetOf<String>()
    private val enabledFlow = MutableStateFlow(enabled)

    /** Test hook: current dismissed fingerprints. */
    val dismissedFingerprints: Set<String>
        get() = dismissed.toSet()

    fun seedDismissed(vararg fingerprints: String) {
        dismissed.clear()
        dismissed.addAll(fingerprints)
    }

    fun seedEnabled(enabled: Boolean) {
        enabledFlow.value = enabled
    }

    override fun observeEnabled(): Flow<Boolean> = enabledFlow.asStateFlow()

    override suspend fun isEnabled(): Boolean = enabledFlow.value

    override suspend fun setEnabled(enabled: Boolean) {
        enabledFlow.value = enabled
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
