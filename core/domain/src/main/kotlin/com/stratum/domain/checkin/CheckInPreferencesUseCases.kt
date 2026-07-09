package com.stratum.domain.checkin

import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    suspend operator fun invoke(enabled: Boolean) {
        repository.setEnabled(enabled)
    }
}

class SkipCheckInUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
) {
    suspend operator fun invoke(now: Instant = Instant.now()) {
        repository.skip(now)
    }
}

class CompleteCheckInUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
) {
    suspend operator fun invoke(now: Instant = Instant.now()) {
        repository.complete(now)
    }
}

/**
 * Marks a pending coach check-in after a successful response-modeling **re-extract**.
 * Called from [com.stratum.core.data.work.ResponseModelingWork] only when there was a prior
 * extract and the ≥20-workout gate passed.
 */
class MarkCheckInPendingUseCase @Inject constructor(
    private val repository: CheckInPreferencesRepository,
) {
    suspend operator fun invoke(extractedAt: Instant = Instant.now()) {
        repository.markPending(extractedAt)
    }
}
