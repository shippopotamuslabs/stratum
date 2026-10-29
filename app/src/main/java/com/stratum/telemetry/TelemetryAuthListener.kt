package com.stratum.telemetry

import com.stratum.domain.auth.AuthState
import com.stratum.application.auth.GetAuthStateUseCase
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.collectLatestRestarting
import com.stratum.domain.telemetry.CrashTelemetryPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Observes authentication state changes to bind and clear user ID attribution on [CrashTelemetryPort].
 */
@Singleton
class TelemetryAuthListener @Inject constructor(
    private val getAuthState: GetAuthStateUseCase,
    private val crashTelemetryPort: CrashTelemetryPort,
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

    private companion object {
        const val TAG = "TelemetryAuth"
    }
}
