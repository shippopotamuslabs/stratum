package com.stratum.application.auth

/**
 * Agnostic classification of a Google sign-in failure at the application port
 * (STR-243).
 *
 * Placement note (CODE_STYLE_GOSPEL §11): this vocabulary is owned by the
 * application layer, not the domain. The infrastructure adapter translates
 * vendor SDK exceptions (Credential Manager, Firebase Auth, IO) into this
 * classification at the port so vendor types never cross the boundary;
 * presentation maps each kind to the shipped sign-in copy. [Other] carries
 * the originating message so presentation can still apply its message
 * heuristics for unclassified failures.
 */
enum class SignInFailureKind {
    /** The user cancelled the credential flow. */
    Cancelled,

    /** No Google account / credential is available on the device. */
    NoAccount,

    /** The credential flow was interrupted before completing. */
    Interrupted,

    /** Google Sign-In is not available or not configured on this device. */
    Unavailable,

    /** The failure looks network-related. */
    Network,

    /** Unclassified operational failure; see the carried message. */
    Other,
}

/**
 * Application-port failure for Google sign-in. The adapter logs the
 * originating throwable once at its boundary, then throws this classification
 * (origin preserved as [cause], adapter-side only); use cases translate it
 * into the `LoginWithGoogleResult.Failed` branch, so it never crosses a
 * use-case signature as a payload.
 */
class GoogleSignInException(
    val kind: SignInFailureKind,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
