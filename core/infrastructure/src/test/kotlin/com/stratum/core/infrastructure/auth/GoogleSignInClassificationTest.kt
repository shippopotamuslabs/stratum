package com.stratum.core.infrastructure.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.stratum.application.auth.SignInFailureKind
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GoogleSignInClassificationTest {

    @Test
    @DisplayName("Typed credential exceptions classify to their kinds")
    fun typedKinds() {
        assertEquals(
            SignInFailureKind.Cancelled,
            GetCredentialCancellationException().signInFailureKind(),
        )
        assertEquals(
            SignInFailureKind.NoAccount,
            NoCredentialException().signInFailureKind(),
        )
        assertEquals(
            SignInFailureKind.Interrupted,
            GetCredentialInterruptedException().signInFailureKind(),
        )
        assertEquals(
            SignInFailureKind.Unavailable,
            GetCredentialProviderConfigurationException().signInFailureKind(),
        )
        assertEquals(
            SignInFailureKind.Unavailable,
            GetCredentialUnsupportedException().signInFailureKind(),
        )
        assertEquals(
            SignInFailureKind.Network,
            IOException("timeout").signInFailureKind(),
        )
    }

    @Test
    @DisplayName("Cause chain is walked and origin is preserved")
    fun walksCauseAndPreservesOrigin() {
        val origin = RuntimeException("wrapper", NoCredentialException())
        val classified = origin.toGoogleSignInException()
        assertEquals(SignInFailureKind.NoAccount, classified.kind)
        assertSame(origin, classified.cause)
        assertEquals("wrapper", classified.message)
    }

    @Test
    @DisplayName("Unclassified failure is Other")
    fun unclassifiedIsOther() {
        assertEquals(
            SignInFailureKind.Other,
            IllegalStateException("Firebase sign in failed").signInFailureKind(),
        )
    }
}
