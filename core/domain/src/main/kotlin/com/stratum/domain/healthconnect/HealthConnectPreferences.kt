package com.stratum.domain.healthconnect

/** Shared log tag for Health Connect preference-write drain boundaries. */
const val HEALTH_CONNECT_PREFS_TAG = "HealthConnectPrefs"

/**
 * User preferences for Health Connect synchronization.
 *
 * [pendingSyncError] is sticky until consumed by UI so snackbars still show when the worker
 * finishes before a collector is alive.
 */
data class HealthConnectPreferences(
    val enabled: Boolean = false,
    val pendingSyncError: String? = null,
)
