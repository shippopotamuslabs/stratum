package com.stratum.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.app.theme.toUi
import com.stratum.core.ui.auth.loginFailureToGoogleSignInMessage
import com.stratum.core.ui.theme.ThemePreferences
import com.stratum.domain.auth.AuthState
import com.stratum.application.auth.GetAuthStateUseCase
import com.stratum.application.auth.LoginWithGoogleResult
import com.stratum.application.auth.LoginWithGoogleUseCase
import com.stratum.application.auth.LogoutResult
import com.stratum.application.auth.LogoutUseCase
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.sync.CloudSyncDiagnostic
import com.stratum.application.sync.CloudSyncOutcome
import com.stratum.domain.sync.CloudSessionState
import com.stratum.application.sync.ObserveCloudSessionStateUseCase
import com.stratum.application.sync.StartCloudSyncUseCase
import com.stratum.application.theme.ObserveThemePreferencesUseCase
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
    private val diagnosticDrain: DiagnosticDrain,
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
            val uid = authenticatedUserId.value ?: return@launch
            signInError.value = null
            val outcome = startCloudSync(uid)
            if (outcome is CloudSyncOutcome.Failed) {
                drainSyncDiagnostics(outcome.diagnostics, "start failed")
            }
        }
    }

    fun onSignOutFromShell() {
        viewModelScope.launch {
            signInError.value = null
            val result = logout()
            if (result !is LogoutResult.Success) {
                when (result) {
                    is LogoutResult.WipeFailed -> {
                        // STR-238: the primary wipe failure drains here too,
                        // exactly once, alongside the compensating restart's.
                        drainSyncDiagnostics(result.wipeDiagnostics, "endSessionAndWipe failed")
                        drainRestartDiagnostics(result.restartDiagnostics)
                    }
                    is LogoutResult.SignOutFailed -> {
                        // STR-238: the primary sign-out failure drains here
                        // too, exactly once, alongside the restart's.
                        diagnosticDrain.drain(result.signOutDiagnostics)
                        drainRestartDiagnostics(result.restartDiagnostics)
                    }
                    LogoutResult.Success -> Unit
                }
                // Keep shell copy non-technical.
                signInError.value = "Couldn't sign out. Try again."
            }
        }
    }

    /**
     * STR-238: drains a sync operation's failure diagnostics exactly once,
     * each rendered with the sync adapter's tag/message shape for that
     * operation ([operation] is "start failed" or "endSessionAndWipe
     * failed") so the record matches the adapter's legacy log line for the
     * same failure.
     */
    private fun drainSyncDiagnostics(
        diagnostics: List<CloudSyncDiagnostic>,
        operation: String,
    ) {
        diagnostics.forEach { diagnostic ->
            when (diagnostic) {
                is CloudSyncDiagnostic.OperationFailed ->
                    diagnosticDrain.drain(
                        OperationalDiagnostic(
                            tag = TAG_SYNC_ADAPTER,
                            message = "$operation: ${diagnostic.cause.message}",
                            cause = diagnostic.cause,
                        ),
                    )
            }
        }
    }

    /** STR-256: a failed compensating sync restart is drained here exactly once. */
    private fun drainRestartDiagnostics(diagnostics: List<CloudSyncDiagnostic>) {
        diagnostics.forEach { diagnostic ->
            when (diagnostic) {
                is CloudSyncDiagnostic.OperationFailed ->
                    diagnosticDrain.drain(
                        OperationalDiagnostic(
                            tag = TAG_AUTH,
                            message = MSG_SYNC_RESTART_FAILED,
                            cause = diagnostic.cause,
                        ),
                    )
            }
        }
    }

    private companion object {
        const val TAG_AUTH = "ShellAuth"

        /** The sync adapter's log tag — sync-failure records keep its shape. */
        const val TAG_SYNC_ADAPTER = "FirestoreCloudSync"
        const val MSG_SYNC_RESTART_FAILED = "Cloud sync restart failed after sign-out failure."
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
