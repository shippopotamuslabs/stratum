package com.stratum.core.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException

fun Throwable.toGoogleSignInMessage(): String =
    when (this) {
        is NoCredentialException ->
            "No Google account found on this device. Add one in Settings and try again."
        is GetCredentialCancellationException -> "Sign-in cancelled."
        else -> message?.takeIf { it.isNotBlank() } ?: "Sign-in failed. Try again."
    }
