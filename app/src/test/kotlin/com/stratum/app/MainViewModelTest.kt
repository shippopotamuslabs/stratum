package com.stratum.app

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.logging.FakeLoggingPort
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
import org.junit.jupiter.api.Assertions.assertSame
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
    private val loggingPort = FakeLoggingPort()
    private lateinit var subject: MainViewModel

    @BeforeEach
    fun setup() {
        fakeTheme.seed(ThemePreferences())
        fakeCloudSync.setSessionState(CloudSessionState.Inactive)
        loggingPort.errors.clear()
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
            diagnosticDrain = DiagnosticDrain(loggingPort),
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
    @DisplayName("When sign-in fails then the failure is logged exactly once at this boundary")
    fun signInFailureLogsOnce() = runTest {
        val signInErrorCause = IllegalStateException("network down")
        fakeAuth.signInFailure = signInErrorCause
        subject = buildViewModel()
        advanceUntilIdle()

        subject.onSignInWithGoogle()
        advanceUntilIdle()

        // STR-238 (A18): the adapter records nothing; the Failed branch's
        // §10 diagnostics drain here exactly once, with the real cause and
        // the adapter's legacy tag/message shape.
        assertEquals(1, loggingPort.errors.size)
        assertEquals("StratumGoogleAuth", loggingPort.errors.single().tag)
        assertEquals(
            "Google sign-in failed: network down",
            loggingPort.errors.single().message,
        )
        assertSame(signInErrorCause, loggingPort.errors.single().throwable)
    }

    @Test
    @DisplayName("When shell sign-out fails then the sign-out failure is logged exactly once")
    fun shellSignOutFailureLogsOnce() = runTest {
        val signOutError = IllegalStateException("sign-out failed")
        fakeAuth.signOutFailure = signOutError
        subject = buildViewModel()
        advanceUntilIdle()

        subject.onSignOutFromShell()
        advanceUntilIdle()

        // STR-238 (A19): the sign-out failure's diagnostics drain here
        // exactly once; the (unattempted, uid-less) restart adds none.
        assertEquals(1, loggingPort.errors.size)
        assertEquals("StratumGoogleAuth", loggingPort.errors.single().tag)
        assertEquals(
            "Google sign-out failed: sign-out failed",
            loggingPort.errors.single().message,
        )
        assertSame(signOutError, loggingPort.errors.single().throwable)
    }

    @Test
    @DisplayName("When sign-out and restart fail then wipe and restart are each logged once and shell stays generic")
    fun signOutAndRestartFailureLogsWipeAndRestartOnceEach() = runTest {
        fakeAuth.setState(
            AuthState.Authenticated(UserProfile("uid-1", "A", "a@x.com", null)),
        )
        val wipeError = IllegalStateException("wipe failed")
        fakeCloudSync.wipeFailure = wipeError
        val restartError = IllegalStateException("restart failed")
        fakeCloudSync.startFailure = restartError
        subject = buildViewModel()
        advanceUntilIdle()

        subject.onSignOutFromShell()
        advanceUntilIdle()

        // STR-238: the primary wipe failure now drains at this boundary too —
        // exactly one wipe record (adapter shape, real cause) in addition to
        // the STR-256 restart record's exactly one.
        assertEquals(2, loggingPort.errors.size)
        val wipeRecord = loggingPort.errors.single { it.throwable == wipeError }
        assertEquals("FirestoreCloudSync", wipeRecord.tag)
        assertEquals("endSessionAndWipe failed: wipe failed", wipeRecord.message)
        assertSame(wipeError, wipeRecord.throwable)
        val restartRecords = loggingPort.errors.filter { it.throwable == restartError }
        assertEquals(1, restartRecords.size)
        // Thermos round 2: the record ORDER is pinned — the primary (wipe)
        // record first, the compensating restart record second (the
        // recording fake preserves append order).
        assertEquals(
            listOf(wipeError, restartError),
            loggingPort.errors.map { it.throwable },
        )
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

    @Test
    @DisplayName("When retry session start fails then the failed pass is logged exactly once")
    fun retrySessionStartFailureLogsOnce() = runTest {
        fakeAuth.setState(
            AuthState.Authenticated(UserProfile("uid-1", "A", "a@x.com", null)),
        )
        fakeCloudSync.setSessionState(
            CloudSessionState.Failed("uid-1", "hydrate failed"),
        )
        val startError = IllegalStateException("start boom")
        fakeCloudSync.startFailure = startError
        subject = buildViewModel()
        advanceUntilIdle()

        subject.onRetrySession()
        advanceUntilIdle()

        // STR-238: the invoking boundary records the failed start pass
        // exactly once, in the sync adapter's tag/message shape.
        assertEquals(1, loggingPort.errors.size)
        assertEquals("FirestoreCloudSync", loggingPort.errors.single().tag)
        assertEquals("start failed: start boom", loggingPort.errors.single().message)
        assertSame(startError, loggingPort.errors.single().throwable)
    }
}
