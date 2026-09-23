package com.stratum.domain.lift

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Permanently dismisses one equipment-increment proposal fingerprint — used when the
 * user resolves the proposal by hand (e.g. saving corrected tiers from the proposal's
 * "Edit steps" editor) so the card does not return even if the saved tiers still miss
 * logged weights. Cancellation paths must not call this.
 */
class DismissEquipmentProposalUseCase @Inject constructor(
    private val preferences: EquipmentProposalPreferences,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(fingerprint: String): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to dismiss equipment proposal") {
            preferences.dismiss(fingerprint)
        }

    private companion object {
        const val TAG = "DismissEquipmentProposal"
    }
}
