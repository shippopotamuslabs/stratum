@file:Suppress("ForbiddenMockKInTests") // FirebaseAuth / CredentialManager are concrete vendor collaborators — no fakes available.

package com.stratum.core.infrastructure.auth

import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.stratum.application.auth.LoginWithGoogleResult
import com.stratum.application.auth.SignOutOutcome
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.SignInFailureKind
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * E2 consistency at the REAL adapter (A2): the [AuthState.Error] emission and
 * the returned [LoginWithGoogleResult.Failed] are populated from the same
 * single locals at [FirebaseGoogleAuthManager.recordSignInFailure], so they
 * must carry identical kind and message. (The fake's consistency is pinned
 * elsewhere; this pins the production population site.)
 *
 * STR-238 (A18/A19): the adapter holds no LoggingPort at all, so it records
 * zero errors by construction; the failure rides the outcome branch as a
 * §10 diagnostic carrying the real cause, and the boundary drain (pinned in
 * MainViewModelTest / SettingsViewModelTest) is the single record.
 */
class FirebaseGoogleAuthManagerTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun manager(
        googleServerClientId: String = "test-client-id",
    ): FirebaseGoogleAuthManager =
        FirebaseGoogleAuthManager(
            firebaseAuth = mockk<FirebaseAuth>(relaxed = true),
            credentialManager = mockk<CredentialManager>(relaxed = true),
            signInHostContext = SignInHostContext(),
            externalScope = CoroutineScope(Dispatchers.Unconfined),
            googleServerClientId = googleServerClientId,
        )

    @Test
    @DisplayName("Classified failure: emitted Error and returned Failed carry identical kind and message")
    fun classifiedFailureCarriersAgree() {
        val subject = manager()
        val error = IOException("timeout")

        val failed = subject.recordSignInFailure(error)

        assertEquals(SignInFailureKind.Network, failed.kind)
        assertEquals("timeout", failed.message)
        val state = subject.authState.value
        assertEquals(AuthState.Error(SignInFailureKind.Network, "timeout"), state)
        assertEquals(failed.kind, (state as AuthState.Error).kind)
        assertEquals(failed.message, state.message)
        // STR-238 pin (moved from the adapter's former log assertion): the
        // carrier holds the real cause exactly once for the boundary drain.
        assertEquals(1, failed.diagnostics.size)
        assertSame(error, failed.diagnostics.single().cause)
        assertEquals("StratumGoogleAuth", failed.diagnostics.single().tag)
        assertEquals("Google sign-in failed: timeout", failed.diagnostics.single().message)
    }

    @Test
    @DisplayName("Sign-out failure: Failed carries the real cause as a diagnostic and nothing is recorded")
    fun signOutFailureCarriesCause() = runTest {
        val auth = mockk<FirebaseAuth>(relaxed = true)
        val error = IllegalStateException("credential clear failed")
        every { auth.signOut() } throws error
        val subject = FirebaseGoogleAuthManager(
            firebaseAuth = auth,
            credentialManager = mockk<CredentialManager>(relaxed = true),
            signInHostContext = SignInHostContext(),
            externalScope = CoroutineScope(Dispatchers.Unconfined),
            googleServerClientId = "test-client-id",
        )

        val outcome = subject.signOut()

        assertTrue(outcome is SignOutOutcome.Failed)
        val diagnostics = (outcome as SignOutOutcome.Failed).diagnostics
        assertEquals(1, diagnostics.size)
        assertSame(error, diagnostics.single().cause)
        assertEquals("StratumGoogleAuth", diagnostics.single().tag)
        assertEquals(
            "Google sign-out failed: credential clear failed",
            diagnostics.single().message,
        )
    }

    @Test
    @DisplayName("Null-message failure: kind agrees, Failed keeps null, Error carries the empty string")
    fun nullMessageFailureCarriersAgree() {
        val subject = manager()
        val error = IllegalStateException(null as String?)

        val failed = subject.recordSignInFailure(error)

        assertEquals(SignInFailureKind.Other, failed.kind)
        assertNull(failed.message)
        val state = subject.authState.value
        assertEquals(AuthState.Error(SignInFailureKind.Other, ""), state)
        assertEquals(failed.kind, (state as AuthState.Error).kind)
    }

    @Test
    @DisplayName("signInWithGoogle failure path populates both carriers from the same classification")
    fun signInWithGoogleFailurePathCarriersAgree() = runTest {
        // A blank server client id fails inside the attempt before any
        // Credential Manager call, exercising the real public entry point.
        val subject = manager(googleServerClientId = "")

        val result = subject.signInWithGoogle()

        val failed = result as LoginWithGoogleResult.Failed
        assertEquals(SignInFailureKind.Other, failed.kind)
        assertEquals(
            "Google Sign-In configuration missing: default_web_client_id is blank.",
            failed.message,
        )
        val state = subject.authState.value as AuthState.Error
        assertEquals(failed.kind, state.kind)
        assertEquals(failed.message, state.message)
    }
}
