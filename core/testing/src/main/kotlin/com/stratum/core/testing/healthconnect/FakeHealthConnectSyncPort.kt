package com.stratum.core.testing.healthconnect

import com.stratum.application.healthconnect.HealthConnectSyncPort
import com.stratum.application.healthconnect.HealthSyncFailure
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession

class FakeHealthConnectSyncPort : HealthConnectSyncPort {
    var isAvailable: Boolean = true
    var hasPermission: Boolean = true
    var granted: Set<String> = emptySet()
    var grantedPermissionsFailure: Throwable? = null
    var pushFailure: Throwable? = null
    var pushThrows: Throwable? = null
    val pushedSessions = mutableListOf<Pair<TrainingSession, List<LoggedSet>>>()

    override suspend fun isAvailable(): Boolean = isAvailable

    override suspend fun hasWritePermission(): Boolean = hasPermission

    override suspend fun grantedPermissions(): Set<String> {
        grantedPermissionsFailure?.let { throw it }
        if (!isAvailable) {
            throw HealthSyncFailure.unavailable()
        }
        return granted
    }

    override suspend fun pushSession(
        session: TrainingSession,
        sets: List<LoggedSet>,
    ) {
        if (!isAvailable) {
            throw HealthSyncFailure.unavailable()
        }
        if (!hasPermission) {
            throw HealthSyncFailure.permissionDenied()
        }
        pushThrows?.let { throw it }
        pushFailure?.let { throw it }
        pushedSessions.add(session to sets)
    }
}
