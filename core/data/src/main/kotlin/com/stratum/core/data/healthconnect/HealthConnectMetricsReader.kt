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
        // The HRV read re-verifies its grant immediately before reading. If the grant
        // disappeared since the snapshot-level check, self-correct grantedTypes so the
        // snapshot never claims HRV granted with empty samples.
        val hrvRead = if (HealthMetricType.HeartRateVariability in granted) {
            readHrv(client, request)
        } else {
            HrvReadResult(emptyList(), grantGoneAtRead = false)
        }
        val correctedGranted = if (hrvRead.grantGoneAtRead) {
            granted - HealthMetricType.HeartRateVariability
        } else {
            granted
        }
        return HealthMetricsSnapshot(
            grantedTypes = correctedGranted,
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
            hrvSamples = hrvRead.samples,
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
                originPackage = record.metadata.dataOrigin.packageName,
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

    /**
     * Result of an HRV read that re-verifies its grant immediately before reading.
     * [grantGoneAtRead] is true when the HRV grant was missing at re-verification time
     * or the read itself was denied with a SecurityException.
     */
    private data class HrvReadResult(
        val samples: List<HrvSample>,
        val grantGoneAtRead: Boolean,
    )

    private suspend fun readHrv(
        client: HealthConnectClient,
        request: HealthMetricsRequest,
    ): HrvReadResult {
        // Re-verify the HRV grant immediately before reading: the snapshot-level
        // grantedReadTypes() check can go stale when the user revokes the permission
        // in Health Connect settings (grants are selective per data type).
        val grantedNow = currentGrantedPermissions(client, "grantedReadTypesBeforeHrv")
        if (HealthConnectRequestedPermissions.readHrv !in grantedNow) {
            loggingPort.warning(
                HEALTH_CONNECT_METRICS_TAG,
                "Skipping HRV read: HRV read permission not granted (granted=$grantedNow)",
            )
            return HrvReadResult(emptyList(), grantGoneAtRead = true)
        }
        var deniedAtRead = false
        val samples = catchHealthConnect(
            default = emptyList(),
            label = "readHrv",
            onPermissionDenied = { deniedAtRead = true },
        ) {
            readAll(client, HeartRateVariabilityRmssdRecord::class, instantFilter(request)).map { record ->
                HrvSample(time = record.time, rmssdMillis = record.heartRateVariabilityMillis)
            }
        }
        return HrvReadResult(samples, grantGoneAtRead = deniedAtRead)
    }

    private fun instantFilter(request: HealthMetricsRequest): TimeRangeFilter =
        TimeRangeFilter.between(request.windowStart, request.windowEnd)

    private suspend fun currentGrantedPermissions(
        client: HealthConnectClient,
        label: String,
    ): Set<String> =
        catchHealthConnect(emptySet(), label) {
            client.permissionController.getGrantedPermissions()
        }

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

    /**
     * Runs a Health Connect operation, mapping failures to [default].
     *
     * Exception policy, uniform across all Health Connect reads: a [SecurityException]
     * means the user revoked a grant in system settings (expected user-driven state),
     * so it logs a warning and never records a Crashlytics non-fatal; any other
     * [Throwable] logs an error (which records a non-fatal). [onPermissionDenied]
     * lets callers observe the denial path, e.g. to self-correct a snapshot's
     * granted set.
     */
    private suspend inline fun <T> catchHealthConnect(
        default: T,
        label: String,
        onPermissionDenied: () -> Unit = {},
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            // Permission-state failure (e.g. the user revoked a Health Connect grant
            // in system settings after our last check). Expected user-driven state,
            // not a code bug: warn instead of recording a Crashlytics non-fatal.
            loggingPort.warning(
                HEALTH_CONNECT_METRICS_TAG,
                "Health Connect $label denied (SecurityException): ${e.message}",
            )
            onPermissionDenied()
            default
        } catch (error: Throwable) {
            loggingPort.error(HEALTH_CONNECT_METRICS_TAG, "Health Connect $label failed", error)
            default
        }

    private companion object {
        const val HEALTH_CONNECT_METRICS_TAG = "HealthConnectMetrics"
    }
}

