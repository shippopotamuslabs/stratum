package com.stratum.core.data.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import com.stratum.domain.health.HealthMetricType

/**
 * Canonical Health Connect permission strings for dashboard reads + workout write.
 * Keep in sync with `HealthConnectLaunchPermissions` in `:feature:settings`.
 */
object HealthConnectRequestedPermissions {
    val writeExercise: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val readSteps: String = HealthPermission.getReadPermission(StepsRecord::class)
    val readSleep: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val readWeight: String = HealthPermission.getReadPermission(WeightRecord::class)
    val readRestingHeartRate: String =
        HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    val readHrv: String =
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)

    val reads: Map<String, HealthMetricType> = mapOf(
        readSteps to HealthMetricType.Steps,
        readSleep to HealthMetricType.Sleep,
        readWeight to HealthMetricType.Weight,
        readRestingHeartRate to HealthMetricType.RestingHeartRate,
        readHrv to HealthMetricType.HeartRateVariability,
    )

    fun grantedTypes(grantedPermissions: Set<String>): Set<HealthMetricType> =
        reads.mapNotNull { (permission, type) ->
            type.takeIf { permission in grantedPermissions }
        }.toSet()
}
