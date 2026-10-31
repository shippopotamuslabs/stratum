package com.stratum.core.infrastructure.repository

import android.content.Context
import com.stratum.core.infrastructure.prefs.DismissedFingerprintStore
import com.stratum.core.infrastructure.workout.repRangeShiftProposalPreferencesDataStore
import com.stratum.domain.workout.MAX_DISMISSED_REP_RANGE_SHIFT_FINGERPRINTS
import com.stratum.application.workout.RepRangeShiftProposalPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [RepRangeShiftProposalPreferences] for post-workout enablement + dismissals.
 * Caps retained fingerprints at [MAX_DISMISSED_REP_RANGE_SHIFT_FINGERPRINTS].
 */
@Singleton
class DataStoreRepRangeShiftProposalPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : RepRangeShiftProposalPreferences {

    override fun observeEnabled(): Flow<Boolean> =
        context.repRangeShiftProposalPreferencesDataStore.data.map { it.enabled }

    override suspend fun isEnabled(): Boolean =
        context.repRangeShiftProposalPreferencesDataStore.data.first().enabled

    override suspend fun setEnabled(enabled: Boolean) {
        context.repRangeShiftProposalPreferencesDataStore.updateData { current ->
            current.copy(enabled = enabled)
        }
    }

    override suspend fun isDismissed(fingerprint: String): Boolean {
        if (fingerprint.isBlank()) return false
        val record = context.repRangeShiftProposalPreferencesDataStore.data.first()
        return fingerprint in record.dismissedFingerprints
    }

    override suspend fun dismiss(fingerprint: String) {
        context.repRangeShiftProposalPreferencesDataStore.updateData { current ->
            current.copy(
                dismissedFingerprints =
                    DismissedFingerprintStore.withDismissed(
                        current = current.dismissedFingerprints,
                        fingerprint = fingerprint,
                        maxRetained = MAX_DISMISSED_REP_RANGE_SHIFT_FINGERPRINTS,
                    ),
            )
        }
    }

    override suspend fun clearDismiss(fingerprint: String) {
        context.repRangeShiftProposalPreferencesDataStore.updateData { current ->
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
        context.repRangeShiftProposalPreferencesDataStore.updateData {
            it.copy(enabled = true, dismissedFingerprints = emptyList())
        }
    }
}
