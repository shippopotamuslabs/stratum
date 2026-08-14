package com.stratum.core.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.stratum.domain.healthconnect.DailyStepCount
import com.stratum.domain.healthconnect.HealthConnectMetricsPort
import com.stratum.domain.healthconnect.HealthMetricType
import com.stratum.domain.healthconnect.HealthMetricsRequest
import com.stratum.domain.healthconnect.HealthMetricsSnapshot
import com.stratum.domain.healthconnect.HeartRateSample
import com.stratum.domain.healthconnect.HrvSample
import com.stratum.domain.healthconnect.SleepSessionSample
import com.stratum.domain.healthconnect.SleepStageSample
import com.stratum.domain.healthconnect.WeightSample
import com.stratum.domain.logging.LoggingPort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

@Singleton
class HealthConnectMetricsReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loggingPort: LoggingPort,
) : HealthConnectMetricsPort {

    override suspend fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    override suspend fun grantedReadTypes(): Set<HealthMetricType> {
        if (!isAvailable()) return emptySet()
        return catchHealthConnect(emptySet(), "grantedReadTypes") {
            val granted = HealthConnectClient.getOrCreate(context)
                .permissionController
                .getGrantedPermissions()
            HealthConnectRequestedPermissions.grantedTypes(granted)
        }
    }

    override suspend fun readSnapshot(request: HealthMetricsRequest): HealthMetricsSnapshot {
        val granted = grantedReadTypes()
        if (granted.isEmpty() || !isAvailable()) {
            return HealthMetricsSnapshot(grantedTypes = granted)
        }
        val client = HealthConnectClient.getOrCreate(context)
        return HealthMetricsSnapshot(
            grantedTypes = granted,
            dailySteps = if (HealthMetricType.Steps in granted) {
                readDailySteps(client, request)
            } else {
                emptyList()
            },
            weightSamples = if (HealthMetricType.Weight in granted) {
                readWeight(client, request)
            } else {
                emptyList()
            },
            sleepSessions = if (HealthMetricType.Sleep in granted) {
                readSleep(client, request)
            } else {
                emptyList()
            },
            restingHeartRateSamples = if (HealthMetricType.RestingHeartRate in granted) {
                readRestingHeartRate(client, request)
            } else {
                emptyList()
            },
            hrvSamples = if (HealthMetricType.HeartRateVariability in granted) {
                readHrv(client, request)
            } else {
                emptyList()
            },
        )
    }

    private suspend fun readDailySteps(
        client: HealthConnectClient,
        request: HealthMetricsRequest,
    ): List<DailyStepCount> {
        val aggregated = catchHealthConnect(emptyList(), "aggregateSteps") {
            val rows = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        request.localWindowStart,
                        request.localWindowEnd,
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            )
            rows.map { row ->
                HealthConnectMetricsMapper.dailyStepCount(
                    localStart = row.startTime,
                    zone = request.zone,
                    count = row.result[StepsRecord.COUNT_TOTAL] ?: 0L,
                )
            }
        }
        if (aggregated.any { it.count > 0L }) return aggregated
        val fromRecords = catchHealthConnect(emptyList(), "readSteps") {
            HealthConnectMetricsMapper.dailyStepCountsFromRecords(
                samples = readAll(client, StepsRecord::class, instantFilter(request)).map { record ->
                    record.startTime to record.count
                },
                zone = request.zone,
            )
        }
        return fromRecords.ifEmpty { aggregated }
    }

    private suspend fun readWeight(
        client: HealthConnectClient,
        request: HealthMetricsRequest,
    ): List<WeightSample> = catchHealthConnect(emptyList(), "readWeight") {
        readAll(client, WeightRecord::class, instantFilter(request)).map { record ->
            HealthConnectMetricsMapper.weightSample(
                time = record.time,
                kilograms = record.weight.inKilograms,
            )
        }
    }

    private suspend fun readSleep(
        client: HealthConnectClient,
        request: HealthMetricsRequest,
    ): List<SleepSessionSample> = catchHealthConnect(emptyList(), "readSleep") {
        readAll(client, SleepSessionRecord::class, instantFilter(request)).map { record ->
            SleepSessionSample(
                start = record.startTime,
                end = record.endTime,
                stages = record.stages.map { stage ->
                    SleepStageSample(
                        start = stage.startTime,
                        end = stage.endTime,
                        stage = HealthConnectMetricsMapper.sleepStageType(stage.stage),
                    )
                },
            )
        }
    }

    private suspend fun readRestingHeartRate(
        client: HealthConnectClient,
        request: HealthMetricsRequest,
    ): List<HeartRateSample> = catchHealthConnect(emptyList(), "readRestingHeartRate") {
        readAll(client, RestingHeartRateRecord::class, instantFilter(request)).map { record ->
            HeartRateSample(time = record.time, beatsPerMinute = record.beatsPerMinute)
        }
    }

    private suspend fun readHrv(
        client: HealthConnectClient,
        request: HealthMetricsRequest,
    ): List<HrvSample> = catchHealthConnect(emptyList(), "readHrv") {
        readAll(client, HeartRateVariabilityRmssdRecord::class, instantFilter(request)).map { record ->
            HrvSample(time = record.time, rmssdMillis = record.heartRateVariabilityMillis)
        }
    }

    private fun instantFilter(request: HealthMetricsRequest): TimeRangeFilter =
        TimeRangeFilter.between(request.windowStart, request.windowEnd)

    private suspend fun <T : Record> readAll(
        client: HealthConnectClient,
        type: KClass<T>,
        filter: TimeRangeFilter,
    ): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = filter,
                    pageToken = token,
                ),
            )
            out += response.records
            token = response.pageToken
        } while (token != null)
        return out
    }

    private suspend inline fun <T> catchHealthConnect(
        default: T,
        label: String,
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            loggingPort.error(HEALTH_CONNECT_METRICS_TAG, "Health Connect $label failed", error)
            default
        }

    private companion object {
        const val HEALTH_CONNECT_METRICS_TAG = "HealthConnectMetrics"
    }
}

