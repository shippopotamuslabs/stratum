package com.stratum.domain.healthconnect

/**
 * User-meaning reason a Health Connect operation failed, without vendor detail.
 *
 * Product language, not provider language: neither failure consumer (the log drain,
 * the sticky-sync snackbar) needs retry policy, so the variants describe what the
 * failure means for the user rather than a terminal/transient policy bucket.
 */
enum class HealthFailureKind {
    /** The Health Connect SDK is not available on this device. */
    Unavailable,

    /** The user has not granted the Health Connect permission the operation needs. */
    PermissionDenied,

    /** The failure was not classified (e.g. an unclassified read-snapshot error). */
    Unknown,
}
