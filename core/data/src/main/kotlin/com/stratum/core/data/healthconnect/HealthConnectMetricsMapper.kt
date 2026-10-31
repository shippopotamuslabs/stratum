package com.stratum.core.data.healthconnect

import androidx.health.connect.client.records.SleepSessionRecord
import com.stratum.domain.health.DailyStepCount
import com.stratum.domain.health.SleepStageType
import com.stratum.domain.health.WeightSample
import com.stratum.domain.lift.Weight
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Pure Health Connect record → domain mapping. No SDK I/O. */
object HealthConnectMetricsMapper {

    /**
     * Maps an SDK sleep stage to the domain [SleepStageType], or null when the SDK
     * stage carries no zone distinction the domain math uses. Out-of-bed counts as
     * awake; generic sleeping/unknown stages are dropped here, and the session span
     * still counts that time as sleep via SleepZones.asleepWithin (span minus awake)
     * — STR-264 semantics preserved.
     */
    fun sleepStageType(stage: Int): SleepStageType? =
        when (stage) {
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
            -> SleepStageType.Awake
            SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.Light
            SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.Deep
            SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.Rem
            else -> null
        }

    fun weightSample(time: Instant, kilograms: Double): WeightSample =
        WeightSample(
            time = time,
            weight = Weight(kilograms, Weight.WeightUnit.Kg),
        )

    fun dailyStepCount(localStart: LocalDateTime, zone: ZoneId, count: Long): DailyStepCount =
        DailyStepCount(
            dayStart = localStart.atZone(zone).toInstant(),
            count = count,
        )

    fun dailyStepCountsFromRecords(
        samples: List<Pair<Instant, Long>>,
        zone: ZoneId,
    ): List<DailyStepCount> =
        samples
            .groupBy { it.first.atZone(zone).toLocalDate() }
            .map { (date, daySamples) ->
                DailyStepCount(
                    dayStart = date.atStartOfDay(zone).toInstant(),
                    count = daySamples.sumOf { it.second },
                )
            }
            .sortedBy { it.dayStart }
}
