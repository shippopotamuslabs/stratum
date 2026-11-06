package com.stratum.core.testing.healthconnect

import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.application.healthconnect.HealthConnectPreferencesPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHealthConnectPreferencesRepository : HealthConnectPreferencesPort {
    private val preferences = MutableStateFlow(HealthConnectPreferences())

    /** When non-null, writes throw this error without mutating state. */
    var setEnabledFailure: Throwable? = null
    var setPendingSyncErrorFailure: Throwable? = null

    override fun observePreferences(): Flow<HealthConnectPreferences> = preferences

    override suspend fun getPreferences(): HealthConnectPreferences = preferences.value

    override suspend fun setEnabled(enabled: Boolean) {
        setEnabledFailure?.let { throw it }
        preferences.value = preferences.value.copy(enabled = enabled)
    }

    override suspend fun setPendingSyncError(kind: HealthFailureKind?) {
        setPendingSyncErrorFailure?.let { throw it }
        preferences.value = preferences.value.copy(pendingSyncError = kind)
    }

    fun setPreferences(prefs: HealthConnectPreferences) {
        preferences.value = prefs
    }
}
