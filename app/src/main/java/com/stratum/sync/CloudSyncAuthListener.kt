package com.stratum.sync

import com.stratum.domain.auth.AuthState
import com.stratum.application.auth.GetAuthStateUseCase
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.collectLatestRestarting
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
    private val loggingPort: LoggingPort,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                getAuthState().collectLatestRestarting(loggingPort, TAG) { state ->
                    when (state) {
                        is AuthState.Authenticated -> {
                            // A failed start is error-logged exactly once by
                            // the sync adapter at its own boundary. The Failed
                            // outcome does carry diagnostics for the boundaries
                            // that must classify or drain them (the sync
                            // worker, the compensating restart after a logout /
                            // account-deletion failure) — this listener is not
                            // a drain site, because the adapter already logged
                            // the failure once.
                            startCloudSync(state.user.uid)
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
