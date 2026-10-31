package com.stratum.core.infrastructure.repository

import android.content.Context
import com.stratum.core.infrastructure.equipment.equipmentProposalPreferencesDataStore
import com.stratum.core.infrastructure.prefs.DismissedFingerprintStore
import com.stratum.application.lift.EquipmentProposalPreferences
import com.stratum.application.lift.MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [EquipmentProposalPreferences] for post-workout enablement + dismissals.
 * Caps retained fingerprints at [MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS] (LRU-ish:
 * re-dismiss moves a key to the end; oldest keys drop when over capacity).
 */
@Singleton
class DataStoreEquipmentProposalPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : EquipmentProposalPreferences {

    override fun observeEnabled(): Flow<Boolean> =
        context.equipmentProposalPreferencesDataStore.data.map { it.enabled }

    override suspend fun isEnabled(): Boolean =
        context.equipmentProposalPreferencesDataStore.data.first().enabled

    override suspend fun setEnabled(enabled: Boolean) {
        context.equipmentProposalPreferencesDataStore.updateData { current ->
            current.copy(enabled = enabled)
        }
    }

    override suspend fun isDismissed(fingerprint: String): Boolean {
        if (fingerprint.isBlank()) return false
        val record = context.equipmentProposalPreferencesDataStore.data.first()
        return fingerprint in record.dismissedFingerprints
    }

    override suspend fun dismiss(fingerprint: String) {
        context.equipmentProposalPreferencesDataStore.updateData { current ->
            current.copy(
                dismissedFingerprints =
                    DismissedFingerprintStore.withDismissed(
                        current = current.dismissedFingerprints,
                        fingerprint = fingerprint,
                        maxRetained = MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS,
                    ),
            )
        }
    }

    override suspend fun clearDismiss(fingerprint: String) {
        context.equipmentProposalPreferencesDataStore.updateData { current ->
            current.copy(
                dismissedFingerprints =
                    DismissedFingerprintStore.without(
                        current = current.dismissedFingerprints,
                        fingerprint = fingerprint,
                    ),
            )
        }
    }

    /** Test helper — wipe all dismissals (shared DataStore file across process tests). */
    suspend fun clearAll() {
        context.equipmentProposalPreferencesDataStore.updateData {
            it.copy(enabled = true, dismissedFingerprints = emptyList())
        }
    }
}
