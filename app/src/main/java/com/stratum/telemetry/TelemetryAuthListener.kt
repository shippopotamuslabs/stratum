package com.stratum.telemetry

import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.GetAuthStateUseCase
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.telemetry.CrashTelemetryPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Observes authentication state changes to bind and clear user ID attribution on [CrashTelemetryPort].
 */
@Singleton
class TelemetryAuthListener @Inject constructor(
    private val getAuthState: GetAuthStateUseCase,
    private val crashTelemetryPort: CrashTelemetryPort,
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
                            crashTelemetryPort.setUserId(state.user.uid)
                        }
                        AuthState.Unauthenticated, is AuthState.Error -> {
                            crashTelemetryPort.setUserId(null)
                        }
                        AuthState.Loading -> Unit
                    }
                }
            }
    }
}
