package com.stratum.sync

import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.GetAuthStateUseCase
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.sync.StartCloudSyncUseCase
import com.stratum.domain.sync.StopCloudSyncUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Starts cloud sync when Firebase auth becomes [AuthState.Authenticated] and stops on sign-out.
 *
 * Domain use cases only — feature gate, WorkManager retry, and ownership wipe live inside
 * [com.stratum.core.data.sync.FirestoreCloudSyncAdapter].
 */
@Singleton
class CloudSyncAuthListener @Inject constructor(
    private val getAuthState: GetAuthStateUseCase,
    private val startCloudSync: StartCloudSyncUseCase,
    private val stopCloudSync: StopCloudSyncUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                getAuthState().collectLatest { state ->
                    when (state) {
                        is AuthState.Authenticated -> {
                            // start() owns isolation + optional network sync on IO.
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
}
