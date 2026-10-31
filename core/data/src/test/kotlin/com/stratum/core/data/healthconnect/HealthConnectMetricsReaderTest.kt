@file:Suppress("ForbiddenMockKInTests") // Health Connect SDK boundary (static HealthConnectClient) — no fake available.

package com.stratum.core.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.health.HealthMetricType
import com.stratum.domain.health.HealthMetricsRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class HealthConnectMetricsReaderTest {

    private val client: HealthConnectClient = mockk()
    private val permissionController: PermissionController = mockk()
    private val fakeLogging = FakeLoggingPort()
    private lateinit var reader: HealthConnectMetricsReader

    private val hrvGrant = HealthConnectRequestedPermissions.readHrv

    @BeforeEach
    fun setUp() {
        // Mock the Companion object (not statics): the @JvmStatic getSdkStatus/getOrCreate
        // forward through Companion instance methods, and static mocking does not
        // intercept their $default dispatch. Stub the 2-arg instance methods; the
        // real $default synthetics just fill the default provider package and delegate.
        mockkObject(HealthConnectClient.Companion)
        every { HealthConnectClient.getSdkStatus(any(), any()) } returns HealthConnectClient.SDK_AVAILABLE
        every { HealthConnectClient.getOrCreate(any(), any()) } returns client
        every { client.permissionController } returns permissionController
        reader = HealthConnectMetricsReader(
            context = RuntimeEnvironment.getApplication(),
            loggingPort = fakeLogging,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun request() = HealthMetricsRequest(
        zone = ZoneId.of("UTC"),
        now = Instant.parse("2026-10-03T12:00:00Z"),
    )

    private fun hrvRecord(rmssdMillis: Double) = HeartRateVariabilityRmssdRecord(
        time = Instant.parse("2026-10-03T08:00:00Z"),
        zoneOffset = ZoneOffset.UTC,
        heartRateVariabilityMillis = rmssdMillis,
        metadata = Metadata.activelyRecorded(device = Device(type = Device.TYPE_PHONE)),
    )

    private fun rhrRecord(time: Instant, beatsPerMinute: Long) = RestingHeartRateRecord(
        time = time,
        zoneOffset = ZoneOffset.UTC,
        beatsPerMinute = beatsPerMinute,
        metadata = Metadata.activelyRecorded(device = Device(type = Device.TYPE_PHONE)),
    )

    @Test
    @DisplayName("Given HRV granted and read succeeds when snapshot read then returns HRV samples")
    fun hrvReadSuccess() = runTest {
        coEvery { permissionController.getGrantedPermissions() } returns setOf(hrvGrant)
        coEvery { client.readRecords(any<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>>()) } returns
            ReadRecordsResponse(listOf(hrvRecord(42.5)), null)

        val snapshot = reader.readSnapshot(request())

        assertEquals(1, snapshot.hrvSamples.size)
        assertEquals(42.5, snapshot.hrvSamples.single().rmssdMillis)
        assertTrue(fakeLogging.errors.isEmpty())
        assertTrue(HealthMetricType.HeartRateVariability in snapshot.grantedTypes)
    }

    @Test
    @DisplayName("Given HRV read throws SecurityException when snapshot read then warns without non-fatal")
    fun hrvSecurityExceptionWarnsInsteadOfNonFatal() = runTest {
        coEvery { permissionController.getGrantedPermissions() } returns setOf(hrvGrant)
        coEvery { client.readRecords(any<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>>()) } throws
            SecurityException("Caller does not have permission to read HeartRateVariabilityRmssdRecord")

        val snapshot = reader.readSnapshot(request())

        assertTrue(snapshot.hrvSamples.isEmpty())
        assertTrue(
            fakeLogging.errors.isEmpty(),
            "SecurityException from a revoked grant must not become a Crashlytics non-fatal",
        )
        assertTrue(fakeLogging.warnings.any { it.message.contains("readHrv") })
        assertTrue(
            HealthMetricType.HeartRateVariability !in snapshot.grantedTypes,
            "a read denied with SecurityException self-corrects grantedTypes",
        )
    }

    @Test
    @DisplayName("Given HRV revoked between grant check and read when snapshot read then skips read and warns")
    fun hrvRevokedBeforeReadSkipsRead() = runTest {
        coEvery { permissionController.getGrantedPermissions() } returnsMany listOf(
            setOf(hrvGrant), // snapshot-level grantedReadTypes() check
            emptySet(), // re-verification immediately before the HRV read
        )

        val snapshot = reader.readSnapshot(request())

        assertTrue(snapshot.hrvSamples.isEmpty())
        assertTrue(fakeLogging.errors.isEmpty())
        assertTrue(fakeLogging.warnings.any { it.message.contains("Skipping HRV read") })
        assertTrue(
            HealthMetricType.HeartRateVariability !in snapshot.grantedTypes,
            "a grant revoked before the read self-corrects grantedTypes",
        )
    }

    @Test
    @DisplayName("Given HRV revoked but steps granted when snapshot read then grantedTypes drops only HRV")
    fun hrvRevokedKeepsOtherGrantedTypes() = runTest {
        coEvery { permissionController.getGrantedPermissions() } returnsMany listOf(
            setOf(hrvGrant, HealthConnectRequestedPermissions.readSteps), // snapshot-level check
            setOf(HealthConnectRequestedPermissions.readSteps), // HRV revoked before the HRV read
        )
        coEvery { client.aggregateGroupByPeriod(any<AggregateGroupByPeriodRequest>()) } returns
            emptyList()
        coEvery { client.readRecords(any<ReadRecordsRequest<StepsRecord>>()) } returns
            ReadRecordsResponse(emptyList(), null)

        val snapshot = reader.readSnapshot(request())

        assertEquals(setOf(HealthMetricType.Steps), snapshot.grantedTypes)
        assertTrue(snapshot.hrvSamples.isEmpty())
        assertTrue(fakeLogging.errors.isEmpty())
    }

    @Test
    @DisplayName("STR-112: RHR read spans 30 days and partitions in-window from lookback")
    fun rhrLookbackReadSpansThirtyDays() = runTest {
        coEvery { permissionController.getGrantedPermissions() } returns
            setOf(HealthConnectRequestedPermissions.readRestingHeartRate)
        val requestSlot = slot<ReadRecordsRequest<RestingHeartRateRecord>>()
        coEvery { client.readRecords(capture(requestSlot)) } returns
            ReadRecordsResponse(
                listOf(
                    rhrRecord(Instant.parse("2026-09-23T08:00:00Z"), 54L),
                    rhrRecord(Instant.parse("2026-10-02T08:00:00Z"), 52L),
                ),
                null,
            )

        val request = request()
        val snapshot = reader.readSnapshot(request)

        val filter = requestSlot.captured.timeRangeFilter
        assertEquals(request.vitalsLookbackStart, filter.startTime)
        assertEquals(request.windowEnd, filter.endTime)
        assertEquals(1, snapshot.restingHeartRateSamples.size)
        assertEquals(52L, snapshot.restingHeartRateSamples.single().beatsPerMinute)
        assertEquals(2, snapshot.restingHeartRateLookback.size)
        assertTrue(fakeLogging.errors.isEmpty())
    }

    @Test
    @DisplayName("Given HRV read throws unexpected exception when snapshot read then records non-fatal error")
    fun hrvUnexpectedExceptionStillRecordsNonFatal() = runTest {
        coEvery { permissionController.getGrantedPermissions() } returns setOf(hrvGrant)
        coEvery { client.readRecords(any<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>>()) } throws
            IOException("transient failure")

        val snapshot = reader.readSnapshot(request())

        assertTrue(snapshot.hrvSamples.isEmpty())
        assertEquals(1, fakeLogging.errors.size)
        assertTrue(fakeLogging.warnings.isEmpty())
        assertTrue(
            HealthMetricType.HeartRateVariability in snapshot.grantedTypes,
            "an unexpected read failure is not a revocation: grantedTypes stays intact",
        )
    }
}
