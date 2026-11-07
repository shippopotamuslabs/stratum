package com.stratum.application.checkin

import com.stratum.application.diagnostics.OperationalDiagnostic
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.stratum.domain.checkin.CheckInPreferences

/**
 * Outcome of a check-in preferences write (STR-243; STR-237 Route (i)).
 *
 * The originating throwable rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates as cancellation.
 */
sealed interface CheckInPreferenceResult {
    data object Success : CheckInPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : CheckInPreferenceResult
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
) {
    suspend operator fun invoke(enabled: Boolean): CheckInPreferenceResult =
        try {
            repository.setEnabled(enabled)
            CheckInPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            CheckInPreferenceResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set check-in enabled: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SetCheckInEnabled"
    }
}

class SkipCheckInUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
) {
    suspend operator fun invoke(now: Instant = Instant.now()): CheckInPreferenceResult =
        try {
            repository.skip(now)
            CheckInPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            CheckInPreferenceResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to skip check-in: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SkipCheckIn"
    }
}

class CompleteCheckInUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
) {
    suspend operator fun invoke(now: Instant = Instant.now()): CheckInPreferenceResult =
        try {
            repository.complete(now)
            CheckInPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            CheckInPreferenceResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to complete check-in: ${t.message}",
                        cause = t,
                    ),
                ),
            )
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
) {
    suspend operator fun invoke(extractedAt: Instant = Instant.now()): CheckInPreferenceResult =
        try {
            repository.markPending(extractedAt)
            CheckInPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            CheckInPreferenceResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to mark check-in pending: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "MarkCheckInPending"
    }
}
