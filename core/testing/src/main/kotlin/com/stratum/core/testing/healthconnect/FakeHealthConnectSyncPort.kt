package com.stratum.core.testing.healthconnect

import com.stratum.domain.healthconnect.HealthConnectPermissionDeniedException
import com.stratum.application.healthconnect.HealthConnectSyncPort
import com.stratum.domain.healthconnect.HealthConnectUnavailableException
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession

class FakeHealthConnectSyncPort : HealthConnectSyncPort {
    var isAvailable: Boolean = true
    var hasPermission: Boolean = true
    var granted: Set<String> = emptySet()
    var grantedPermissionsFailure: Throwable? = null
    var pushFailure: Throwable? = null
    val pushedSessions = mutableListOf<Pair<TrainingSession, List<LoggedSet>>>()

    override suspend fun isAvailable(): Boolean = isAvailable

    override suspend fun hasWritePermission(): Boolean = hasPermission

    override suspend fun grantedPermissions(): Result<Set<String>> {
        grantedPermissionsFailure?.let { return Result.failure(it) }
        if (!isAvailable) {
            return Result.failure(HealthConnectUnavailableException())
        }
        return Result.success(granted)
    }

    override suspend fun pushSession(
        session: TrainingSession,
        sets: List<LoggedSet>,
    ): Result<Unit> {
        if (!isAvailable) {
            return Result.failure(HealthConnectUnavailableException())
        }
        if (!hasPermission) {
            return Result.failure(HealthConnectPermissionDeniedException())
        }
        pushFailure?.let { return Result.failure(it) }
        pushedSessions.add(session to sets)
        return Result.success(Unit)
    }
}
