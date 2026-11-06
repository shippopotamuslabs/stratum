package com.stratum.core.testing.healthconnect

import com.stratum.application.healthconnect.HEALTH_SYNC_PERMISSION_DENIED_MESSAGE
import com.stratum.application.healthconnect.HEALTH_SYNC_UNAVAILABLE_MESSAGE
import com.stratum.application.healthconnect.HealthConnectGrantedPermissionsOutcome
import com.stratum.application.healthconnect.HealthConnectPushOutcome
import com.stratum.application.healthconnect.HealthConnectSyncPort
import com.stratum.application.healthconnect.HealthSyncDiagnostic
import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import kotlinx.coroutines.CancellationException

class FakeHealthConnectSyncPort : HealthConnectSyncPort {
    var isAvailable: Boolean = true
    var hasPermission: Boolean = true
    var granted: Set<String> = emptySet()

    /** When non-null, the granted-permissions query fails with this as its diagnostic cause. */
    var grantedPermissionsFailure: Throwable? = null

    /** When non-null, push fails as Transient with this as its diagnostic cause. */
    var pushFailure: Throwable? = null

    /** When non-null, push throws this instead of returning an outcome (cancellation tests). */
    var pushThrows: Throwable? = null

    /** When non-null, push fails with this classification kind (and [pushFailureMessage]). */
    var pushFailureKind: HealthSyncFailureKind? = null
    var pushFailureMessage: String? = null

    val pushedSessions = mutableListOf<Pair<TrainingSession, List<LoggedSet>>>()

    override suspend fun isAvailable(): Boolean = isAvailable

    override suspend fun hasWritePermission(): Boolean = hasPermission

    override suspend fun grantedPermissions(): HealthConnectGrantedPermissionsOutcome {
        grantedPermissionsFailure?.let { failure ->
            if (failure is CancellationException) throw failure
            return HealthConnectGrantedPermissionsOutcome.Failed(
                kind = HealthSyncFailureKind.Transient,
                message = failure.message,
                diagnostics = listOf(HealthSyncDiagnostic.OperationFailed(failure)),
            )
        }
        if (!isAvailable) {
            return HealthConnectGrantedPermissionsOutcome.Failed(
                kind = HealthSyncFailureKind.Terminal,
                message = HEALTH_SYNC_UNAVAILABLE_MESSAGE,
                diagnostics = listOf(
                    HealthSyncDiagnostic.OperationFailed(
                        IllegalStateException(HEALTH_SYNC_UNAVAILABLE_MESSAGE),
                    ),
                ),
            )
        }
        return HealthConnectGrantedPermissionsOutcome.Success(granted)
    }

    override suspend fun pushSession(
        session: TrainingSession,
        sets: List<LoggedSet>,
    ): HealthConnectPushOutcome {
        pushThrows?.let { failure ->
            if (failure is CancellationException) throw failure
        }
        pushFailure?.let { failure ->
            if (failure is CancellationException) throw failure
        }
        val outcome = when {
            !isAvailable -> failedPush(
                HealthSyncFailureKind.Terminal,
                HEALTH_SYNC_UNAVAILABLE_MESSAGE,
            )
            !hasPermission -> failedPush(
                HealthSyncFailureKind.UserActionable,
                HEALTH_SYNC_PERMISSION_DENIED_MESSAGE,
            )
            pushThrows != null -> failedPush(
                HealthSyncFailureKind.Transient,
                pushThrows!!.message,
                pushThrows,
            )
            pushFailureKind != null -> failedPush(
                pushFailureKind!!,
                pushFailureMessage,
                IllegalStateException(pushFailureMessage),
            )
            pushFailure != null -> failedPush(
                HealthSyncFailureKind.Transient,
                pushFailure!!.message,
                pushFailure,
            )
            else -> {
                pushedSessions.add(session to sets)
                HealthConnectPushOutcome.Success
            }
        }
        return outcome
    }

    private fun failedPush(
        kind: HealthSyncFailureKind,
        message: String?,
        cause: Throwable? = null,
    ): HealthConnectPushOutcome.Failed =
        HealthConnectPushOutcome.Failed(
            kind = kind,
            message = message,
            diagnostics = listOf(
                HealthSyncDiagnostic.OperationFailed(
                    cause ?: IllegalStateException(message),
                ),
            ),
        )
}
