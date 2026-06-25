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
import com.stratum.navigation.StratumNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var signInHostContext: SignInHostContext

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
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
        }
    }
}
