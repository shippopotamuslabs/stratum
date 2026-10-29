package com.stratum.core.testing.checkin

import com.stratum.domain.checkin.CheckInPreferences
import com.stratum.application.checkin.CheckInPreferencesRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [CheckInPreferencesRepository] for use-case and ViewModel tests. */
class FakeCheckInPreferencesRepository(
    initial: CheckInPreferences = CheckInPreferences(),
) : CheckInPreferencesRepository {

    private val preferences = MutableStateFlow(initial)

    val current: CheckInPreferences
        get() = preferences.value

    fun seed(value: CheckInPreferences) {
        preferences.value = value
    }

    override fun observe(): Flow<CheckInPreferences> = preferences.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        preferences.value =
            if (enabled) {
                preferences.value.copy(enabled = true)
            } else {
                preferences.value.copy(
                    enabled = false,
                    pendingSinceExtractedAt = null,
                )
            }
    }

    override suspend fun markPending(extractedAt: Instant) {
        val current = preferences.value
        if (!current.enabled) return
        preferences.value = current.copy(pendingSinceExtractedAt = extractedAt)
    }

    override suspend fun skip(now: Instant) {
        preferences.value =
            preferences.value.copy(
                pendingSinceExtractedAt = null,
                lastSkippedAt = now,
            )
    }

    override suspend fun complete(now: Instant) {
        preferences.value =
            preferences.value.copy(
                pendingSinceExtractedAt = null,
                lastCompletedAt = now,
            )
    }
}
