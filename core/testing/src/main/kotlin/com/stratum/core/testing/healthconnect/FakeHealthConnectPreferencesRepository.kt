package com.stratum.core.testing.healthconnect

import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.application.healthconnect.HealthConnectPreferencesPort
import com.stratum.application.healthconnect.HealthConnectWriteOutcome
import com.stratum.application.healthconnect.HealthSyncDiagnostic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHealthConnectPreferencesRepository : HealthConnectPreferencesPort {
    private val preferences = MutableStateFlow(HealthConnectPreferences())

    /**
     * When non-null, writes return a Failed outcome carrying this as the
     * diagnostic cause without mutating state. A [CancellationException]
     * still throws — cancellation is never an outcome branch.
     */
    var setEnabledFailure: Throwable? = null
    var setPendingSyncErrorFailure: Throwable? = null

    override fun observePreferences(): Flow<HealthConnectPreferences> = preferences

    override suspend fun getPreferences(): HealthConnectPreferences = preferences.value

    override suspend fun setEnabled(enabled: Boolean): HealthConnectWriteOutcome {
        setEnabledFailure?.let { return failureOutcome(it) }
        preferences.value = preferences.value.copy(enabled = enabled)
        return HealthConnectWriteOutcome.Success
    }

    override suspend fun setPendingSyncError(
        kind: HealthFailureKind?,
    ): HealthConnectWriteOutcome {
        setPendingSyncErrorFailure?.let { return failureOutcome(it) }
        preferences.value = preferences.value.copy(pendingSyncError = kind)
        return HealthConnectWriteOutcome.Success
    }

    fun setPreferences(prefs: HealthConnectPreferences) {
        preferences.value = prefs
    }

    private fun failureOutcome(failure: Throwable): HealthConnectWriteOutcome {
        if (failure is CancellationException) throw failure
        return HealthConnectWriteOutcome.Failed(
            listOf(HealthSyncDiagnostic.OperationFailed(failure)),
        )
    }
}
