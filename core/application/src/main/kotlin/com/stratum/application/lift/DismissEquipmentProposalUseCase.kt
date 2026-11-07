package com.stratum.application.lift

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of dismissing an equipment proposal (STR-243; STR-237 Route (i)).
 * The originating throwable of a failed write rides the [PersistenceFailed]
 * branch as an [OperationalDiagnostic] (legacy tag + rendered message, real
 * throwable as cause); the presentation boundary drains it exactly once
 * through its module's DiagnosticDrain. Coroutine cancellation is not a
 * branch: it propagates.
 */
sealed interface DismissEquipmentProposalResult {
    data object Success : DismissEquipmentProposalResult

    /** The dismissal write failed (preferences store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : DismissEquipmentProposalResult
}

/**
 * Permanently dismisses one equipment-increment proposal fingerprint — used when the
 * user resolves the proposal by hand (e.g. saving corrected tiers from the proposal's
 * "Edit steps" editor) so the card does not return even if the saved tiers still miss
 * logged weights. Cancellation paths must not call this.
 */
class DismissEquipmentProposalUseCase @Inject constructor(
    private val preferences: EquipmentProposalPreferences,
) {
    suspend operator fun invoke(fingerprint: String): DismissEquipmentProposalResult =
        try {
            preferences.dismiss(fingerprint)
            DismissEquipmentProposalResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            DismissEquipmentProposalResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to dismiss equipment proposal: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "DismissEquipmentProposal"
    }
}
