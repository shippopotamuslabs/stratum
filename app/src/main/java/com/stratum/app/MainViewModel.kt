package com.stratum.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.app.theme.toUi
import com.stratum.core.ui.auth.toGoogleSignInMessage
import com.stratum.core.ui.theme.ThemePreferences
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.GetAuthStateUseCase
import com.stratum.domain.auth.LoginWithGoogleUseCase
import com.stratum.domain.auth.LogoutUseCase
import com.stratum.domain.sync.CloudSessionState
import com.stratum.domain.sync.ObserveCloudSessionStateUseCase
import com.stratum.domain.sync.StartCloudSyncUseCase
import com.stratum.domain.theme.ObserveThemePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    getAuthState: GetAuthStateUseCase,
    private val loginWithGoogle: LoginWithGoogleUseCase,
    private val logout: LogoutUseCase,
    private val startCloudSync: StartCloudSyncUseCase,
    observeSessionState: ObserveCloudSessionStateUseCase,
    observeThemePreferences: ObserveThemePreferencesUseCase,
) : ViewModel() {
    private val signInError = MutableStateFlow<String?>(null)
    private val authenticatedUserId = MutableStateFlow<String?>(null)

    /**
     * Sticky: once the shell has entered [ShellUiState.Ready] for the current authenticated
     * user, stay Ready until sign-out. Prevents logout teardown (session → Inactive) from
     * unmounting NavHost and cancelling Settings-scoped logout work mid-flight.
     */
    private val hasEnteredApp = MutableStateFlow(false)

    private val authStateFlow = getAuthState()
    private val sessionStateFlow = observeSessionState()

    val shellUiState: StateFlow<ShellUiState> =
        combine(
            authStateFlow,
            sessionStateFlow,
            signInError,
            hasEnteredApp,
        ) { auth, session, errorMessage, entered ->
            mapShellState(auth, session, errorMessage, entered)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ShellUiState.CheckingAuth,
        )

    /** Composition theme prefs for the app shell — already mapped off domain types. */
    val themePreferences: StateFlow<ThemePreferences> =
        observeThemePreferences()
            .map { it.toUi() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemePreferences(),
            )

    init {
        viewModelScope.launch {
            combine(authStateFlow, sessionStateFlow) { auth, session ->
                auth to session
            }.collect { (auth, session) ->
                when (auth) {
                    is AuthState.Authenticated -> {
                        authenticatedUserId.value = auth.user.uid
                        if (session is CloudSessionState.Ready) {
                            hasEnteredApp.value = true
                        }
                    }
                    else -> {
                        authenticatedUserId.value = null
                        hasEnteredApp.value = false
                    }
                }
            }
        }
    }

    fun onSignInWithGoogle() {
        viewModelScope.launch {
            signInError.value = null
            loginWithGoogle().onFailure { error ->
                signInError.value = error.toGoogleSignInMessage()
            }
        }
    }

    fun onRetrySession() {
        viewModelScope.launch {
            val uid = authenticatedUserId.value ?: return@launch
            signInError.value = null
            startCloudSync(uid)
        }
    }

    fun onSignOutFromShell() {
        viewModelScope.launch {
            signInError.value = null
            logout().onFailure {
                // Keep shell copy non-technical; detail is logged by the auth layer.
                signInError.value = "Couldn't sign out. Try again."
            }
        }
    }

    private fun mapShellState(
        auth: AuthState,
        session: CloudSessionState,
        signInErrorMessage: String?,
        hasEntered: Boolean,
    ): ShellUiState {
        if (signInErrorMessage != null && auth !is AuthState.Authenticated) {
            return ShellUiState.SignInError(signInErrorMessage)
        }
        return when (auth) {
            AuthState.Loading -> ShellUiState.CheckingAuth
            AuthState.Unauthenticated -> ShellUiState.SignedOut
            is AuthState.Error ->
                ShellUiState.SignInError(RuntimeException(auth.message).toGoogleSignInMessage())
            is AuthState.Authenticated -> {
                if (hasEntered) {
                    ShellUiState.Ready
                } else {
                    when (session) {
                        is CloudSessionState.Ready -> ShellUiState.Ready
                        is CloudSessionState.Failed ->
                            ShellUiState.SessionFailed(session.message)
                        CloudSessionState.Inactive,
                        is CloudSessionState.Establishing,
                        -> ShellUiState.PreparingSession
                    }
                }
            }
        }
    }
}
