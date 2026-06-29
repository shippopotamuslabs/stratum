package com.stratum

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.stratum.app.MainViewModel
import com.stratum.app.ui.SignInScreen
import com.stratum.core.data.auth.SignInHostContext
import com.stratum.core.ui.auth.StratumAuthUiState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.domain.logging.LoggingPort
import com.stratum.navigation.StratumNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var signInHostContext: SignInHostContext

    @Inject
    lateinit var loggingPort: LoggingPort

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signInHostContext.attach(this)
        initFirebaseAppCheck()
        enableEdgeToEdge()
        setContent {
            StratumTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val authState by viewModel.authState.collectAsStateWithLifecycle()
                when (authState) {
                    StratumAuthUiState.Loading -> SignInScreen(
                        authState = authState,
                        onSignInWithGoogle = viewModel::onSignInWithGoogle,
                    )
                    StratumAuthUiState.Unauthenticated,
                    is StratumAuthUiState.Error,
                    -> SignInScreen(
                        authState = authState,
                        onSignInWithGoogle = viewModel::onSignInWithGoogle,
                    )
                    is StratumAuthUiState.Authenticated -> StratumNavHost()
                }
            }
        }
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

    private companion object {
        const val TAG = "StratumAppCheck"
    }
}
