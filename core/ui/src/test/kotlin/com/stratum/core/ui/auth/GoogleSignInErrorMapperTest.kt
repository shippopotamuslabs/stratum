package com.stratum.core.ui.auth

import com.stratum.domain.auth.SignInFailureKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GoogleSignInErrorMapperTest {

    @Test
    @DisplayName("Classified kinds map to their shipped copy")
    fun classifiedKinds() {
        assertEquals(
            MSG_CANCELLED,
            loginFailureToGoogleSignInMessage(SignInFailureKind.Cancelled, null),
        )
        assertEquals(
            MSG_NO_ACCOUNT,
            loginFailureToGoogleSignInMessage(SignInFailureKind.NoAccount, null),
        )
        assertEquals(
            MSG_INTERRUPTED,
            loginFailureToGoogleSignInMessage(SignInFailureKind.Interrupted, null),
        )
        assertEquals(
            MSG_UNAVAILABLE,
            loginFailureToGoogleSignInMessage(SignInFailureKind.Unavailable, null),
        )
        assertEquals(
            MSG_NETWORK,
            loginFailureToGoogleSignInMessage(SignInFailureKind.Network, null),
        )
    }

    @Test
    @DisplayName("Other kind applies message heuristics then generic fallback")
    fun classifiedOther() {
        assertEquals(
            MSG_CONFIG,
            loginFailureToGoogleSignInMessage(
                SignInFailureKind.Other,
                "Google Sign-In configuration missing: default_web_client_id not found.",
            ),
        )
        assertEquals(
            MSG_NETWORK,
            loginFailureToGoogleSignInMessage(SignInFailureKind.Other, "timeout"),
        )
        assertEquals(
            MSG_GENERIC,
            loginFailureToGoogleSignInMessage(SignInFailureKind.Other, null),
        )
        // A technical dump in the message never leaks through the heuristics.
        val raw =
            "java.lang.IllegalStateException: boom\n\tat com.stratum.Foo.bar(Foo.kt:1)"
        assertEquals(
            MSG_GENERIC,
            loginFailureToGoogleSignInMessage(SignInFailureKind.Other, raw),
        )
    }
}
