package com.stratum.feature.settings

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.theme.FakeThemePreferencesRepository
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ObserveThemePreferencesUseCase
import com.stratum.domain.theme.SetColorModeUseCase
import com.stratum.domain.theme.SetThemeFamilyUseCase
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class SettingsViewModelTest {

    private val fakeThemePreferences = FakeThemePreferencesRepository()
    private lateinit var subject: SettingsViewModel

    @BeforeEach
    fun setup() {
        fakeThemePreferences.seed(ThemePreferences())
        subject = buildViewModel()
    }

    private fun buildViewModel(): SettingsViewModel =
        SettingsViewModel(
            observeThemePreferences = ObserveThemePreferencesUseCase(fakeThemePreferences),
            setThemeFamily = SetThemeFamilyUseCase(fakeThemePreferences),
            setColorMode = SetColorModeUseCase(fakeThemePreferences),
        )

    @Test
    @DisplayName("Given default prefs when observed then state is System + Stratum")
    fun defaultState() = runTest {
        subject.uiState.test {
            assertEquals(
                SettingsUiState(
                    appearance = AppearanceUi.System,
                    themeFamily = ThemeFamilyUi.Stratum,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given seeded Neo + Dark when observed then presentation enums match")
    fun mapsSeededPreferences() = runTest {
        fakeThemePreferences.seed(
            ThemePreferences(
                family = ThemeFamily.NeoBrutalist,
                colorMode = ColorMode.Dark,
            ),
        )
        subject = buildViewModel()

        subject.uiState.test {
            assertEquals(
                SettingsUiState(
                    appearance = AppearanceUi.Dark,
                    themeFamily = ThemeFamilyUi.NeoBrutalist,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("When appearance selected then repository stores matching ColorMode")
    fun appearanceSelectionPersists() = runTest {
        subject.onAppearanceSelected(AppearanceUi.Light)
        advanceUntilIdle()

        assertEquals(ColorMode.Light, fakeThemePreferences.current.colorMode)
        subject.uiState.test {
            assertEquals(AppearanceUi.Light, awaitItem().appearance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("When theme family selected then repository stores NeoBrutalist")
    fun themeFamilySelectionPersists() = runTest {
        subject.onThemeFamilySelected(ThemeFamilyUi.NeoBrutalist)
        advanceUntilIdle()

        assertEquals(ThemeFamily.NeoBrutalist, fakeThemePreferences.current.family)
        subject.uiState.test {
            assertEquals(ThemeFamilyUi.NeoBrutalist, awaitItem().themeFamily)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
