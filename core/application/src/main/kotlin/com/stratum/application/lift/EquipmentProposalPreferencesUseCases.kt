package com.stratum.application.lift

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Outcome of an equipment-proposal preferences write (STR-243).
 *
 * The originating throwable goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates as cancellation.
 */
sealed interface EquipmentProposalPreferenceResult {
    data object Success : EquipmentProposalPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data object PersistenceFailed : EquipmentProposalPreferenceResult
}

class ObserveEquipmentProposalsEnabledUseCase @Inject constructor(
    private val preferences: EquipmentProposalPreferences,
) {
    operator fun invoke(): Flow<Boolean> = preferences.observeEnabled()
}

class SetEquipmentProposalsEnabledUseCase @Inject constructor(
    private val preferences: EquipmentProposalPreferences,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(enabled: Boolean): EquipmentProposalPreferenceResult =
        try {
            preferences.setEnabled(enabled)
            EquipmentProposalPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set equipment proposals enabled: ${t.message}", t)
            EquipmentProposalPreferenceResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SetEquipmentProposalsEnabled"
    }
}
