package com.stratum.core.inference

import com.stratum.core.testing.telemetry.FakeCrashTelemetryPort
import com.stratum.core.testing.telemetry.FakePendingClientLogStore
import com.stratum.core.testing.telemetry.FakeTelemetryUploadPort
import com.stratum.domain.telemetry.ClientLogBuffer
import com.stratum.domain.telemetry.ClientLogSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Runs under Robolectric so `android.util.Log` inside [AndroidLoggingAdapter]
 * hits shadows instead of failing on the plain JVM — no MockK needed.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ForwardingLoggingAdapterTest {
    private lateinit var telemetry: FakeCrashTelemetryPort
    private lateinit var upload: FakeTelemetryUploadPort
    private lateinit var store: FakePendingClientLogStore

    @BeforeEach
    fun setup() {
        telemetry = FakeCrashTelemetryPort()
        upload = FakeTelemetryUploadPort()
        store = FakePendingClientLogStore()
    }

    private data class Fixture(
        val forwarding: ForwardingLoggingAdapter,
        val buffer: ClientLogBuffer,
    )

    private fun fixture(scope: CoroutineScope): Fixture {
        val buffer =
            ClientLogBuffer(
                scope = scope,
                upload = upload,
                pendingStore = store,
                appVersion = "test-version",
            )
        return Fixture(ForwardingLoggingAdapter(AndroidLoggingAdapter(telemetry), buffer), buffer)
    }

    @Test
    @DisplayName("warning keeps its breadcrumb and enqueues a WARNING entry")
    fun warningIsTierTwo() =
        runTest {
            val (forwarding, buffer) = fixture(this)
            forwarding.warning("SwapLift", "no active program")

            assertEquals(listOf("SwapLift: no active program"), telemetry.breadcrumbs)
            assertTrue(telemetry.recordedExceptions.isEmpty())

            buffer.flush()

            val batch = upload.uploadedBatches.single()
            assertEquals(1, batch.entries.size)
            assertEquals(ClientLogSeverity.WARNING, batch.entries.single().severity)
            assertEquals("SwapLift", batch.entries.single().tag)
            assertEquals("no active program", batch.entries.single().message)
            assertEquals("test-version", batch.appVersion)
        }

    @Test
    @DisplayName("error stays Crashlytics-only and never enqueues")
    fun errorIsTierOneOnly() =
        runTest {
            val (forwarding, buffer) = fixture(this)
            val failure = RuntimeException("unexpected")
            forwarding.error("Tag", "boom", failure)

            assertEquals(listOf(failure), telemetry.recordedExceptions)

            buffer.flush()

            assertTrue(upload.uploadedBatches.isEmpty())
        }

    @Test
    @DisplayName("errorSkippingNonFatal skips the non-fatal and enqueues an ERROR with the type")
    fun errorSkippingNonFatalIsTierTwo() =
        runTest {
            val (forwarding, buffer) = fixture(this)
            forwarding.errorSkippingNonFatal("Uncaught", "pre-fatal path", IllegalStateException("bad state"))

            assertTrue(telemetry.recordedExceptions.isEmpty())
            assertEquals(listOf("Uncaught: pre-fatal path"), telemetry.breadcrumbs)

            buffer.flush()

            val entry = upload.uploadedBatches.single().entries.single()
            assertEquals(ClientLogSeverity.ERROR, entry.severity)
            assertTrue(entry.message.contains("IllegalStateException"))
            assertTrue(entry.message.contains("bad state"))
        }

    @Test
    @DisplayName("enqueued entries scrub PII")
    fun enqueuedEntriesScrubPii() =
        runTest {
            val (forwarding, buffer) = fixture(this)
            forwarding.warning("Tag", "failed for user@example.com")

            buffer.flush()

            val message = upload.uploadedBatches.single().entries.single().message
            assertTrue(message.contains("[REDACTED_EMAIL]"))
            assertTrue(!message.contains("user@example.com"))
        }
}
