package com.stratum.core.data.healthconnect

import com.stratum.domain.healthconnect.HealthConnectPreferences
import kotlinx.serialization.Serializable

@Serializable
internal data class HealthConnectPreferencesRecord(
    val enabled: Boolean = false,
    val pendingSyncError: String? = null,
) {
    fun toDomain(): HealthConnectPreferences =
        HealthConnectPreferences(
            enabled = enabled,
            pendingSyncError = pendingSyncError,
        )
}
