package com.stratum.domain.auth

/**
 * Agnostic classification of a Google sign-in failure (STR-243, E2 ruling:
 * single-sourced here in the domain, alongside [AuthState]).
 *
 * The infrastructure adapter is the single classification source: it
 * translates vendor SDK exceptions (Credential Manager, Firebase Auth, IO)
 * into this kind at its boundary, so vendor types never cross it. Every
 * consumer — the sign-in outcome branch and [AuthState.Error] — carries this
 * kind, and presentation maps each kind to the shipped sign-in copy.
 * [Other] carries the originating message so presentation can still apply
 * its message heuristics for unclassified failures.
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
