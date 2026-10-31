package com.stratum.core.ui.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import java.io.IOException

/**
 * Maps Google / Credential Manager failures to short, user-facing copy.
 *
 * Never returns exception class names, stack-ish text, or developer config jargon.
 * Technical detail stays in logs ([com.stratum.core.infrastructure.auth.FirebaseGoogleAuthManager]).
 */
fun Throwable.toGoogleSignInMessage(): String {
    val chain = generateSequence(this) { it.cause }.toList()
    // Prefer typed Credential Manager / IO failures over free-form messages on wrappers.
    chain.forEach { typedMessage(it)?.let { msg -> return msg } }
    chain.forEach { heuristicMessage(it)?.let { msg -> return msg } }
    return MSG_GENERIC
}

private fun typedMessage(error: Throwable): String? =
    when (error) {
        is GetCredentialCancellationException -> MSG_CANCELLED
        is NoCredentialException -> MSG_NO_ACCOUNT
        is GetCredentialInterruptedException -> MSG_INTERRUPTED
        is GetCredentialProviderConfigurationException,
        is GetCredentialUnsupportedException,
        -> MSG_UNAVAILABLE
        is IOException -> MSG_NETWORK
        else -> null
    }

private fun heuristicMessage(error: Throwable): String? {
    val msg = error.message.orEmpty()
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
