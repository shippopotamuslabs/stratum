package com.stratum.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.app.theme.toUi
import com.stratum.core.ui.auth.loginFailureToGoogleSignInMessage
import com.stratum.core.ui.theme.ThemePreferences
import com.stratum.application.auth.LoginWithGoogleResult
import com.stratum.application.auth.LoginWithGoogleUseCase
import com.stratum.application.auth.LogoutResult
import com.stratum.application.auth.LogoutUseCase
import com.stratum.application.auth.ObserveShellStateUseCase
import com.stratum.application.auth.records
import com.stratum.application.sync.CloudSyncOperation
import com.stratum.application.sync.CloudSyncOutcome
import com.stratum.application.sync.StartCloudSyncUseCase
import com.stratum.application.theme.ObserveThemePreferencesUseCase
import com.stratum.domain.auth.AuthState
import com.stratum.domain.sync.CloudSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// STR-216: STR-207 §2 exception — `themePreferences` is the sanctioned shell theme
// stream alongside `shellUiState`; suppressed per-class with ticket reference.
@Suppress("ForbiddenMultipleStateFlowInViewModel")
@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeShellState: ObserveShellStateUseCase,
    private val loginWithGoogle: LoginWithGoogleUseCase,
    private val logout: LogoutUseCase,
    private val startCloudSync: StartCloudSyncUseCase,
    observeThemePreferences: ObserveThemePreferencesUseCase,
    private val diagnosticDrain: DiagnosticDrain,
) : ViewModel() {
    private val signInError = MutableStateFlow<String?>(null)

    val shellUiState: StateFlow<ShellUiState> =
        combine(
            observeShellState(),
            signInError,
        ) { snapshot, errorMessage ->
            mapShellState(snapshot.auth, snapshot.session, errorMessage, snapshot.hasEnteredApp)
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

    fun onSignInWithGoogle() {
        viewModelScope.launch {
            signInError.value = null
            when (val result = loginWithGoogle()) {
                is LoginWithGoogleResult.Success -> Unit
                is LoginWithGoogleResult.Failed -> {
                    // STR-238: the sign-in failure's §10 diagnostics are
                    // drained here exactly once — this boundary is the
                    // single recording owner (the adapter records nothing).
                    diagnosticDrain.drain(result.diagnostics)
                    signInError.value =
                        loginFailureToGoogleSignInMessage(result.kind, result.message)
                }
            }
        }
    }

    fun onRetrySession() {
        viewModelScope.launch {
            val uid = observeShellState().first().authenticatedUserId ?: return@launch
            signInError.value = null
            val outcome = startCloudSync(uid)
            if (outcome is CloudSyncOutcome.Failed) {
                diagnosticDrain.drainSync(outcome.diagnostics, CloudSyncOperation.Start)
            }
        }
    }

    fun onSignOutFromShell() {
        viewModelScope.launch {
            signInError.value = null
            val result = logout()
            if (result !is LogoutResult.Success) {
                // STR-238: the result's accessor returns the branch's full
                // ordered record list (primary failure first, compensating
                // restart second, restart rendered under this boundary's
                // tag) — drained here exactly once in a single call.
                diagnosticDrain.drain(result.records(TAG_AUTH))
                // Keep shell copy non-technical.
                signInError.value = "Couldn't sign out. Try again."
            }
        }
    }

    private companion object {
        /** This boundary's tag for compensating-restart records. */
        const val TAG_AUTH = "ShellAuth"
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
                ShellUiState.SignInError(loginFailureToGoogleSignInMessage(auth.kind, auth.message))
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
