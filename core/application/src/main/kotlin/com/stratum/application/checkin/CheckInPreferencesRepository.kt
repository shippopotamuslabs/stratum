package com.stratum.application.checkin

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.checkin.CheckInPreferences

/**
 * Observes and updates persisted [CheckInPreferences]. Implementations use DataStore; domain
 * callers must not block the main thread on writes.
 */
interface CheckInPreferencesRepository {
    fun observe(): Flow<CheckInPreferences>

    suspend fun setEnabled(enabled: Boolean)

    /** Marks a pending check-in after a successful re-extract. No-op when check-ins are disabled. */
    suspend fun markPending(extractedAt: Instant)

    suspend fun skip(now: Instant = Instant.now())

    suspend fun complete(now: Instant = Instant.now())
}
