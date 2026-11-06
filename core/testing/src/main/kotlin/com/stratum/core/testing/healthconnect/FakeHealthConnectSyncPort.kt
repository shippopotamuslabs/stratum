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

    /**
     * The single push-failure driver (shared fake convention): when non-null,
     * [pushSession] returns its Failed branch with this throwable as the
     * diagnostic cause — the configured throwable itself, never a synthesized
     * substitute. A [CancellationException] still throws: cancellation is
     * never an outcome branch, and it is the only value that throws from
     * [pushSession].
     */
    var pushFailure: Throwable? = null

    /**
     * Optional classification override pair for the [pushFailure] branch,
     * consulted only when [pushFailure] is non-null: [pushFailureKind]
     * replaces the default [HealthSyncFailureKind.Transient] classification,
     * and a non-null [pushFailureMessage] replaces `pushFailure.message` as
     * the branch message. The diagnostic cause is [pushFailure] either way.
     */
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
        pushFailure?.let { failure ->
            if (failure is CancellationException) throw failure
        }
        // Documented check order, consistent with grantedPermissions() above:
        // the configured failure field first, then availability state.
        // The state-driven branches below have no configured throwable; their
        // diagnostic cause is synthesized from the branch message, mirroring
        // the production adapter's typed unavailable / permission-denied
        // exceptions for the same states.
        val outcome = when {
            pushFailure != null -> failedPush(
                pushFailureKind ?: HealthSyncFailureKind.Transient,
                pushFailureMessage ?: pushFailure!!.message,
                pushFailure!!,
            )
            !isAvailable -> failedPush(
                HealthSyncFailureKind.Terminal,
                HEALTH_SYNC_UNAVAILABLE_MESSAGE,
                IllegalStateException(HEALTH_SYNC_UNAVAILABLE_MESSAGE),
            )
            !hasPermission -> failedPush(
                HealthSyncFailureKind.UserActionable,
                HEALTH_SYNC_PERMISSION_DENIED_MESSAGE,
                IllegalStateException(HEALTH_SYNC_PERMISSION_DENIED_MESSAGE),
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
        cause: Throwable,
    ): HealthConnectPushOutcome.Failed =
        HealthConnectPushOutcome.Failed(
            kind = kind,
            message = message,
            diagnostics = listOf(
                HealthSyncDiagnostic.OperationFailed(cause),
            ),
        )
}
