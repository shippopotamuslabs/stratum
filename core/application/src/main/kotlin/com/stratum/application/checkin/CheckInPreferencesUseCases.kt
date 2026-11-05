package com.stratum.application.checkin

import com.stratum.domain.logging.LoggingPort
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.stratum.domain.checkin.CheckInPreferences

/**
 * Outcome of a check-in preferences write (STR-243).
 *
 * The originating throwable goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates as cancellation.
 */
sealed interface CheckInPreferenceResult {
    data object Success : CheckInPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data object PersistenceFailed : CheckInPreferenceResult
}

class ObserveCheckInPreferencesUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
) {
    operator fun invoke(): Flow<CheckInPreferences> = repository.observe()
}

class ObservePendingCheckInUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        repository.observe().map { prefs -> prefs.isPending }
}

class SetCheckInEnabledUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(enabled: Boolean): CheckInPreferenceResult =
        try {
            repository.setEnabled(enabled)
            CheckInPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set check-in enabled: ${t.message}", t)
            CheckInPreferenceResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SetCheckInEnabled"
    }
}

class SkipCheckInUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(now: Instant = Instant.now()): CheckInPreferenceResult =
        try {
            repository.skip(now)
            CheckInPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to skip check-in: ${t.message}", t)
            CheckInPreferenceResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SkipCheckIn"
    }
}

class CompleteCheckInUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(now: Instant = Instant.now()): CheckInPreferenceResult =
        try {
            repository.complete(now)
            CheckInPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to complete check-in: ${t.message}", t)
            CheckInPreferenceResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "CompleteCheckIn"
    }
}

/**
 * Marks a pending coach check-in after a successful response-modeling **re-extract**.
 * Called from [com.stratum.core.infrastructure.work.ResponseModelingWork] only when there was a prior
 * extract and the ≥20-workout gate passed.
 */
class MarkCheckInPendingUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(extractedAt: Instant = Instant.now()): CheckInPreferenceResult =
        try {
            repository.markPending(extractedAt)
            CheckInPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to mark check-in pending: ${t.message}", t)
            CheckInPreferenceResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "MarkCheckInPending"
    }
}
