package com.stratum.core.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.stratum.domain.auth.AuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseGoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val signInHostContext: SignInHostContext,
    @com.stratum.core.data.di.ApplicationScope private val externalScope: CoroutineScope,
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

    override suspend fun signInWithGoogle(): Result<UserProfile> = runCatching {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName,
        )
        require(resourceId != 0) {
            "Google Sign-In configuration missing: default_web_client_id not found."
        }
        val webClientId = context.getString(resourceId)
        val signInWithGoogleOption =
            GetSignInWithGoogleOption.Builder(serverClientId = webClientId).build()
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
        UserProfile(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoUrl?.toString(),
        )
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        firebaseAuth.signOut()
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
