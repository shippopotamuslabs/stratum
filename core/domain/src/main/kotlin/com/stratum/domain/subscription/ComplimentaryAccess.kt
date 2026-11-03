package com.stratum.domain.subscription

/**
 * The outcome of reading the complimentary / developer free-access override.
 *
 * Three-valued on purpose (STR-280): a read that failed — denied by App Check,
 * offline, Firestore error — is [Unknown], which is not evidence about access.
 * Only a successful read can produce [Granted] or [NotGranted]; conflating a
 * failed read with [NotGranted] paywalls entitled users on transient errors.
 */
enum class ComplimentaryAccess {
    Granted,
    NotGranted,
    Unknown,
}
