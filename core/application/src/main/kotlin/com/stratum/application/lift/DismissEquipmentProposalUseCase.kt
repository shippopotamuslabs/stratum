package com.stratum.application.lift

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of dismissing an equipment proposal (STR-243). The originating
 * throwable of a failed write goes once to the diagnostics channel
 * ([LoggingPort.error]) at the use-case boundary; it never crosses as a
 * payload. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface DismissEquipmentProposalResult {
    data object Success : DismissEquipmentProposalResult

    /** The dismissal write failed (preferences store I/O). */
    data object PersistenceFailed : DismissEquipmentProposalResult
}

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
    suspend operator fun invoke(fingerprint: String): DismissEquipmentProposalResult =
        try {
            preferences.dismiss(fingerprint)
            DismissEquipmentProposalResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to dismiss equipment proposal: ${t.message}", t)
            DismissEquipmentProposalResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "DismissEquipmentProposal"
    }
}
