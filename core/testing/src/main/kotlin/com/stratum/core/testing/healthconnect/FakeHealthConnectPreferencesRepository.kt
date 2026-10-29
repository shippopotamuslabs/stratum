package com.stratum.core.testing.healthconnect

import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.application.healthconnect.HealthConnectPreferencesPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHealthConnectPreferencesRepository : HealthConnectPreferencesPort {
    private val preferences = MutableStateFlow(HealthConnectPreferences())

    override fun observePreferences(): Flow<HealthConnectPreferences> = preferences

    override suspend fun getPreferences(): HealthConnectPreferences = preferences.value

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> {
        preferences.value = preferences.value.copy(enabled = enabled)
        return Result.success(Unit)
    }

    override suspend fun setPendingSyncError(message: String?): Result<Unit> {
        preferences.value = preferences.value.copy(pendingSyncError = message)
        return Result.success(Unit)
    }

    fun setPreferences(prefs: HealthConnectPreferences) {
        preferences.value = prefs
    }
}
