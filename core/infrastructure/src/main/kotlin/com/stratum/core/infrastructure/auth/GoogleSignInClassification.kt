package com.stratum.core.infrastructure.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.stratum.application.auth.GoogleSignInException
import com.stratum.application.auth.SignInFailureKind
import java.io.IOException

/**
 * Maps a vendor sign-in failure to the application-owned classification at
 * the adapter boundary (STR-243): typed Credential Manager / IO failures are
 * discriminated over the whole cause chain, mirroring the typed branch of
 * the presentation mapper in `:core:ui`; anything else is
 * [SignInFailureKind.Other] and presentation applies its message heuristics.
 * The originating throwable is preserved as the exception's cause.
 */
internal fun Throwable.toGoogleSignInException(): GoogleSignInException =
    GoogleSignInException(
        kind = signInFailureKind(),
        message = message,
        cause = this,
    )

internal fun Throwable.signInFailureKind(): SignInFailureKind {
    val chain = generateSequence(this) { it.cause }.toList()
    for (error in chain) {
        val kind = when (error) {
            is GetCredentialCancellationException -> SignInFailureKind.Cancelled
            is NoCredentialException -> SignInFailureKind.NoAccount
            is GetCredentialInterruptedException -> SignInFailureKind.Interrupted
            is GetCredentialProviderConfigurationException,
            is GetCredentialUnsupportedException,
            -> SignInFailureKind.Unavailable
            is IOException -> SignInFailureKind.Network
            else -> null
        }
        if (kind != null) return kind
    }
    return SignInFailureKind.Other
}
