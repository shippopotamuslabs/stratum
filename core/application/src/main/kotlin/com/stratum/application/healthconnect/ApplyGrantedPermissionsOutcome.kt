package com.stratum.application.healthconnect

import com.stratum.application.diagnostics.OperationalDiagnostic

/**
 * Outcome of [SetHealthConnectEnabledUseCase.applyGrantedPermissions] (STR-243):
 * [Applied] carries any permission-query degradation the call tolerated as
 * [ApplyGrantedPermissionsDiagnostic]s; [PersistenceFailed] is the preference
 * write itself failing (its diagnostics ride the branch, drained once at the
 * presentation boundary).
 *
 * Placement note (CODE_STYLE_GOSPEL §11): this type is owned by the application
 * layer, not the domain. The degradation it records is detected in use-case
 * coordination — a grant-set query failing after the permission sheet closes — not in
 * domain math, so the domain stays free of this operational concern. Diagnostics are
 * drained exactly once at the feature boundary and never reach UI state.
 */
sealed interface ApplyGrantedPermissionsOutcome {
    data class Applied(
        val diagnostics: List<ApplyGrantedPermissionsDiagnostic> = emptyList(),
    ) : ApplyGrantedPermissionsOutcome

    /** The SDK became unavailable before the opt-in write; no write was attempted. */
    data object SdkUnavailable : ApplyGrantedPermissionsOutcome

    /** The preference write failed (store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : ApplyGrantedPermissionsOutcome
}

/**
 * A non-fatal permission-query degradation recorded as data (STR-257).
 *
 * Diagnostics carry what was skipped and why — the failure as data — and are
 * constructed, never logged, below the boundary. Coroutine cancellation is not a
 * diagnostic: it propagates.
 */
sealed interface ApplyGrantedPermissionsDiagnostic {
    /**
     * The SDK grant set could not be read after the permission sheet closed, so the
     * preference was left unchanged (a Back / cancel cannot turn an enabled switch off).
     */
    data class PermissionQueryFailed(
        val cause: Throwable,
    ) : ApplyGrantedPermissionsDiagnostic
}
