package com.stratum.application.checkin

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.stratum.domain.checkin.CheckInPreferences

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
    suspend operator fun invoke(enabled: Boolean): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set check-in enabled") {
            repository.setEnabled(enabled)
        }

    private companion object {
        const val TAG = "SetCheckInEnabled"
    }
}

class SkipCheckInUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(now: Instant = Instant.now()): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to skip check-in") {
            repository.skip(now)
        }

    private companion object {
        const val TAG = "SkipCheckIn"
    }
}

class CompleteCheckInUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(now: Instant = Instant.now()): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to complete check-in") {
            repository.complete(now)
        }

    private companion object {
        const val TAG = "CompleteCheckIn"
    }
}

/**
 * Marks a pending coach check-in after a successful response-modeling **re-extract**.
 * Called from [com.stratum.core.data.work.ResponseModelingWork] only when there was a prior
 * extract and the ≥20-workout gate passed.
 */
class MarkCheckInPendingUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(extractedAt: Instant = Instant.now()): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to mark check-in pending") {
            repository.markPending(extractedAt)
        }

    private companion object {
        const val TAG = "MarkCheckInPending"
    }
}
