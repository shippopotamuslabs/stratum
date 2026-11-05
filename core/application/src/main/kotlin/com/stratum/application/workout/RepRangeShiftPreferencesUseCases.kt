package com.stratum.application.workout

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Outcome of a rep-range-shift preferences write (STR-243).
 *
 * The originating throwable goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates as cancellation.
 */
sealed interface RepRangeShiftPreferenceResult {
    data object Success : RepRangeShiftPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data object PersistenceFailed : RepRangeShiftPreferenceResult
}

class ObserveRepRangeShiftProposalsEnabledUseCase @Inject constructor(
    private val preferences: RepRangeShiftProposalPreferences,
) {
    operator fun invoke(): Flow<Boolean> = preferences.observeEnabled()
}

class SetRepRangeShiftProposalsEnabledUseCase @Inject constructor(
    private val preferences: RepRangeShiftProposalPreferences,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(enabled: Boolean): RepRangeShiftPreferenceResult =
        try {
            preferences.setEnabled(enabled)
            RepRangeShiftPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set rep-range shift proposals enabled: ${t.message}", t)
            RepRangeShiftPreferenceResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SetRepRangeShiftEnabled"
    }
}
