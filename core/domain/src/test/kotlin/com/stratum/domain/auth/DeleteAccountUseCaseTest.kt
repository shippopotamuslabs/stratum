package com.stratum.domain.auth

import com.stratum.core.testing.auth.FakeAccountDeletionPort
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.domain.sync.EndSessionAndWipeUseCase
import com.stratum.domain.sync.StartCloudSyncUseCase
import com.stratum.domain.sync.StopCloudSyncUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DeleteAccountUseCaseTest {

    @Test
    @DisplayName("Given authenticated when delete then stop, data, wipe, auth, sign out")
    fun deleteSucceeds() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val remote = FakeAccountDeletionPort()
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertTrue(result.isSuccess)
        assertEquals(1, remote.deleteDataCount)
        assertEquals(1, remote.deleteAuthCount)
        assertEquals(listOf("stop", "wipe"), port.callOrder)
        assertEquals("uid-1", port.lastWipedUserId)
        assertEquals(1, auth.signOutCount)
        assertEquals(AuthState.Unauthenticated, auth.authState.value)
    }

    @Test
    @DisplayName("Given unauthenticated when delete then failure without side effects")
    fun deleteUnauthenticatedFails() = runTest {
        val auth = FakeAuthManager(AuthState.Unauthenticated)
        val remote = FakeAccountDeletionPort()
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertTrue(result.isFailure)
        assertEquals(0, remote.deleteDataCount)
        assertEquals(0, remote.deleteAuthCount)
        assertEquals(emptyList<String>(), port.callOrder)
        assertEquals(0, auth.signOutCount)
    }

    @Test
    @DisplayName("Given remote data fails when delete then no wipe/auth and sync restarts")
    fun remoteDataFailureLeavesLocalIntactAndRestartsSync() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val remote = FakeAccountDeletionPort().apply {
            deleteDataResult = Result.failure(IllegalStateException("cloud failed"))
        }
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertTrue(result.isFailure)
        assertEquals(1, remote.deleteDataCount)
        assertEquals(0, remote.deleteAuthCount)
        assertEquals(listOf("stop", "start"), port.callOrder)
        assertEquals(0, auth.signOutCount)
        assertTrue(auth.authState.value is AuthState.Authenticated)
    }

    @Test
    @DisplayName("Given wipe fails after remote data when delete then typed local cleanup failure")
    fun wipeFailureAfterRemoteData() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val remote = FakeAccountDeletionPort()
        val port = FakeCloudSyncPort().apply {
            wipeResult = Result.failure(IllegalStateException("wipe failed"))
        }
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertTrue(result.isFailure)
        assertInstanceOf(
            LocalCleanupAfterAccountDeletionException::class.java,
            result.exceptionOrNull(),
        )
        assertEquals(1, remote.deleteDataCount)
        assertEquals(0, remote.deleteAuthCount)
        // Must not restart sync after cloud data is already gone (would re-upload).
        assertEquals(listOf("stop", "wipe"), port.callOrder)
        assertEquals(0, auth.signOutCount)
        assertTrue(auth.authState.value is AuthState.Authenticated)
    }

    @Test
    @DisplayName("Given auth delete fails after wipe when delete then typed auth failure and no sign out")
    fun authDeleteFailureAfterWipe() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val remote = FakeAccountDeletionPort().apply {
            deleteAuthResult = Result.failure(IllegalStateException("auth delete failed"))
        }
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertTrue(result.isFailure)
        assertInstanceOf(
            AuthDeleteAfterAccountWipeException::class.java,
            result.exceptionOrNull(),
        )
        assertEquals(1, remote.deleteDataCount)
        assertEquals(1, remote.deleteAuthCount)
        assertEquals(listOf("stop", "wipe"), port.callOrder)
        assertEquals(0, auth.signOutCount)
    }

    @Test
    @DisplayName("Given signOut fails after auth delete when delete then still success")
    fun signOutFailureAfterAuthDeleteIsBestEffort() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user)).apply {
            signOutResult = Result.failure(IllegalStateException("credential clear failed"))
        }
        val remote = FakeAccountDeletionPort()
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertTrue(result.isSuccess)
        assertEquals(1, remote.deleteAuthCount)
        assertEquals(1, auth.signOutCount)
        assertEquals(listOf("stop", "wipe"), port.callOrder)
    }

    private fun buildSubject(
        auth: FakeAuthManager,
        remote: FakeAccountDeletionPort,
        port: FakeCloudSyncPort,
    ): DeleteAccountUseCase =
        DeleteAccountUseCase(
            authManager = auth,
            accountDeletionPort = remote,
            stopCloudSync = StopCloudSyncUseCase(port),
            startCloudSync = StartCloudSyncUseCase(port),
            endSessionAndWipe = EndSessionAndWipeUseCase(port),
        )
}
