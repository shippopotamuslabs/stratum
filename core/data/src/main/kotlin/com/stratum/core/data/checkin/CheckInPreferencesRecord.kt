package com.stratum.core.data.checkin

import kotlinx.serialization.Serializable

/**
 * Typed DataStore root for [com.stratum.domain.checkin.CheckInPreferences].
 * Epoch millis are nullable so absent fields recover to null Instant.
 */
@Serializable
data class CheckInPreferencesRecord(
    val enabled: Boolean = true,
    val pendingSinceExtractedAtEpochMillis: Long? = null,
    val lastCompletedAtEpochMillis: Long? = null,
    val lastSkippedAtEpochMillis: Long? = null,
)
