package com.stratum.feature.settings

import com.stratum.core.testing.logging.FakeLoggingPort

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.checkin.FakeCheckInPreferencesRepository
import com.stratum.core.testing.theme.FakeThemePreferencesRepository
import com.stratum.domain.checkin.CheckInPreferences
import com.stratum.domain.checkin.MarkCheckInPendingUseCase
import com.stratum.domain.checkin.ObserveCheckInPreferencesUseCase
import com.stratum.domain.checkin.SetCheckInEnabledUseCase
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class SettingsViewModelTest {

    private val fakeThemePreferences = FakeThemePreferencesRepository()
    private val fakeCheckInPreferences = FakeCheckInPreferencesRepository()
    private lateinit var subject: SettingsViewModel

    @BeforeEach
    fun setup() {
        fakeThemePreferences.seed(ThemePreferences())
        fakeCheckInPreferences.seed(CheckInPreferences())
        subject = buildViewModel()
    }

    private fun buildViewModel(): SettingsViewModel =
        SettingsViewModel(
            observeThemePreferences = ObserveThemePreferencesUseCase(fakeThemePreferences),
            observeCheckInPreferences = ObserveCheckInPreferencesUseCase(fakeCheckInPreferences),
            setThemeFamily = SetThemeFamilyUseCase(fakeThemePreferences, FakeLoggingPort()),
            setColorMode = SetColorModeUseCase(fakeThemePreferences, FakeLoggingPort()),
            setCheckInEnabled = SetCheckInEnabledUseCase(fakeCheckInPreferences, FakeLoggingPort()),
            markCheckInPending = MarkCheckInPendingUseCase(fakeCheckInPreferences, FakeLoggingPort()),
        )

    @Test
    @DisplayName("Given default prefs when observed then state is System + Stratum + check-ins on")
    fun defaultState() = runTest {
        subject.uiState.test {
            val state = awaitItem()
            assertEquals(AppearanceUi.System, state.appearance)
            assertEquals(ThemeFamilyUi.Stratum, state.themeFamily)
            assertTrue(state.checkInsEnabled)
            // showDebugTools follows BuildConfig.DEBUG of the test variant (true for debug unit tests).
            assertEquals(BuildConfig.DEBUG, state.showDebugTools)
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
                    checkInsEnabled = true,
                    showDebugTools = BuildConfig.DEBUG,
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

    @Test
    @DisplayName("When check-ins disabled then repository stores enabled=false")
    fun checkInsTogglePersists() = runTest {
        subject.onCheckInsEnabledChange(false)
        advanceUntilIdle()

        assertFalse(fakeCheckInPreferences.current.enabled)
        subject.uiState.test {
            assertFalse(awaitItem().checkInsEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("forceCheckInPending enables check-ins and marks pending in debug")
    fun forceCheckInPendingMarksPending() = runTest {
        fakeCheckInPreferences.seed(CheckInPreferences(enabled = false))
        subject = buildViewModel()

        subject.forceCheckInPending()
        advanceUntilIdle()

        if (BuildConfig.DEBUG) {
            assertTrue(fakeCheckInPreferences.current.enabled)
            assertNotNull(fakeCheckInPreferences.current.pendingSinceExtractedAt)
            subject.uiState.test {
                val state = awaitItem()
                assertTrue(state.checkInsEnabled)
                assertNotNull(state.debugStatusMessage)
                cancelAndIgnoreRemainingEvents()
            }
        } else {
            assertFalse(fakeCheckInPreferences.current.enabled)
            assertEquals(null, fakeCheckInPreferences.current.pendingSinceExtractedAt)
        }
    }
}
