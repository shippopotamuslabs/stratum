package com.stratum.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.app.theme.toUi
import com.stratum.core.ui.auth.toGoogleSignInMessage
import com.stratum.core.ui.auth.StratumAuthUiState
import com.stratum.core.ui.auth.toStratumAuthUiState
import com.stratum.core.ui.theme.ThemePreferences
import com.stratum.domain.auth.GetAuthStateUseCase
import com.stratum.domain.auth.LoginWithGoogleUseCase
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
    observeThemePreferences: ObserveThemePreferencesUseCase,
) : ViewModel() {
    private val signInError = MutableStateFlow<String?>(null)

    val authState: StateFlow<StratumAuthUiState> =
        combine(
            getAuthState(),
            signInError,
        ) { domainAuthState, errorMessage ->
            errorMessage?.let(StratumAuthUiState::Error)
                ?: domainAuthState.toStratumAuthUiState()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StratumAuthUiState.Loading,
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
            loginWithGoogle().onFailure { error ->
                signInError.value = error.toGoogleSignInMessage()
            }
        }
    }
}
