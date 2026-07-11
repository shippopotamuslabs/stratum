package com.stratum.app

/**
 * Single app-shell presentation state. Composes auth + cloud session without dual
 * boolean algebra in the Activity.
 */
sealed interface ShellUiState {
    /** Firebase auth still resolving. */
    data object CheckingAuth : ShellUiState

    /** Not signed in — show Google Sign-In. */
    data object SignedOut : ShellUiState

    /** Sign-in failed (or overlay error from Google credential flow). */
    data class SignInError(val message: String) : ShellUiState

    /** Authenticated; establishing account-owned local data for first entry. */
    data object PreparingSession : ShellUiState

    /** Authenticated but could not establish a usable local session. */
    data class SessionFailed(val message: String) : ShellUiState

    /** Main app (NavHost) may be shown. */
    data object Ready : ShellUiState
}
