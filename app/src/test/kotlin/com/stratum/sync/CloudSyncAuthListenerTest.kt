package com.stratum.sync

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.domain.auth.AuthState
import com.stratum.application.auth.GetAuthStateUseCase
import com.stratum.domain.auth.UserProfile
import com.stratum.application.sync.StartCloudSyncUseCase
import com.stratum.application.sync.StopCloudSyncUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class CloudSyncAuthListenerTest {
    private val authManager = FakeAuthManager()
    private val cloudSyncPort = FakeCloudSyncPort()

    private fun createListener(
        scope: CoroutineScope,
        loggingPort: FakeLoggingPort = FakeLoggingPort(),
    ) =
        CloudSyncAuthListener(
            getAuthState = GetAuthStateUseCase(authManager),
            startCloudSync = StartCloudSyncUseCase(cloudSyncPort),
            stopCloudSync = StopCloudSyncUseCase(cloudSyncPort),
            loggingPort = loggingPort,
            scope = scope,
        )

    @Test
    fun `authenticated state starts cloud sync with user ID`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()

            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))

            assertEquals("user-abc", cloudSyncPort.startedUserId)
            scope.cancel()
        }

    @Test
    fun `unauthenticated state stops cloud sync`() =
        runTest {
            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            assertEquals("user-abc", cloudSyncPort.startedUserId)

            authManager.setState(AuthState.Unauthenticated)

            assertEquals(1, cloudSyncPort.stopCount)
            scope.cancel()
        }

    @Test
    fun `error auth state stops cloud sync`() =
        runTest {
            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            assertEquals("user-abc", cloudSyncPort.startedUserId)

            authManager.setState(AuthState.Error(com.stratum.domain.auth.SignInFailureKind.Other, "Auth failed"))

            assertEquals(1, cloudSyncPort.stopCount)
            scope.cancel()
        }

    @Test
    fun `repeated start calls do not launch duplicate collectors`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            listener.start()

            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))

            assertEquals("user-abc", cloudSyncPort.startedUserId)
            scope.cancel()
        }

    @Test
    fun `startCloudSync failure is drained exactly once by the listener and does not kill it`() =
        runTest {
            val logging = FakeLoggingPort()
            val startError = IllegalStateException("sync start boom")
            cloudSyncPort.startFailure = startError
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope, logging)
            listener.start()

            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))

            // STR-238: the listener is the single recorder for its own start
            // invocations — the failed start's diagnostics drain here exactly
            // once, in the sync adapter's tag/message shape for a start
            // failure, with the real cause attached.
            assertEquals(1, logging.errors.size)
            assertEquals("FirestoreCloudSync", logging.errors.single().tag)
            assertEquals("start failed: sync start boom", logging.errors.single().message)
            assertSame(startError, logging.errors.single().throwable)
            val stopsBeforeSignOut = cloudSyncPort.stopCount
            authManager.setState(AuthState.Unauthenticated)
            assertEquals(stopsBeforeSignOut + 1, cloudSyncPort.stopCount)
            scope.cancel()
        }
}

