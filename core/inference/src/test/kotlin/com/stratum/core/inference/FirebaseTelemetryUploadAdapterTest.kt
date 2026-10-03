@file:Suppress("ForbiddenMockKInTests") // Firebase Functions SDK boundary — no fake available.

package com.stratum.core.inference

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.stratum.core.testing.telemetry.FakeCrashTelemetryPort
import com.stratum.domain.telemetry.ClientLogBatch
import com.stratum.domain.telemetry.ClientLogEntry
import com.stratum.domain.telemetry.ClientLogSeverity
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Runs under Robolectric so `android.util.Log` inside [AndroidLoggingAdapter]
 * hits shadows instead of failing on the plain JVM — no static mocking needed.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirebaseTelemetryUploadAdapterTest {
    private val functions: FirebaseFunctions = mockk()
    private val telemetry = FakeCrashTelemetryPort()
    private val adapter = FirebaseTelemetryUploadAdapter(functions, AndroidLoggingAdapter(telemetry))

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun batch() =
        ClientLogBatch(
            entries =
                listOf(
                    ClientLogEntry(
                        severity = ClientLogSeverity.WARNING,
                        tag = "Tag",
                        message = "warn",
                        clientTimestampMs = 1_000L,
                        sessionId = "run-1",
                    ),
                ),
            droppedEntryCount = 0,
            appVersion = "test-version",
        )

    @Test
    @DisplayName("empty batch succeeds without touching the network")
    fun emptyBatchShortCircuits() =
        runTest {
            val result = adapter.upload(batch().copy(entries = emptyList()))

            assertTrue(result.isSuccess)
            verify(exactly = 0) { functions.getHttpsCallable(any()) }
        }

    @Test
    @DisplayName("cancellation is rethrown instead of reported as a retryable failure")
    fun cancellationIsRethrown() =
        runTest {
            val callable: HttpsCallableReference = mockk()
            every {
                functions.getHttpsCallable("ingestClientLogs")
            } returns callable
            every { callable.call(any()) } returns
                Tasks.forException(CancellationException("call cancelled"))

            try {
                adapter.upload(batch())
                fail("Expected CancellationException to propagate")
            } catch (error: CancellationException) {
                assertEquals("call cancelled", error.message)
            }
        }
}
