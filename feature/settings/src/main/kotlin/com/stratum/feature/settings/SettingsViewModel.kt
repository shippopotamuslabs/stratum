package com.stratum.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.auth.AuthDeleteAfterAccountWipeException
import com.stratum.domain.auth.DeleteAccountUseCase
import com.stratum.domain.auth.LocalCleanupAfterAccountDeletionException
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
import kotlinx.coroutines.flow.update
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
    private val deleteAccount: DeleteAccountUseCase,
) : ViewModel() {

    private val debugStatusMessage = MutableStateFlow<String?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    /** Exclusive account lifecycle UI — only one dialog or in-flight op at a time. */
    private val accountUi = MutableStateFlow<AccountUi>(AccountUi.Idle)

    fun userMessageShown() {
        _userMessage.value = null
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            observeThemePreferences(),
            observeCheckInPreferences(),
            debugStatusMessage,
            accountUi,
        ) { theme, checkIn, debugStatus, account ->
            SettingsUiState(
                appearance = theme.colorMode.toAppearanceUi(),
                themeFamily = theme.family.toThemeFamilyUi(),
                checkInsEnabled = checkIn.enabled,
                showDebugTools = BuildConfig.DEBUG,
                debugStatusMessage = debugStatus,
                showLogoutConfirm = account is AccountUi.ConfirmLogout,
                isLoggingOut = account is AccountUi.LoggingOut,
                showDeleteConfirm = account is AccountUi.ConfirmDelete,
                isDeletingAccount = account is AccountUi.DeletingAccount,
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
        accountUi.update { current ->
            if (current is AccountUi.Idle) AccountUi.ConfirmLogout else current
        }
    }

    fun onLogoutConfirmDismiss() {
        accountUi.update { current ->
            if (current is AccountUi.ConfirmLogout) AccountUi.Idle else current
        }
    }

    fun onLogoutConfirmed() {
        var claimed = false
        accountUi.update { current ->
            if (current is AccountUi.ConfirmLogout) {
                claimed = true
                AccountUi.LoggingOut
            } else {
                current
            }
        }
        if (!claimed) return
        viewModelScope.launch {
            try {
                logout().onFailure {
                    _userMessage.value = MSG_LOGOUT_FAILED
                }
            } finally {
                accountUi.value = AccountUi.Idle
            }
        }
    }

    fun onDeleteAccountClick() {
        accountUi.update { current ->
            if (current is AccountUi.Idle) AccountUi.ConfirmDelete else current
        }
    }

    fun onDeleteAccountConfirmDismiss() {
        accountUi.update { current ->
            if (current is AccountUi.ConfirmDelete) AccountUi.Idle else current
        }
    }

    fun onDeleteAccountConfirmed() {
        var claimed = false
        accountUi.update { current ->
            if (current is AccountUi.ConfirmDelete) {
                claimed = true
                AccountUi.DeletingAccount
            } else {
                current
            }
        }
        if (!claimed) return
        viewModelScope.launch {
            try {
                deleteAccount()
                    .onFailure { error ->
                        _userMessage.value =
                            when (error) {
                                is LocalCleanupAfterAccountDeletionException ->
                                    MSG_DELETE_LOCAL_CLEANUP_FAILED
                                is AuthDeleteAfterAccountWipeException ->
                                    MSG_DELETE_AUTH_FAILED
                                else -> MSG_DELETE_FAILED
                            }
                    }
            } finally {
                accountUi.value = AccountUi.Idle
            }
        }
    }

    private sealed class AccountUi {
        data object Idle : AccountUi()
        data object ConfirmLogout : AccountUi()
        data object ConfirmDelete : AccountUi()
        data object LoggingOut : AccountUi()
        data object DeletingAccount : AccountUi()
    }

    private companion object {
        const val MSG_PREFS_FAILED = "Couldn't save that setting. Try again."
        const val MSG_LOGOUT_FAILED = "Couldn't sign out. Try again."
        const val MSG_DELETE_FAILED = "Couldn't delete account. Try again."
        const val MSG_DELETE_AUTH_FAILED =
            "Your training data was removed, but the account could not be fully closed. " +
                "Try Delete account again, or use Log out and contact support."
        const val MSG_DELETE_LOCAL_CLEANUP_FAILED =
            "Your cloud data was deleted, but this device could not clear local data. " +
                "Uninstall Stratum or clear the app’s storage in system settings."
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
    val showDeleteConfirm: Boolean = false,
    val isDeletingAccount: Boolean = false,
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
