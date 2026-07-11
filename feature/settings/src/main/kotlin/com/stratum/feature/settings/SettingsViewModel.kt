package com.stratum.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.auth.LogoutUseCase
import com.stratum.domain.checkin.MarkCheckInPendingUseCase
import com.stratum.domain.checkin.ObserveCheckInPreferencesUseCase
import com.stratum.domain.checkin.SetCheckInEnabledUseCase
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ObserveThemePreferencesUseCase
import com.stratum.domain.theme.SetColorModeUseCase
import com.stratum.domain.theme.SetThemeFamilyUseCase
import com.stratum.domain.theme.ThemeFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeThemePreferences: ObserveThemePreferencesUseCase,
    observeCheckInPreferences: ObserveCheckInPreferencesUseCase,
    private val setThemeFamily: SetThemeFamilyUseCase,
    private val setColorMode: SetColorModeUseCase,
    private val setCheckInEnabled: SetCheckInEnabledUseCase,
    private val markCheckInPending: MarkCheckInPendingUseCase,
    private val logout: LogoutUseCase,
) : ViewModel() {

    private val debugStatusMessage = MutableStateFlow<String?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val showLogoutConfirm = MutableStateFlow(false)
    private val isLoggingOut = MutableStateFlow(false)

    fun userMessageShown() {
        _userMessage.value = null
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            observeThemePreferences(),
            observeCheckInPreferences(),
            debugStatusMessage,
            showLogoutConfirm,
            isLoggingOut,
        ) { theme, checkIn, debugStatus, logoutConfirm, loggingOut ->
            SettingsUiState(
                appearance = theme.colorMode.toAppearanceUi(),
                themeFamily = theme.family.toThemeFamilyUi(),
                checkInsEnabled = checkIn.enabled,
                showDebugTools = BuildConfig.DEBUG,
                debugStatusMessage = debugStatus,
                showLogoutConfirm = logoutConfirm,
                isLoggingOut = loggingOut,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(showDebugTools = BuildConfig.DEBUG),
        )

    fun onAppearanceSelected(appearance: AppearanceUi) {
        viewModelScope.launch {
            setColorMode(appearance.toDomain()).onFailure {
                _userMessage.value = MSG_PREFS_FAILED
            }
        }
    }

    fun onThemeFamilySelected(family: ThemeFamilyUi) {
        viewModelScope.launch {
            setThemeFamily(family.toDomain()).onFailure {
                _userMessage.value = MSG_PREFS_FAILED
            }
        }
    }

    fun onCheckInsEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            setCheckInEnabled(enabled).onFailure {
                _userMessage.value = MSG_PREFS_FAILED
            }
        }
    }

    /**
     * Debug-only: ensure check-ins are enabled and mark a pending coach check-in so the Programs
     * banner appears without waiting for the 20-workout re-extract gate.
     */
    fun forceCheckInPending() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            val enable = setCheckInEnabled(true)
            val pending = markCheckInPending(Instant.now())
            if (enable.isFailure || pending.isFailure) {
                _userMessage.value = MSG_PREFS_FAILED
                return@launch
            }
            debugStatusMessage.value = "Check-in pending — open Programs"
        }
    }

    fun onLogoutClick() {
        if (isLoggingOut.value) return
        showLogoutConfirm.value = true
    }

    fun onLogoutConfirmDismiss() {
        if (isLoggingOut.value) return
        showLogoutConfirm.value = false
    }

    fun onLogoutConfirmed() {
        if (isLoggingOut.value) return
        viewModelScope.launch {
            isLoggingOut.value = true
            showLogoutConfirm.value = false
            logout()
                .onFailure {
                    _userMessage.value = MSG_LOGOUT_FAILED
                }
            isLoggingOut.value = false
        }
    }

    private companion object {
        const val MSG_PREFS_FAILED = "Couldn't save that setting. Try again."
        const val MSG_LOGOUT_FAILED = "Couldn't sign out. Try again."
    }
}

/** Presentation-only settings state — no domain types in composables. */
data class SettingsUiState(
    val appearance: AppearanceUi = AppearanceUi.System,
    val themeFamily: ThemeFamilyUi = ThemeFamilyUi.Stratum,
    val checkInsEnabled: Boolean = true,
    /** True only in debug builds; release UI never shows developer tools. */
    val showDebugTools: Boolean = false,
    val debugStatusMessage: String? = null,
    val showLogoutConfirm: Boolean = false,
    val isLoggingOut: Boolean = false,
)

/** Light / Dark / System (device) appearance policy. */
enum class AppearanceUi {
    System,
    Light,
    Dark,
}

/** Product chrome family labels shown in Settings. */
enum class ThemeFamilyUi {
    /** Stratum Tonal — default ship target. */
    Stratum,

    /** Neo Brutalist iron/concrete family. */
    NeoBrutalist,
}

private fun ColorMode.toAppearanceUi(): AppearanceUi =
    when (this) {
        ColorMode.System -> AppearanceUi.System
        ColorMode.Light -> AppearanceUi.Light
        ColorMode.Dark -> AppearanceUi.Dark
    }

private fun AppearanceUi.toDomain(): ColorMode =
    when (this) {
        AppearanceUi.System -> ColorMode.System
        AppearanceUi.Light -> ColorMode.Light
        AppearanceUi.Dark -> ColorMode.Dark
    }

private fun ThemeFamily.toThemeFamilyUi(): ThemeFamilyUi =
    when (this) {
        ThemeFamily.Tonal -> ThemeFamilyUi.Stratum
        ThemeFamily.NeoBrutalist -> ThemeFamilyUi.NeoBrutalist
    }

private fun ThemeFamilyUi.toDomain(): ThemeFamily =
    when (this) {
        ThemeFamilyUi.Stratum -> ThemeFamily.Tonal
        ThemeFamilyUi.NeoBrutalist -> ThemeFamily.NeoBrutalist
    }
