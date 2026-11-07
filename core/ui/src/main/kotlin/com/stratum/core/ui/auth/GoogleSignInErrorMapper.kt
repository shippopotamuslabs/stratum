package com.stratum.core.ui.auth

import com.stratum.domain.auth.SignInFailureKind

/**
 * Maps Google sign-in failures to short, user-facing copy.
 *
 * The single classification source is the infrastructure adapter's
 * vendor-type -> [SignInFailureKind] mapping (STR-243, E2 ruling); this
 * mapper only turns a kind (plus the originating message for
 * [SignInFailureKind.Other]) into copy. Never returns exception class names,
 * stack-ish text, or developer config jargon. Technical detail stays in logs
 * ([com.stratum.core.infrastructure.auth.FirebaseGoogleAuthManager]).
 */
fun loginFailureToGoogleSignInMessage(
    kind: SignInFailureKind,
    message: String?,
): String =
    when (kind) {
        SignInFailureKind.Cancelled -> MSG_CANCELLED
        SignInFailureKind.NoAccount -> MSG_NO_ACCOUNT
        SignInFailureKind.Interrupted -> MSG_INTERRUPTED
        SignInFailureKind.Unavailable -> MSG_UNAVAILABLE
        SignInFailureKind.Network -> MSG_NETWORK
        SignInFailureKind.Other ->
            heuristicMessageText(message.orEmpty()) ?: MSG_GENERIC
    }

private fun heuristicMessageText(msg: String): String? {
    if (msg.isBlank()) return null

    val lower = msg.lowercase()
    return when {
        lower.contains("cancel") -> MSG_CANCELLED
        lower.contains("default_web_client_id") ||
            (lower.contains("oauth") && lower.contains("client")) ||
            lower.contains("developer_error") ||
            lower.contains("sign-in configuration") ||
            lower.contains("signin configuration")
        -> MSG_CONFIG
        lower.contains("network") ||
            lower.contains("unable to resolve host") ||
            lower.contains("timeout") ||
            lower.contains("connection")
        -> MSG_NETWORK
        lower.contains("no credentials") ||
            lower.contains("no account") ||
            lower.contains("cannot find a matching credential")
        -> MSG_NO_ACCOUNT
        looksTechnical(msg) -> null
        // Short, already human copy from our own code / known libs.
        msg.length <= MAX_FRIENDLY_LENGTH -> msg
        else -> null
    }
}

private fun looksTechnical(message: String): Boolean {
    val lower = message.lowercase()
    return lower.contains("exception") ||
        lower.contains("error:") ||
        lower.contains("illegal") ||
        lower.contains("nullpointer") ||
        lower.contains(" at ") ||
        lower.contains("com.") ||
        lower.contains("android.") ||
        lower.contains("kotlin.") ||
        lower.contains("status code") ||
        lower.contains("api_not_available") ||
        message.lines().size > 1
}

// User-facing copy (English). Keep in sync with app sign-in strings if localized later.
internal const val MSG_GENERIC = "Couldn't sign in with Google. Try again."
internal const val MSG_CANCELLED = "Sign-in cancelled."
internal const val MSG_NO_ACCOUNT =
    "No Google account found on this device. Add one in Settings and try again."
internal const val MSG_INTERRUPTED = "Sign-in was interrupted. Try again."
internal const val MSG_UNAVAILABLE = "Google Sign-In isn't available on this device."
internal const val MSG_NETWORK = "Couldn't reach Google. Check your connection and try again."
internal const val MSG_CONFIG =
    "Sign-in isn't available in this build. Update the app or try again later."

private const val MAX_FRIENDLY_LENGTH = 120
