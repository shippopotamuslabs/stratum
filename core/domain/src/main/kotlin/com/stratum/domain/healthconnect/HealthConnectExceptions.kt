package com.stratum.domain.healthconnect

/** Health Connect SDK is missing or not usable on this device — not worth retrying. */
class HealthConnectUnavailableException(
    message: String = "Health Connect SDK is not available on this device",
) : IllegalStateException(message)

/** Write permission missing or revoked — not worth retrying until the user re-grants. */
class HealthConnectPermissionDeniedException(
    message: String = "Health Connect write permission is not granted",
) : IllegalStateException(message)

/** Returns true when [error] should not be retried by WorkManager. */
fun isTerminalHealthConnectFailure(error: Throwable): Boolean =
    error is HealthConnectUnavailableException ||
        error is HealthConnectPermissionDeniedException
