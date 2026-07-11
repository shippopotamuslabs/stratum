package com.stratum.core.data.repository

import android.content.Context
import com.stratum.core.data.equipment.equipmentProposalPreferencesDataStore
import com.stratum.domain.lift.EquipmentProposalPreferences
import com.stratum.domain.lift.MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed [EquipmentProposalPreferences] for post-workout proposal dismissals.
 * Caps retained fingerprints at [MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS] (LRU-ish:
 * re-dismiss moves a key to the end; oldest keys drop when over capacity).
 */
@Singleton
class DataStoreEquipmentProposalPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : EquipmentProposalPreferences {

    override suspend fun isDismissed(fingerprint: String): Boolean {
        if (fingerprint.isBlank()) return false
        val record = context.equipmentProposalPreferencesDataStore.data.first()
        return fingerprint in record.dismissedFingerprints
    }

    override suspend fun dismiss(fingerprint: String) {
        val key = fingerprint.trim()
        if (key.isEmpty()) return
        context.equipmentProposalPreferencesDataStore.updateData { current ->
            val without = current.dismissedFingerprints.filterNot { it == key }
            val appended = without + key
            val capped =
                if (appended.size > MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS) {
                    appended.takeLast(MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS)
                } else {
                    appended
                }
            current.copy(dismissedFingerprints = capped)
        }
    }

    override suspend fun clearDismiss(fingerprint: String) {
        val key = fingerprint.trim()
        if (key.isEmpty()) return
        context.equipmentProposalPreferencesDataStore.updateData { current ->
            current.copy(
                dismissedFingerprints = current.dismissedFingerprints.filterNot { it == key },
            )
        }
    }

    /** Test helper — wipe all dismissals (shared DataStore file across process tests). */
    suspend fun clearAll() {
        context.equipmentProposalPreferencesDataStore.updateData {
            it.copy(dismissedFingerprints = emptyList())
        }
    }
}
