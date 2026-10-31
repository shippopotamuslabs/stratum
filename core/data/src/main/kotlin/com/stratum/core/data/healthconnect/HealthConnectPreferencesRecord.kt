package com.stratum.core.data.healthconnect

import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthFailureKind
import kotlinx.serialization.Serializable

@Serializable
internal data class HealthConnectPreferencesRecord(
    val enabled: Boolean = false,
    val pendingSyncErrorKind: String? = null,
) {
    fun toDomain(): HealthConnectPreferences =
        HealthConnectPreferences(
            enabled = enabled,
            pendingSyncError =
                pendingSyncErrorKind?.let { name ->
                    runCatching { HealthFailureKind.valueOf(name) }
                        .getOrDefault(HealthFailureKind.Unknown)
                },
        )
}
