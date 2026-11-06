package com.stratum.core.infrastructure.auth

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.stratum.application.auth.AuthManager
import com.stratum.application.auth.LoginWithGoogleResult
import com.stratum.application.auth.SignOutOutcome
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseGoogleAuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val signInHostContext: SignInHostContext,
    private val loggingPort: LoggingPort,
    @ApplicationScope private val externalScope: CoroutineScope,
    @GoogleServerClientId private val googleServerClientId: String,
) : AuthManager {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        externalScope.launch {
            firebaseAuth.addAuthStateListener { auth ->
                val user = auth.currentUser
                _authState.value = if (user != null) {
                    AuthState.Authenticated(
                        UserProfile(
                            uid = user.uid,
                            displayName = user.displayName,
                            email = user.email,
                            photoUrl = user.photoUrl?.toString(),
                        ),
                    )
                } else {
                    AuthState.Unauthenticated
                }
            }
        }
    }

    override suspend fun signInWithGoogle(): LoginWithGoogleResult {
        try {
            return LoginWithGoogleResult.Success(signInWithGoogleOrThrow())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return recordSignInFailure(error)
        }
    }

    /**
     * The single population site for a classified sign-in failure (E2).
     *
     * Single-locals invariant: [error]'s classification and message are
     * computed exactly once, into the single locals `kind` and `message`,
     * and those same locals populate BOTH carriers — the returned
     * [LoginWithGoogleResult.Failed] and the [AuthState.Error] auth-state
     * emission — so the two can never diverge. The state's message is
     * non-null by type, so a null `message` rides the state as "" while the
     * outcome branch keeps the null. The failure is logged exactly once
     * here, at the adapter boundary.
     *
     * Internal (not private) as the unit-test seam: tests drive this handler
     * directly and assert the two carriers agree.
     */
    internal fun recordSignInFailure(error: Throwable): LoginWithGoogleResult.Failed {
        loggingPort.error(TAG, "Google sign-in failed: ${error.message}", error)
        val kind = error.signInFailureKind()
        val message = error.message
        _authState.value = AuthState.Error(kind, message ?: "")
        return LoginWithGoogleResult.Failed(kind, message)
    }

    private suspend fun signInWithGoogleOrThrow(): UserProfile {
        require(googleServerClientId.isNotBlank()) {
            "Google Sign-In configuration missing: default_web_client_id is blank."
        }
        val signInWithGoogleOption =
            GetSignInWithGoogleOption.Builder(serverClientId = googleServerClientId).build()
        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()
        val hostContext = signInHostContext.requireContext()
        val result = credentialManager.getCredential(hostContext, request)
        val credential = result.credential
        val firebaseCredential = when {
            credential is GoogleIdTokenCredential -> {
                GoogleAuthProvider.getCredential(credential.idToken, null)
            }
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            }
            else -> error("Unexpected credential type: ${credential.type}")
        }
        val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
        val user = authResult.user ?: error("Firebase sign in failed")
        return UserProfile(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoUrl?.toString(),
        )
    }

    override suspend fun signOut(): SignOutOutcome {
        try {
            firebaseAuth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Google sign-out failed: ${error.message}", error)
            return SignOutOutcome.Failed
        }
        return SignOutOutcome.Success
    }

    private companion object {
        const val TAG = "StratumGoogleAuth"
    }
}
