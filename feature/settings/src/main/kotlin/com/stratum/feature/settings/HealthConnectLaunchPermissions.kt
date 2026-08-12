package com.stratum.feature.settings

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord

/**
 * Permission strings launched from Settings. Keep in sync with
 * `HealthConnectRequestedPermissions` in `:core:data`.
 */
object HealthConnectLaunchPermissions {
    val writeExercise: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val readSteps: String = HealthPermission.getReadPermission(StepsRecord::class)
    val readSleep: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val readWeight: String = HealthPermission.getReadPermission(WeightRecord::class)
    val readRestingHeartRate: String =
        HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    val readHrv: String =
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)

    val all: Set<String> = setOf(
        writeExercise,
        readSteps,
        readSleep,
        readWeight,
        readRestingHeartRate,
        readHrv,
    )
}

/** Opt in when the sheet grants write or any requested read. */
internal fun healthConnectOptInFromGrants(
    granted: Set<String>,
    requested: Set<String>,
): Boolean = granted.any { it in requested }
