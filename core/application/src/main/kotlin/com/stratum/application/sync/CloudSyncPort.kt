package com.stratum.application.sync

import kotlinx.coroutines.flow.StateFlow
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.sync.CloudSessionState
import com.stratum.domain.sync.SyncStatus
import com.stratum.domain.lift.residual

/**
 * Cloud sync boundary. Domain never sees Firebase/Firestore types.
 *
 * Implementations live in `:core:data`. Call sites are use cases only (ARCHITECTURE.md §4.4).
 *
 * Contract:
 * - [start]: ensure account ownership of local Room, then push/pull (or ownership-only when
 *   the feature gate is off). Marks [sessionState] [CloudSessionState.Ready] once local data
 *   is owned/usable (hydrated local cache or after first successful hydrate); network failures
 *   after local-ready do not revoke Ready.
 * - [stop]: session teardown only (cancel WorkManager / clear active uid); durable hydrate
 *   and pull cursors are retained for same-user re-login **unless** [endSessionAndWipe] ran.
 * - [requestSync]: multi-pass upload of residual dirty + incremental pull while started.
 * - [endSessionAndWipe]: atomic logout teardown — stop session + wipe Room + durable
 *   hydrate/cursors so next [start] full-hydrates from cloud. Last-account ownership is
 *   retained so a different uid on the next [start] still triggers a wipe. Call before
 *   Firebase sign-out.
 * - [sessionState]: shell gate for account-scoped local readiness (not merely Firebase auth).
 *
 * Incomplete surface (not synced by design): response-modeling tables, import job metadata,
 * insights, theme prefs, workout overlays — multi-device may recompute or diverge for those.
 *
 * Failure shape (STR-243 remediation, Sean's E1 ruling): operations return
 * [CloudSyncOutcome]. No exception crosses this port in place of an outcome
 * (coroutine cancellation still propagates). The adapter logs an operational
 * failure once at its own boundary before returning [CloudSyncOutcome.Failed];
 * the failure's cause rides the branch only as a [CloudSyncDiagnostic] value
 * for boundaries that must classify or drain it (the sync worker, the
 * compensating-restart seam) — never as a raw `Result` payload.
 */
interface CloudSyncPort {
    val syncStatus: StateFlow<SyncStatus>

    /**
     * Account-scoped local session readiness. Never [CloudSessionState.Ready] solely because
     * [syncStatus] is Idle before [start] has run.
     */
    val sessionState: StateFlow<CloudSessionState>

    /**
     * Begin sync for [userId]: account isolation, upload pending, hydrate/pull.
     */
    suspend fun start(userId: String): CloudSyncOutcome

    /** Tear down the current sync session (not an account wipe). */
    suspend fun stop()

    /**
     * One-shot push of dirty rows then pull remote changes. Safe to call while [start] is active.
     */
    suspend fun requestSync(): CloudSyncOutcome

    /**
     * Atomic logout teardown: cancel workers, clear active uid, wipe Room user data and
     * durable hydrate/cursors for [userId]. Last-account ownership is retained so a different
     * uid on the next [start] still triggers a wipe. Safe to call without a prior [stop].
     */
    suspend fun endSessionAndWipe(userId: String): CloudSyncOutcome
}

/**
 * Outcome of a cloud sync operation (STR-243).
 *
 * [Failed] carries the operation's failure as [CloudSyncDiagnostic] values:
 * the adapter logs the operational failure once at its own boundary, and a
 * boundary that must classify the failure (the sync worker's
 * NotStarted / ResidualBacklog / Unexpected discrimination) or drain it (the
 * compensating sync restart after a logout / account-deletion failure) reads
 * the cause from the diagnostic — the throwable never crosses as a thrown
 * exception or a raw `Result` payload. Coroutine cancellation is not a
 * branch: it propagates as cancellation.
 */
sealed interface CloudSyncOutcome {
    data object Success : CloudSyncOutcome

    data class Failed(
        val diagnostics: List<CloudSyncDiagnostic>,
    ) : CloudSyncOutcome {
        init {
            require(diagnostics.isNotEmpty()) {
                "CloudSyncOutcome.Failed requires at least one diagnostic."
            }
        }
    }
}

/**
 * A sync operation failure recorded as data (§10 diagnostics channel). The
 * boundary drains each diagnostic through `LoggingPort.error` with the
 * throwable attached; diagnostics never reach UI state.
 */
sealed interface CloudSyncDiagnostic {
    data class OperationFailed(
        val cause: Throwable,
    ) : CloudSyncDiagnostic
}

/** The sync adapter's log tag — every sync-failure record keeps its shape. */
const val CLOUD_SYNC_LOG_TAG = "FirestoreCloudSync"

/**
 * A cloud sync operation whose failure a boundary may have to render. Each
 * operation owns the failure-label prefix of its legacy adapter log line, so
 * the rendered record is `"<label>: <cause.message>"` (see
 * [toOperationalDiagnostic]).
 */
enum class CloudSyncOperation(
    val failureLabel: String,
) {
    Start("start failed"),
    RequestSync("requestSync failed"),
    EndSessionAndWipe("endSessionAndWipe failed"),
}

/**
 * The single shared render factory for sync failures (STR-238 fix pass 2):
 * converts a [CloudSyncDiagnostic] into the §10 [OperationalDiagnostic] a
 * boundary drains, rendering exactly the adapter's legacy log line for that
 * operation — tag [CLOUD_SYNC_LOG_TAG], message
 * `"<operation label>: <cause.message>"`, the real cause preserved. Every
 * boundary renders through here; no boundary hand-rolls this conversion.
 */
fun CloudSyncDiagnostic.toOperationalDiagnostic(
    operation: CloudSyncOperation,
): OperationalDiagnostic =
    when (this) {
        is CloudSyncDiagnostic.OperationFailed ->
            OperationalDiagnostic(
                tag = CLOUD_SYNC_LOG_TAG,
                message = "${operation.failureLabel}: ${cause.message}",
                cause = cause,
            )
    }

/** List form of [toOperationalDiagnostic], order preserved. */
fun List<CloudSyncDiagnostic>.toOperationalDiagnostics(
    operation: CloudSyncOperation,
): List<OperationalDiagnostic> = map { it.toOperationalDiagnostic(operation) }

/**
 * Renders a compensating-restart failure (STR-256) as an
 * [OperationalDiagnostic]: unlike an operation failure, the record's tag and
 * message belong to the boundary/flow rather than the sync adapter — the
 * boundary supplies its own [restartTag] and the flow supplies its restart
 * [message] — and only the cause comes from the diagnostic.
 */
fun CloudSyncDiagnostic.toRestartDiagnostic(
    restartTag: String,
    message: String,
): OperationalDiagnostic =
    when (this) {
        is CloudSyncDiagnostic.OperationFailed ->
            OperationalDiagnostic(
                tag = restartTag,
                message = message,
                cause = cause,
            )
    }

/** List form of [toRestartDiagnostic], order preserved. */
fun List<CloudSyncDiagnostic>.toRestartDiagnostics(
    restartTag: String,
    message: String,
): List<OperationalDiagnostic> = map { it.toRestartDiagnostic(restartTag, message) }
