package com.stratum.core.infrastructure.checkin

import com.stratum.domain.checkin.CheckInPreferences
import java.time.Instant

internal fun CheckInPreferencesRecord.toDomain(): CheckInPreferences =
    CheckInPreferences(
        enabled = enabled,
        pendingSinceExtractedAt = pendingSinceExtractedAtEpochMillis?.let(Instant::ofEpochMilli),
        lastCompletedAt = lastCompletedAtEpochMillis?.let(Instant::ofEpochMilli),
        lastSkippedAt = lastSkippedAtEpochMillis?.let(Instant::ofEpochMilli),
    )

internal fun CheckInPreferences.toRecord(): CheckInPreferencesRecord =
    CheckInPreferencesRecord(
        enabled = enabled,
        pendingSinceExtractedAtEpochMillis = pendingSinceExtractedAt?.toEpochMilli(),
        lastCompletedAtEpochMillis = lastCompletedAt?.toEpochMilli(),
        lastSkippedAtEpochMillis = lastSkippedAt?.toEpochMilli(),
    )
