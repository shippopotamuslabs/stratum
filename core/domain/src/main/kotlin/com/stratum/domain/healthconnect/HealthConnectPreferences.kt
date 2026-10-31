package com.stratum.domain.healthconnect

/**
 * User preferences for Health Connect synchronization.
 *
 * [pendingSyncError] is the agnostic kind of the sticky sync failure, kept until
 * consumed by UI so snackbars still show when the worker finishes before a
 * collector is alive. The feature boundary translates it to user-visible text.
 */
data class HealthConnectPreferences(
    val enabled: Boolean = false,
    val pendingSyncError: HealthFailureKind? = null,
)
