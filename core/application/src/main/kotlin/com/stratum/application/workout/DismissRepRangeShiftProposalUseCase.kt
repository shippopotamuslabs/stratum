package com.stratum.application.workout

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of dismissing a rep-range shift proposal (STR-243; STR-237 Route (i)).
 * The originating throwable of a failed write rides the [PersistenceFailed]
 * branch as an [OperationalDiagnostic] (legacy tag + rendered message, real
 * throwable as cause); the presentation boundary drains it exactly once
 * through its module's DiagnosticDrain. Coroutine cancellation is not a
 * branch: it propagates.
 */
sealed interface DismissRepRangeShiftProposalResult {
    data object Success : DismissRepRangeShiftProposalResult

    /** The dismissal write failed (preferences store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : DismissRepRangeShiftProposalResult
}

/**
 * Permanently dismisses one rep-range shift proposal fingerprint — used when
 * the user defers the card ("Not now") so it does not resurface until
 * signals change (new fingerprint). Mirrors
 * [com.stratum.application.lift.DismissEquipmentProposalUseCase].
 * Cancellation paths must not call this.
 */
class DismissRepRangeShiftProposalUseCase @Inject constructor(
    private val preferences: RepRangeShiftProposalPreferences,
) {
    suspend operator fun invoke(fingerprint: String): DismissRepRangeShiftProposalResult =
        try {
            preferences.dismiss(fingerprint)
            DismissRepRangeShiftProposalResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            DismissRepRangeShiftProposalResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to dismiss rep-range shift proposal: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "DismissRepRangeShiftProposal"
    }
}
