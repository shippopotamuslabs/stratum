package com.stratum.core.ui.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GoogleSignInErrorMapperTest {

    @Test
    @DisplayName("NoCredentialException maps to add-account guidance")
    fun noCredential() {
        assertEquals(MSG_NO_ACCOUNT, NoCredentialException().toGoogleSignInMessage())
    }

    @Test
    @DisplayName("Cancellation maps to short cancelled copy")
    fun cancelled() {
        assertEquals(
            MSG_CANCELLED,
            GetCredentialCancellationException().toGoogleSignInMessage(),
        )
    }

    @Test
    @DisplayName("Config-style IllegalArgumentException does not leak raw text")
    fun configMissingNotRaw() {
        val raw =
            "Google Sign-In configuration missing: default_web_client_id not found."
        val mapped = IllegalArgumentException(raw).toGoogleSignInMessage()
        assertEquals(MSG_CONFIG, mapped)
        assertFalse(mapped.contains("default_web_client_id"))
        assertFalse(mapped.contains("IllegalArgument"))
    }

    @Test
    @DisplayName("Network IOException maps to connection copy")
    fun network() {
        assertEquals(MSG_NETWORK, IOException("timeout").toGoogleSignInMessage())
    }

    @Test
    @DisplayName("Unknown technical dump falls back to generic")
    fun technicalFallback() {
        val raw =
            "java.lang.IllegalStateException: boom\n\tat com.stratum.Foo.bar(Foo.kt:1)"
        val mapped = RuntimeException(raw).toGoogleSignInMessage()
        assertEquals(MSG_GENERIC, mapped)
        assertFalse(mapped.contains("IllegalStateException"))
        assertFalse(mapped.contains("com.stratum"))
    }

    @Test
    @DisplayName("Cause chain is walked for typed credential errors")
    fun walksCause() {
        val wrapped =
            RuntimeException(
                "wrapper",
                GetCredentialCancellationException(),
            )
        assertEquals(MSG_CANCELLED, wrapped.toGoogleSignInMessage())
    }

    @Test
    @DisplayName("Classified kinds map to the same copy as their typed exceptions")
    fun classifiedKinds() {
        assertEquals(
            MSG_CANCELLED,
            loginFailureToGoogleSignInMessage(
                com.stratum.application.auth.SignInFailureKind.Cancelled,
                null,
            ),
        )
        assertEquals(
            MSG_NO_ACCOUNT,
            loginFailureToGoogleSignInMessage(
                com.stratum.application.auth.SignInFailureKind.NoAccount,
                null,
            ),
        )
        assertEquals(
            MSG_INTERRUPTED,
            loginFailureToGoogleSignInMessage(
                com.stratum.application.auth.SignInFailureKind.Interrupted,
                null,
            ),
        )
        assertEquals(
            MSG_UNAVAILABLE,
            loginFailureToGoogleSignInMessage(
                com.stratum.application.auth.SignInFailureKind.Unavailable,
                null,
            ),
        )
        assertEquals(
            MSG_NETWORK,
            loginFailureToGoogleSignInMessage(
                com.stratum.application.auth.SignInFailureKind.Network,
                null,
            ),
        )
    }

    @Test
    @DisplayName("Other kind applies message heuristics then generic fallback")
    fun classifiedOther() {
        assertEquals(
            MSG_CONFIG,
            loginFailureToGoogleSignInMessage(
                com.stratum.application.auth.SignInFailureKind.Other,
                "Google Sign-In configuration missing: default_web_client_id not found.",
            ),
        )
        assertEquals(
            MSG_GENERIC,
            loginFailureToGoogleSignInMessage(
                com.stratum.application.auth.SignInFailureKind.Other,
                null,
            ),
        )
    }
}
