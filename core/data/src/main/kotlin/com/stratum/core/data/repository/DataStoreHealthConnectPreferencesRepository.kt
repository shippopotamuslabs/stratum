package com.stratum.core.data.repository

import android.content.Context
import com.stratum.core.data.healthconnect.healthConnectPreferencesDataStore
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.application.healthconnect.HealthConnectPreferencesPort
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreHealthConnectPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loggingPort: LoggingPort,
) : HealthConnectPreferencesPort {

    override fun observePreferences(): Flow<HealthConnectPreferences> =
        context.healthConnectPreferencesDataStore.data.map { record -> record.toDomain() }

    override suspend fun getPreferences(): HealthConnectPreferences =
        context.healthConnectPreferencesDataStore.data.first().toDomain()

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set Health Connect enabled") {
            context.healthConnectPreferencesDataStore.updateData { current ->
                current.copy(enabled = enabled)
            }
            Unit
        }

    override suspend fun setPendingSyncError(message: String?): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set Health Connect sync error") {
            context.healthConnectPreferencesDataStore.updateData { current ->
                current.copy(pendingSyncError = message)
            }
            Unit
        }

    private companion object {
        const val TAG = "HealthConnectPrefs"
    }
}
