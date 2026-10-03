package com.stratum.domain.telemetry

/**
 * Normalizes the user-visible version for the tier-2 upload envelope.
 *
 * Lives next to [ClientLogBatch], which defines the [ClientLogBatch.APP_VERSION_UNKNOWN]
 * fallback contract. The PackageManager lookup happens at the call site inside
 * runCatching so telemetry metadata can never crash app start.
 */
fun resolveAppVersion(versionName: String?): String =
    versionName?.takeIf { it.isNotBlank() } ?: ClientLogBatch.APP_VERSION_UNKNOWN
