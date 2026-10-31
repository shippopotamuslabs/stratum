package com.stratum

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.app.MainViewModel
import com.stratum.app.ShellUiState
import com.stratum.app.ui.SignInScreen
import com.stratum.core.infrastructure.auth.SignInHostContext
import com.stratum.core.infrastructure.subscription.BillingHostContext
import com.stratum.core.ui.component.LocalUiRenderErrorReporter
import com.stratum.core.ui.component.UiRenderErrorReporter
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.telemetry.CrashTelemetryPort
import com.stratum.feature.workout.notification.WorkoutNavExtras
import com.stratum.navigation.StratumNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var signInHostContext: SignInHostContext

    @Inject
    lateinit var billingHostContext: BillingHostContext

    @Inject
    lateinit var loggingPort: LoggingPort

    @Inject
    lateinit var crashTelemetryPort: CrashTelemetryPort

    private var pendingWorkoutId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signInHostContext.attach(this)
        billingHostContext.attach(this)
        // Transparent system bar scrims so scaffold/list surfaces truly reach the glass edge.
        // Default enableEdgeToEdge() applies a dark nav-bar scrim that reads as a dead strip
        // under the gesture pill on Pixel gesture navigation.
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.auto(
                    lightScrim = AndroidColor.TRANSPARENT,
                    darkScrim = AndroidColor.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.auto(
                    lightScrim = AndroidColor.TRANSPARENT,
                    darkScrim = AndroidColor.TRANSPARENT,
                ),
        )
        pendingWorkoutId = intent.workoutIdExtra()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
            StratumTheme(preferences = themePreferences) {
                val renderReporter =
                    remember {
                        UiRenderErrorReporter { error ->
                            loggingPort.error("UiRender", "UI render failure: ${error.message}", error)
                        }
                    }
                CompositionLocalProvider(LocalUiRenderErrorReporter provides renderReporter) {
                    val shellState by viewModel.shellUiState.collectAsStateWithLifecycle()
                    if (shellState is ShellUiState.Ready) {
                        StratumNavHost(
                            pendingWorkoutId = pendingWorkoutId,
                            onPendingWorkoutConsumed = { pendingWorkoutId = null },
                            onScreenChanged = crashTelemetryPort::setScreen,
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            crashTelemetryPort.setScreen("SignInScreen")
                        }
                        SignInScreen(
                            shellState = shellState,
                            onSignInWithGoogle = viewModel::onSignInWithGoogle,
                            onRetrySession = viewModel::onRetrySession,
                            onSignOut = viewModel::onSignOutFromShell,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWorkoutId = intent.workoutIdExtra()
    }

    override fun onDestroy() {
        signInHostContext.detach(this)
        billingHostContext.detach(this)
        super.onDestroy()
    }

    private fun Intent.workoutIdExtra(): String? =
        getStringExtra(WorkoutNavExtras.EXTRA_WORKOUT_ID)?.takeIf { it.isNotBlank() }
}
