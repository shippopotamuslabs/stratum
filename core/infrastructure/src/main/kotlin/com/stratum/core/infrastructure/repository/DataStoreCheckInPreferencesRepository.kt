package com.stratum.core.infrastructure.repository

import android.content.Context
import com.stratum.core.infrastructure.checkin.checkInPreferencesDataStore
import com.stratum.core.infrastructure.checkin.toDomain
import com.stratum.core.infrastructure.checkin.toRecord
import com.stratum.domain.checkin.CheckInPreferences
import com.stratum.application.checkin.CheckInPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [CheckInPreferencesRepository]. Emits defaults (enabled, no pending) until
 * the first successful read.
 */
@Singleton
class DataStoreCheckInPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : CheckInPreferencesRepository {

    override fun observe(): Flow<CheckInPreferences> =
        context.checkInPreferencesDataStore.data.map { record -> record.toDomain() }

    override suspend fun setEnabled(enabled: Boolean) {
        context.checkInPreferencesDataStore.updateData { current ->
            val domain = current.toDomain()
            if (enabled) {
                domain.copy(enabled = true).toRecord()
            } else {
                domain.copy(enabled = false, pendingSinceExtractedAt = null).toRecord()
            }
        }
    }

    override suspend fun markPending(extractedAt: Instant) {
        context.checkInPreferencesDataStore.updateData { current ->
            val domain = current.toDomain()
            if (!domain.enabled) {
                current
            } else {
                domain.copy(pendingSinceExtractedAt = extractedAt).toRecord()
            }
        }
    }

    override suspend fun skip(now: Instant) {
        context.checkInPreferencesDataStore.updateData { current ->
            current.toDomain()
                .copy(pendingSinceExtractedAt = null, lastSkippedAt = now)
                .toRecord()
        }
    }

    override suspend fun complete(now: Instant) {
        context.checkInPreferencesDataStore.updateData { current ->
            current.toDomain()
                .copy(pendingSinceExtractedAt = null, lastCompletedAt = now)
                .toRecord()
        }
    }

    /** Test helper — current snapshot. */
    suspend fun current(): CheckInPreferences =
        context.checkInPreferencesDataStore.data.first().toDomain()
}
