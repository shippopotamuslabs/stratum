package com.stratum.domain.healthconnect

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
