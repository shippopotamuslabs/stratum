package com.stratum.core.data.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.stratum.domain.healthconnect.HealthConnectPreferencesPort
import com.stratum.domain.healthconnect.HealthConnectSchedulerPort
import com.stratum.domain.healthconnect.HealthConnectSyncPort
import com.stratum.domain.training.identity.TrainingSessionId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prefs-gated WorkManager enqueue for Health Connect session sync.
 * Never throws — finalize treats this as fire-and-forget.
 */
@Singleton
class WorkManagerHealthConnectScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesPort: HealthConnectPreferencesPort,
    private val healthConnectSyncPort: HealthConnectSyncPort,
) : HealthConnectSchedulerPort {

    override suspend fun enqueueSync(sessionId: TrainingSessionId) {
        runCatching {
            if (!preferencesPort.getPreferences().enabled) return
            if (!healthConnectSyncPort.hasWritePermission()) return
            val inputData = Data.Builder()
                .putString(KEY_SESSION_ID, sessionId.value)
                .build()

            val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_PREFIX + sessionId.value,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    companion object {
        const val KEY_SESSION_ID = "health_connect_session_id"
        const val WORK_NAME_PREFIX = "health_connect_sync_"
    }
}
