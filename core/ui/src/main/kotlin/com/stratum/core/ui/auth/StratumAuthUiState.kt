package com.stratum.core.ui.auth

import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile

sealed interface StratumAuthUiState {
    data object Loading : StratumAuthUiState
    data object Unauthenticated : StratumAuthUiState
    data class Authenticated(
        val displayName: String?,
        val email: String?,
    ) : StratumAuthUiState

    data class Error(val message: String) : StratumAuthUiState
}

data class StratumUserUiState(
    val displayName: String?,
    val email: String?,
)

fun AuthState.toStratumAuthUiState(): StratumAuthUiState = when (this) {
    AuthState.Loading -> StratumAuthUiState.Loading
    AuthState.Unauthenticated -> StratumAuthUiState.Unauthenticated
    is AuthState.Authenticated -> StratumAuthUiState.Authenticated(
        displayName = user.displayName,
        email = user.email,
    )
    is AuthState.Error -> StratumAuthUiState.Error(message)
}

fun UserProfile.toUi(): StratumUserUiState = StratumUserUiState(
    displayName = displayName,
    email = email,
)
