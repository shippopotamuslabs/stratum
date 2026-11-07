package com.stratum.sync

import com.stratum.domain.auth.AuthState
import com.stratum.app.DiagnosticDrain
import com.stratum.application.auth.GetAuthStateUseCase
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.coroutines.collectLatestRestarting
import com.stratum.application.sync.CloudSyncOperation
import com.stratum.application.sync.CloudSyncOutcome
import com.stratum.application.sync.StartCloudSyncUseCase
import com.stratum.application.sync.StopCloudSyncUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Starts cloud sync when Firebase auth becomes [AuthState.Authenticated] and stops on sign-out.
 *
 * Domain use cases only — feature gate, WorkManager retry, and ownership wipe live inside
 * [com.stratum.core.infrastructure.sync.FirestoreCloudSyncAdapter].
 */
@Singleton
class CloudSyncAuthListener @Inject constructor(
    private val getAuthState: GetAuthStateUseCase,
    private val startCloudSync: StartCloudSyncUseCase,
    private val stopCloudSync: StopCloudSyncUseCase,
    private val diagnosticDrain: DiagnosticDrain,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                getAuthState().collectLatestRestarting(
                    { t, m, e -> diagnosticDrain.recordError(t, m, e) },
                    TAG,
                ) { state ->
                    when (state) {
                        is AuthState.Authenticated -> {
                            // This listener is the single recorder for its own
                            // start invocations (STR-238): a Failed outcome's
                            // diagnostics are drained here exactly once through
                            // the drain facade's sync entry point, rendered by
                            // the shared factory in the sync adapter's
                            // tag/message shape for a start failure so the
                            // record matches the adapter's legacy log line for
                            // the same failure. Restart invocations are a
                            // different path — their diagnostics ride the
                            // logout / account-deletion results and drain at
                            // the Settings / shell boundaries, never here.
                            val outcome = startCloudSync(state.user.uid)
                            if (outcome is CloudSyncOutcome.Failed) {
                                diagnosticDrain.drainSync(
                                    outcome.diagnostics,
                                    CloudSyncOperation.Start,
                                )
                            }
                        }
                        AuthState.Unauthenticated, is AuthState.Error -> {
                            stopCloudSync()
                        }
                        AuthState.Loading -> Unit
                    }
                }
            }
    }

    private companion object {
        const val TAG = "CloudSyncAuth"
    }
}
