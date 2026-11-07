package com.stratum.core.infrastructure.repository

import android.content.Context
import com.stratum.core.infrastructure.healthconnect.HealthConnectPreferencesRecord
import com.stratum.core.infrastructure.healthconnect.healthConnectPreferencesDataStore
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.application.healthconnect.HealthConnectPreferencesPort
import com.stratum.application.healthconnect.HealthConnectWriteOutcome
import com.stratum.application.healthconnect.HealthSyncDiagnostic
import kotlin.coroutines.cancellation.CancellationException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
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

    override suspend fun setEnabled(enabled: Boolean): HealthConnectWriteOutcome =
        writePreferences { current -> current.copy(enabled = enabled) }

    override suspend fun setPendingSyncError(
        kind: HealthFailureKind?,
    ): HealthConnectWriteOutcome =
        writePreferences { current -> current.copy(pendingSyncErrorKind = kind?.name) }

    private suspend fun writePreferences(
        transform: suspend (HealthConnectPreferencesRecord) -> HealthConnectPreferencesRecord,
    ): HealthConnectWriteOutcome {
        try {
            context.healthConnectPreferencesDataStore.updateData(transform)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return HealthConnectWriteOutcome.Failed(
                listOf(HealthSyncDiagnostic.OperationFailed(error)),
            )
        }
        return HealthConnectWriteOutcome.Success
    }
}
