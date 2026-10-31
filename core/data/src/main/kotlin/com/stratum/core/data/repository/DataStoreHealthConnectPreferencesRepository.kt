package com.stratum.core.data.repository

import android.content.Context
import com.stratum.core.data.healthconnect.HealthConnectPreferencesRecord
import com.stratum.core.data.healthconnect.healthConnectPreferencesDataStore
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.application.healthconnect.HealthConnectPreferencesPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreHealthConnectPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectPreferencesPort {

    override fun observePreferences(): Flow<HealthConnectPreferences> =
        context.healthConnectPreferencesDataStore.data.map { record -> record.toDomain() }

    override suspend fun getPreferences(): HealthConnectPreferences =
        context.healthConnectPreferencesDataStore.data.first().toDomain()

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> =
        writePreferences { current -> current.copy(enabled = enabled) }

    override suspend fun setPendingSyncError(message: String?): Result<Unit> =
        writePreferences { current -> current.copy(pendingSyncError = message) }

    private suspend fun writePreferences(
        transform: suspend (HealthConnectPreferencesRecord) -> HealthConnectPreferencesRecord,
    ): Result<Unit> =
        try {
            context.healthConnectPreferencesDataStore.updateData(transform)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
}
