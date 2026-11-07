@file:Suppress("ForbiddenMockKInTests") // Firebase Auth/Functions SDK boundary — no fake available.

package com.stratum.core.infrastructure.inference

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.stratum.application.auth.AccountDeletionOutcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * STR-238 (A11): a failed deletion phase returns [AccountDeletionOutcome.Failed]
 * carrying the real cause as a §10 diagnostic. The adapter holds no
 * LoggingPort, so it records zero errors by construction; the single record
 * is the Settings boundary drain (pinned in SettingsViewModelTest).
 */
class FirebaseAccountDeletionAdapterTest {
    private val auth: FirebaseAuth = mockk()
    private val functions: FirebaseFunctions = mockk()
    private lateinit var adapter: FirebaseAccountDeletionAdapter

    @BeforeEach
    fun setup() {
        adapter = FirebaseAccountDeletionAdapter(
            functions = functions,
            auth = auth,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("callable name and timeout match the server endpoint")
    fun callableContract() {
        assertEquals("deleteAccount", FirebaseAccountDeletionAdapter.CALLABLE_NAME)
        assertEquals(120L, FirebaseAccountDeletionAdapter.CALLABLE_TIMEOUT_SECONDS)
    }

    @Test
    @DisplayName("signed-out data phase fails without touching the network and carries the cause")
    fun signedOutFailsWithoutNetwork() = runTest {
        every { auth.currentUser } returns null

        val outcome = adapter.deleteRemoteUserData()

        assertTrue(outcome is AccountDeletionOutcome.Failed)
        val diagnostics = (outcome as AccountDeletionOutcome.Failed).diagnostics
        assertEquals(1, diagnostics.size)
        val diagnostic = diagnostics.single()
        assertTrue(diagnostic.cause is IllegalStateException)
        assertEquals("Sign-in required to delete account.", diagnostic.cause.message)
        assertEquals(FirebaseAccountDeletionAdapter.TAG, diagnostic.tag)
        assertEquals(
            "deleteAccount(data) failed: Sign-in required to delete account.",
            diagnostic.message,
        )
        verify(exactly = 0) { functions.getHttpsCallable(any()) }
    }

    @Test
    @DisplayName("callable error on the auth phase maps to Failed carrying the same cause")
    fun callableErrorCarriesCause() = runTest {
        val user: FirebaseUser = mockk()
        every { auth.currentUser } returns user
        val callable: HttpsCallableReference = mockk()
        every {
            functions.getHttpsCallable(FirebaseAccountDeletionAdapter.CALLABLE_NAME)
        } returns callable
        every { callable.withTimeout(any(), any()) } returns callable
        val callableError = RuntimeException("auth delete boom")
        every { callable.call(any()) } returns Tasks.forException(callableError)

        val outcome = adapter.deleteRemoteAuthUser()

        assertTrue(outcome is AccountDeletionOutcome.Failed)
        val diagnostics = (outcome as AccountDeletionOutcome.Failed).diagnostics
        assertEquals(1, diagnostics.size)
        assertSame(callableError, diagnostics.single().cause)
        assertEquals(FirebaseAccountDeletionAdapter.TAG, diagnostics.single().tag)
        assertEquals(
            "deleteAccount(auth) failed: auth delete boom",
            diagnostics.single().message,
        )
    }
}
