package com.stratum.application.auth

import app.cash.turbine.test
import com.stratum.application.sync.ObserveCloudSessionStateUseCase
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile
import com.stratum.domain.sync.CloudSessionState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [ObserveShellStateUseCase] composition tests (STR-211): the auth + session
 * combine and the entered-app latch moved out of `MainViewModel` must
 * preserve the VM's prior behavior — sticky Ready across logout teardown,
 * latch reset on sign-out, and uid passthrough for retry.
 */
class ObserveShellStateUseCaseTest {

    private val fakeAuth = FakeAuthManager(AuthState.Unauthenticated)
    private val fakeCloudSync = FakeCloudSyncPort()

    private val subject = ObserveShellStateUseCase(
        GetAuthStateUseCase(fakeAuth),
        ObserveCloudSessionStateUseCase(fakeCloudSync),
    )

    private fun authenticated(uid: String = "uid-1"): AuthState.Authenticated =
        AuthState.Authenticated(UserProfile(uid, "A", "a@x.com", null))

    @Test
    @DisplayName("Given unauthenticated and inactive then snapshot carries no uid and no latch")
    fun unauthenticatedSnapshot() = runTest {
        subject().test {
            val snapshot = awaitItem()
            assertEquals(AuthState.Unauthenticated, snapshot.auth)
            assertEquals(CloudSessionState.Inactive, snapshot.session)
            assertNull(snapshot.authenticatedUserId)
            assertFalse(snapshot.hasEnteredApp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given authenticated and establishing then snapshot carries uid without latch")
    fun establishingSnapshot() = runTest {
        fakeAuth.setState(authenticated())
        fakeCloudSync.setSessionState(CloudSessionState.Establishing("uid-1"))

        subject().test {
            val snapshot = awaitItem()
            assertEquals("uid-1", snapshot.authenticatedUserId)
            assertFalse(snapshot.hasEnteredApp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given authenticated and ready then latch is entered")
    fun readyEntersLatch() = runTest {
        fakeAuth.setState(authenticated())
        fakeCloudSync.setSessionState(CloudSessionState.Ready("uid-1"))

        subject().test {
            val snapshot = awaitItem()
            assertEquals("uid-1", snapshot.authenticatedUserId)
            assertTrue(snapshot.hasEnteredApp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given entered when session goes inactive then latch stays entered")
    fun stickyAcrossLogoutTeardown() = runTest {
        fakeAuth.setState(authenticated())
        fakeCloudSync.setSessionState(CloudSessionState.Ready("uid-1"))

        subject().test {
            assertTrue(awaitItem().hasEnteredApp)

            fakeCloudSync.setSessionState(CloudSessionState.Inactive)

            val snapshot = awaitItem()
            assertEquals(CloudSessionState.Inactive, snapshot.session)
            assertEquals("uid-1", snapshot.authenticatedUserId)
            assertTrue(snapshot.hasEnteredApp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given entered when auth signs out then latch resets and uid clears")
    fun signOutResetsLatch() = runTest {
        fakeAuth.setState(authenticated())
        fakeCloudSync.setSessionState(CloudSessionState.Ready("uid-1"))

        subject().test {
            assertTrue(awaitItem().hasEnteredApp)

            fakeAuth.setState(AuthState.Unauthenticated)
            fakeCloudSync.setSessionState(CloudSessionState.Inactive)

            val snapshot = awaitItem()
            assertNull(snapshot.authenticatedUserId)
            assertFalse(snapshot.hasEnteredApp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given failed session then failure passes through with uid and no latch")
    fun failedSessionPassesThrough() = runTest {
        fakeAuth.setState(authenticated())
        fakeCloudSync.setSessionState(CloudSessionState.Failed("uid-1", "hydrate failed"))

        subject().test {
            val snapshot = awaitItem()
            assertEquals(
                CloudSessionState.Failed("uid-1", "hydrate failed"),
                snapshot.session,
            )
            assertEquals("uid-1", snapshot.authenticatedUserId)
            assertFalse(snapshot.hasEnteredApp)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
