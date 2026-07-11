package com.stratum

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.stratum.app.MainViewModel
import com.stratum.app.ShellUiState
import com.stratum.app.ui.SignInScreen
import com.stratum.core.data.auth.SignInHostContext
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.domain.logging.LoggingPort
import com.stratum.feature.workout.notification.WorkoutNavExtras
import com.stratum.navigation.StratumNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var signInHostContext: SignInHostContext

    @Inject
    lateinit var loggingPort: LoggingPort

    private var pendingWorkoutId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signInHostContext.attach(this)
        initFirebaseAppCheck()
        enableEdgeToEdge()
        pendingWorkoutId = intent.workoutIdExtra()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
            StratumTheme(preferences = themePreferences) {
                val shellState by viewModel.shellUiState.collectAsStateWithLifecycle()
                if (shellState is ShellUiState.Ready) {
                    StratumNavHost(
                        pendingWorkoutId = pendingWorkoutId,
                        onPendingWorkoutConsumed = { pendingWorkoutId = null },
                    )
                } else {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWorkoutId = intent.workoutIdExtra()
    }

    override fun onDestroy() {
        signInHostContext.detach(this)
        super.onDestroy()
    }

    private fun initFirebaseAppCheck() {
        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            logDebugAppCheckTokenHint(appCheck)
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
        }
    }

    /**
     * Surfaces App Check setup issues early. On debug builds, register the secret printed by
     * `DebugAppCheckProvider` (or logged here on failure) in Firebase Console → App Check →
     * Manage debug tokens. Callable inference endpoints enforce App Check server-side.
     */
    private fun logDebugAppCheckTokenHint(appCheck: FirebaseAppCheck) {
        appCheck.getAppCheckToken(false)
            .addOnSuccessListener {
                loggingPort.info(TAG, "App Check debug token obtained successfully.")
            }
            .addOnFailureListener { error ->
                loggingPort.error(
                    TAG,
                    "App Check debug token failed (register the debug secret from logcat in " +
                        "Firebase Console → App Check → Manage debug tokens): ${error.message}",
                    error,
                )
            }
    }

    private fun Intent.workoutIdExtra(): String? =
        getStringExtra(WorkoutNavExtras.EXTRA_WORKOUT_ID)?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "StratumAppCheck"
    }
}
