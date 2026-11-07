package com.stratum.core.infrastructure.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.stratum.domain.auth.SignInFailureKind
import java.io.IOException

/**
 * The single classification source for Google sign-in failures (STR-243, E2
 * ruling): maps a vendor sign-in failure to the domain-owned
 * [SignInFailureKind] at the adapter boundary. Typed Credential Manager / IO
 * failures are discriminated over the whole cause chain; anything else is
 * [SignInFailureKind.Other] and presentation applies its message heuristics
 * to the carried message.
 */
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
