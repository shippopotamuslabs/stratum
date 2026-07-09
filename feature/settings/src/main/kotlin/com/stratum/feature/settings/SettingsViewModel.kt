package com.stratum.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val debugStatusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            observeThemePreferences(),
            observeCheckInPreferences(),
            debugStatusMessage,
        ) { theme, checkIn, debugStatus ->
            SettingsUiState(
                appearance = theme.colorMode.toAppearanceUi(),
                themeFamily = theme.family.toThemeFamilyUi(),
                checkInsEnabled = checkIn.enabled,
                showDebugTools = BuildConfig.DEBUG,
                debugStatusMessage = debugStatus,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(showDebugTools = BuildConfig.DEBUG),
        )

    fun onAppearanceSelected(appearance: AppearanceUi) {
        viewModelScope.launch {
            setColorMode(appearance.toDomain())
        }
    }

    fun onThemeFamilySelected(family: ThemeFamilyUi) {
        viewModelScope.launch {
            setThemeFamily(family.toDomain())
        }
    }

    fun onCheckInsEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            setCheckInEnabled(enabled)
        }
    }

    /**
     * Debug-only: ensure check-ins are enabled and mark a pending coach check-in so the Programs
     * banner appears without waiting for the 20-workout re-extract gate.
     */
    fun forceCheckInPending() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            setCheckInEnabled(true)
            markCheckInPending(Instant.now())
            debugStatusMessage.value = "Check-in pending — open Programs"
        }
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
