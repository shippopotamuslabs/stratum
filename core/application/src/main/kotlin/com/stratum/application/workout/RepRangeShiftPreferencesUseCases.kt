package com.stratum.application.workout

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Outcome of a rep-range-shift preferences write (STR-243; STR-237 Route (i)).
 *
 * The originating throwable rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates as cancellation.
 */
sealed interface RepRangeShiftPreferenceResult {
    data object Success : RepRangeShiftPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : RepRangeShiftPreferenceResult
}

class ObserveRepRangeShiftProposalsEnabledUseCase @Inject constructor(
    private val preferences: RepRangeShiftProposalPreferences,
) {
    operator fun invoke(): Flow<Boolean> = preferences.observeEnabled()
}

class SetRepRangeShiftProposalsEnabledUseCase @Inject constructor(
    private val preferences: RepRangeShiftProposalPreferences,
) {
    suspend operator fun invoke(enabled: Boolean): RepRangeShiftPreferenceResult =
        try {
            preferences.setEnabled(enabled)
            RepRangeShiftPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            RepRangeShiftPreferenceResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set rep-range shift proposals enabled: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SetRepRangeShiftEnabled"
    }
}
