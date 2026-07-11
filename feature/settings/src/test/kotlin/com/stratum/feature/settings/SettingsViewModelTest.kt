package com.stratum.feature.settings

import com.stratum.core.testing.logging.FakeLoggingPort

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.checkin.FakeCheckInPreferencesRepository
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.core.testing.theme.FakeThemePreferencesRepository
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.LogoutUseCase
import com.stratum.domain.auth.UserProfile
import com.stratum.domain.checkin.CheckInPreferences
import com.stratum.domain.checkin.MarkCheckInPendingUseCase
import com.stratum.domain.checkin.ObserveCheckInPreferencesUseCase
import com.stratum.domain.checkin.SetCheckInEnabledUseCase
import com.stratum.domain.sync.EndSessionAndWipeUseCase
import com.stratum.domain.sync.StartCloudSyncUseCase
import com.stratum.domain.sync.StopCloudSyncUseCase
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
import org.junit.jupiter.api.Assertions.assertNull
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
    private lateinit var fakeAuth: FakeAuthManager
    private lateinit var fakeCloudSync: FakeCloudSyncPort
    private lateinit var subject: SettingsViewModel

    @BeforeEach
    fun setup() {
        fakeThemePreferences.seed(ThemePreferences())
        fakeCheckInPreferences.seed(CheckInPreferences())
        fakeAuth = FakeAuthManager(
            AuthState.Authenticated(UserProfile("uid-1", "Test", "t@example.com", null)),
        )
        fakeCloudSync = FakeCloudSyncPort()
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
            logout = LogoutUseCase(
                authManager = fakeAuth,
                endSessionAndWipe = EndSessionAndWipeUseCase(fakeCloudSync),
                stopCloudSync = StopCloudSyncUseCase(fakeCloudSync),
                startCloudSync = StartCloudSyncUseCase(fakeCloudSync),
            ),
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
            assertFalse(state.showLogoutConfirm)
            assertFalse(state.isLoggingOut)
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

    @Test
    @DisplayName("When logout clicked then confirm dialog is shown without wiping yet")
    fun logoutClickShowsConfirm() = runTest {
        subject.onLogoutClick()
        advanceUntilIdle()

        subject.uiState.test {
            assertTrue(awaitItem().showLogoutConfirm)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(emptyList<String>(), fakeCloudSync.callOrder)
        assertEquals(0, fakeAuth.signOutCount)
    }

    @Test
    @DisplayName("When logout confirmed then wipe and sign out run")
    fun logoutConfirmedInvokesUseCase() = runTest {
        subject.onLogoutClick()
        subject.onLogoutConfirmed()
        advanceUntilIdle()

        assertEquals(listOf("wipe"), fakeCloudSync.callOrder)
        assertEquals("uid-1", fakeCloudSync.lastWipedUserId)
        assertEquals(1, fakeAuth.signOutCount)
        assertEquals(AuthState.Unauthenticated, fakeAuth.authState.value)
        assertNull(subject.userMessage.value)
        assertFalse(subject.uiState.value.isLoggingOut)
        assertFalse(subject.uiState.value.showLogoutConfirm)
    }

    @Test
    @DisplayName("When logout fails then userMessage is set and in-flight clears")
    fun logoutFailureSurfacesMessage() = runTest {
        fakeCloudSync.wipeResult = Result.failure(IllegalStateException("wipe failed"))
        subject = buildViewModel()

        subject.onLogoutClick()
        subject.onLogoutConfirmed()
        advanceUntilIdle()

        // Wipe fails → recovery start; sign-out not called
        assertEquals(0, fakeAuth.signOutCount)
        assertEquals("Couldn't sign out. Try again.", subject.userMessage.value)
        assertFalse(subject.uiState.value.isLoggingOut)
    }

    @Test
    @DisplayName("When logout confirm dismissed then dialog closes")
    fun logoutConfirmDismiss() = runTest {
        subject.onLogoutClick()
        advanceUntilIdle()
        subject.onLogoutConfirmDismiss()
        advanceUntilIdle()

        assertFalse(subject.uiState.value.showLogoutConfirm)
        assertEquals(emptyList<String>(), fakeCloudSync.callOrder)
    }
}
