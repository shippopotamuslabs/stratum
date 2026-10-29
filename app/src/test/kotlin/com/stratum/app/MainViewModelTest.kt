package com.stratum.app

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.core.testing.theme.FakeThemePreferencesRepository
import com.stratum.domain.auth.AuthState
import com.stratum.application.auth.GetAuthStateUseCase
import com.stratum.application.auth.LoginWithGoogleUseCase
import com.stratum.application.auth.LogoutUseCase
import com.stratum.domain.auth.UserProfile
import com.stratum.domain.sync.CloudSessionState
import com.stratum.application.sync.EndSessionAndWipeUseCase
import com.stratum.application.sync.ObserveCloudSessionStateUseCase
import com.stratum.application.sync.StartCloudSyncUseCase
import com.stratum.application.sync.StopCloudSyncUseCase
import com.stratum.application.theme.ObserveThemePreferencesUseCase
import com.stratum.domain.theme.ThemePreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class MainViewModelTest {

    private val fakeAuth = FakeAuthManager(AuthState.Unauthenticated)
    private val fakeCloudSync = FakeCloudSyncPort()
    private val fakeTheme = FakeThemePreferencesRepository()
    private lateinit var subject: MainViewModel

    @BeforeEach
    fun setup() {
        fakeTheme.seed(ThemePreferences())
        fakeCloudSync.setSessionState(CloudSessionState.Inactive)
        subject = buildViewModel()
    }

    private fun buildViewModel(): MainViewModel =
        MainViewModel(
            getAuthState = GetAuthStateUseCase(fakeAuth),
            loginWithGoogle = LoginWithGoogleUseCase(fakeAuth),
            logout = LogoutUseCase(
                authManager = fakeAuth,
                endSessionAndWipe = EndSessionAndWipeUseCase(fakeCloudSync),
                stopCloudSync = StopCloudSyncUseCase(fakeCloudSync),
                startCloudSync = StartCloudSyncUseCase(fakeCloudSync),
            ),
            startCloudSync = StartCloudSyncUseCase(fakeCloudSync),
            observeSessionState = ObserveCloudSessionStateUseCase(fakeCloudSync),
            observeThemePreferences = ObserveThemePreferencesUseCase(fakeTheme),
        )

    @Test
    @DisplayName("Given unauthenticated then shell is SignedOut")
    fun unauthenticatedSignedOut() = runTest {
        advanceUntilIdle()
        subject.shellUiState.test {
            assertEquals(ShellUiState.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given authenticated but session not ready then shell is PreparingSession")
    fun authenticatedPreparing() = runTest {
        fakeAuth.setState(
            AuthState.Authenticated(UserProfile("uid-1", "A", "a@x.com", null)),
        )
        fakeCloudSync.setSessionState(CloudSessionState.Establishing("uid-1"))
        subject = buildViewModel()
        advanceUntilIdle()

        subject.shellUiState.test {
            assertEquals(ShellUiState.PreparingSession, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given authenticated and session ready then shell is Ready")
    fun authenticatedReady() = runTest {
        fakeAuth.setState(
            AuthState.Authenticated(UserProfile("uid-1", "A", "a@x.com", null)),
        )
        fakeCloudSync.setSessionState(CloudSessionState.Ready("uid-1"))
        subject = buildViewModel()
        advanceUntilIdle()

        subject.shellUiState.test {
            assertEquals(ShellUiState.Ready, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given first-entry session failed then shell is SessionFailed")
    fun sessionFailedBeforeEntry() = runTest {
        fakeAuth.setState(
            AuthState.Authenticated(UserProfile("uid-1", "A", "a@x.com", null)),
        )
        fakeCloudSync.setSessionState(
            CloudSessionState.Failed("uid-1", "network down"),
        )
        subject = buildViewModel()
        advanceUntilIdle()

        subject.shellUiState.test {
            val state = awaitItem()
            assertTrue(state is ShellUiState.SessionFailed)
            assertEquals("network down", (state as ShellUiState.SessionFailed).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given Ready once when session goes Inactive then shell stays Ready (logout sticky)")
    fun stickyReadyDuringLogoutTeardown() = runTest {
        fakeAuth.setState(
            AuthState.Authenticated(UserProfile("uid-1", "A", "a@x.com", null)),
        )
        fakeCloudSync.setSessionState(CloudSessionState.Ready("uid-1"))
        subject = buildViewModel()
        advanceUntilIdle()

        subject.shellUiState.test {
            assertEquals(ShellUiState.Ready, awaitItem())

            // Simulate endSessionAndWipe while still authenticated
            fakeCloudSync.setSessionState(CloudSessionState.Inactive)
            advanceUntilIdle()

            expectNoEvents()
            assertEquals(ShellUiState.Ready, subject.shellUiState.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given Ready when auth becomes Unauthenticated then shell is SignedOut")
    fun signOutClearsSticky() = runTest {
        fakeAuth.setState(
            AuthState.Authenticated(UserProfile("uid-1", "A", "a@x.com", null)),
        )
        fakeCloudSync.setSessionState(CloudSessionState.Ready("uid-1"))
        subject = buildViewModel()
        advanceUntilIdle()

        subject.shellUiState.test {
            assertEquals(ShellUiState.Ready, awaitItem())

            fakeAuth.setState(AuthState.Unauthenticated)
            fakeCloudSync.setSessionState(CloudSessionState.Inactive)
            advanceUntilIdle()

            assertEquals(ShellUiState.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("When retry session then start is invoked for current uid")
    fun retrySessionStartsSync() = runTest {
        fakeAuth.setState(
            AuthState.Authenticated(UserProfile("uid-1", "A", "a@x.com", null)),
        )
        fakeCloudSync.setSessionState(
            CloudSessionState.Failed("uid-1", "hydrate failed"),
        )
        subject = buildViewModel()
        advanceUntilIdle()

        subject.onRetrySession()
        advanceUntilIdle()

        assertEquals(1, fakeCloudSync.startCount)
        assertEquals("uid-1", fakeCloudSync.startedUserId)
        assertEquals(ShellUiState.Ready, subject.shellUiState.value)
    }
}
