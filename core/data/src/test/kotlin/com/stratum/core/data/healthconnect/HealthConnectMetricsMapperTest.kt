package com.stratum.core.data.healthconnect

import androidx.health.connect.client.records.SleepSessionRecord
import com.stratum.domain.health.SleepStageType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HealthConnectMetricsMapperTest {

    @Nested
    @DisplayName("sleepStageType")
    inner class SleepStageTypeMapping {
        @Test
        fun mapsKnownStages() {
            assertEquals(
                SleepStageType.Awake,
                HealthConnectMetricsMapper.sleepStageType(SleepSessionRecord.STAGE_TYPE_AWAKE),
            )
            assertEquals(
                SleepStageType.Awake,
                HealthConnectMetricsMapper.sleepStageType(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED),
            )
            assertEquals(
                SleepStageType.Light,
                HealthConnectMetricsMapper.sleepStageType(SleepSessionRecord.STAGE_TYPE_LIGHT),
            )
            assertEquals(
                SleepStageType.Deep,
                HealthConnectMetricsMapper.sleepStageType(SleepSessionRecord.STAGE_TYPE_DEEP),
            )
            assertEquals(
                SleepStageType.Rem,
                HealthConnectMetricsMapper.sleepStageType(SleepSessionRecord.STAGE_TYPE_REM),
            )
            assertEquals(
                SleepStageType.Awake,
                HealthConnectMetricsMapper.sleepStageType(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
            )
            assertNull(
                HealthConnectMetricsMapper.sleepStageType(SleepSessionRecord.STAGE_TYPE_SLEEPING),
            )
            assertNull(
                HealthConnectMetricsMapper.sleepStageType(SleepSessionRecord.STAGE_TYPE_UNKNOWN),
            )
            assertNull(HealthConnectMetricsMapper.sleepStageType(99))
        }
    }

    @Nested
    @DisplayName("weightSample")
    inner class WeightSampleMapping {
        @Test
        fun storesKilograms() {
            val time = java.time.Instant.parse("2026-08-12T12:00:00Z")
            val sample = HealthConnectMetricsMapper.weightSample(time, 81.4)
            assertEquals(time, sample.time)
            assertEquals(81.4, sample.weight.amount)
            assertEquals(com.stratum.domain.lift.Weight.WeightUnit.Kg, sample.weight.unit)
        }
    }

    @Nested
    @DisplayName("dailyStepCount")
    inner class DailyStepMapping {
        @Test
        fun mapsLocalStartToZoneInstant() {
            val local = LocalDateTime.of(2026, 8, 12, 0, 0)
            val count = HealthConnectMetricsMapper.dailyStepCount(local, ZoneOffset.UTC, 1_234L)
            assertEquals(1_234L, count.count)
            assertEquals(java.time.Instant.parse("2026-08-12T00:00:00Z"), count.dayStart)
        }

        @Test
        fun bucketsRecordsByLocalDay() {
            val zone = ZoneOffset.UTC
            val buckets = HealthConnectMetricsMapper.dailyStepCountsFromRecords(
                samples = listOf(
                    Instant.parse("2026-08-11T23:00:00Z") to 100L,
                    Instant.parse("2026-08-12T01:00:00Z") to 200L,
                    Instant.parse("2026-08-12T08:00:00Z") to 50L,
                ),
                zone = zone,
            )
            assertEquals(2, buckets.size)
            assertEquals(100L, buckets[0].count)
            assertEquals(250L, buckets[1].count)
            assertEquals(Instant.parse("2026-08-12T00:00:00Z"), buckets[1].dayStart)
        }
    }

    @Test
    @DisplayName("grantedTypes maps only read permissions that were granted")
    fun grantedTypesFilters() {
        val granted = setOf(
            HealthConnectRequestedPermissions.readSteps,
            HealthConnectRequestedPermissions.writeExercise,
        )
        assertEquals(
            setOf(com.stratum.domain.health.HealthMetricType.Steps),
            HealthConnectRequestedPermissions.grantedTypes(granted),
        )
    }
}
