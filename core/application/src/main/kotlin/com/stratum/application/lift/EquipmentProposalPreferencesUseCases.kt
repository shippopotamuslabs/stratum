package com.stratum.application.lift

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Outcome of an equipment-proposal preferences write (STR-243; STR-237 Route (i)).
 *
 * The originating throwable rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates as cancellation.
 */
sealed interface EquipmentProposalPreferenceResult {
    data object Success : EquipmentProposalPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : EquipmentProposalPreferenceResult
}

class ObserveEquipmentProposalsEnabledUseCase @Inject constructor(
    private val preferences: EquipmentProposalPreferences,
) {
    operator fun invoke(): Flow<Boolean> = preferences.observeEnabled()
}

class SetEquipmentProposalsEnabledUseCase @Inject constructor(
    private val preferences: EquipmentProposalPreferences,
) {
    suspend operator fun invoke(enabled: Boolean): EquipmentProposalPreferenceResult =
        try {
            preferences.setEnabled(enabled)
            EquipmentProposalPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            EquipmentProposalPreferenceResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set equipment proposals enabled: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SetEquipmentProposalsEnabled"
    }
}
