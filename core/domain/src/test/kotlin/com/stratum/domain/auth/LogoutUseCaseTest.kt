package com.stratum.domain.auth

import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.domain.sync.CloudSessionState
import com.stratum.domain.sync.EndSessionAndWipeUseCase
import com.stratum.domain.sync.StartCloudSyncUseCase
import com.stratum.domain.sync.StopCloudSyncUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LogoutUseCaseTest {

    @Test
    @DisplayName("Given authenticated user when logout then wipe then sign out")
    fun logoutWipesAndSignsOut() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, port)

        val result = subject()

        assertTrue(result.isSuccess)
        assertEquals(listOf("wipe"), port.callOrder)
        assertEquals("uid-1", port.lastWipedUserId)
        assertEquals(1, port.wipeCount)
        assertEquals(1, auth.signOutCount)
        assertEquals(AuthState.Unauthenticated, auth.authState.value)
        assertEquals(CloudSessionState.Inactive, port.sessionState.value)
    }

    @Test
    @DisplayName("Given unauthenticated when logout then stop and sign out without wipe")
    fun logoutUnauthenticatedStopsOnly() = runTest {
        val auth = FakeAuthManager(AuthState.Unauthenticated)
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, port)

        val result = subject()

        assertTrue(result.isSuccess)
        assertEquals(listOf("stop"), port.callOrder)
        assertEquals(0, port.wipeCount)
        assertEquals(1, auth.signOutCount)
    }

    @Test
    @DisplayName("Given wipe fails when logout then sign out is not called and sync restarts")
    fun wipeFailureRestartsSync() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val port = FakeCloudSyncPort().apply {
            wipeResult = Result.failure(IllegalStateException("wipe failed"))
        }
        val subject = buildSubject(auth, port)

        val result = subject()

        assertTrue(result.isFailure)
        assertEquals(listOf("wipe", "start"), port.callOrder)
        assertEquals(0, auth.signOutCount)
        assertTrue(auth.authState.value is AuthState.Authenticated)
        assertTrue(port.sessionState.value is CloudSessionState.Ready)
    }

    @Test
    @DisplayName("Given sign-out fails after wipe when logout then sync restarts")
    fun signOutFailureRestartsSync() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user)).apply {
            signOutResult = Result.failure(IllegalStateException("sign-out failed"))
        }
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, port)

        val result = subject()

        assertTrue(result.isFailure)
        assertEquals(listOf("wipe", "start"), port.callOrder)
        assertEquals(1, auth.signOutCount)
        assertTrue(auth.authState.value is AuthState.Authenticated)
        assertTrue(port.sessionState.value is CloudSessionState.Ready)
    }

    private fun buildSubject(
        auth: FakeAuthManager,
        port: FakeCloudSyncPort,
    ): LogoutUseCase =
        LogoutUseCase(
            authManager = auth,
            endSessionAndWipe = EndSessionAndWipeUseCase(port),
            stopCloudSync = StopCloudSyncUseCase(port),
            startCloudSync = StartCloudSyncUseCase(port),
        )
}
