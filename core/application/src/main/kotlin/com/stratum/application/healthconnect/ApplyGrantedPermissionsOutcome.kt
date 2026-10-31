package com.stratum.application.healthconnect

/**
 * Outcome of [SetHealthConnectEnabledUseCase.applyGrantedPermissions]: the preference write
 * itself reports success/failure through the surrounding `Result`, while a permission-query
 * degradation the call tolerates rides along as [diagnostics].
 *
 * Placement note (CODE_STYLE_GOSPEL §11): this type is owned by the application
 * layer, not the domain. The degradation it records is detected in use-case
 * coordination — a grant-set query failing after the permission sheet closes — not in
 * domain math, so the domain stays free of this operational concern. Diagnostics are
 * drained exactly once at the feature boundary and never reach UI state.
 */
data class ApplyGrantedPermissionsOutcome(
    val diagnostics: List<ApplyGrantedPermissionsDiagnostic> = emptyList(),
)

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
