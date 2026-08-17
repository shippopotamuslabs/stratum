@file:Suppress("ForbiddenMockKInTests") // android.util.Log framework boundary — static mocking required on JVM.

package com.stratum.core.inference

import android.util.Log
import com.stratum.core.testing.telemetry.FakeCrashTelemetryPort
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AndroidLoggingAdapterTest {
    private lateinit var fakeTelemetry: FakeCrashTelemetryPort
    private lateinit var adapter: AndroidLoggingAdapter

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        fakeTelemetry = FakeCrashTelemetryPort()
        adapter = AndroidLoggingAdapter(fakeTelemetry)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `info delegates breadcrumb to crash telemetry and logs to logcat`() {
        adapter.info("TestTag", "Info message")

        assertEquals(listOf("TestTag: Info message"), fakeTelemetry.breadcrumbs)
        assertTrue(fakeTelemetry.recordedExceptions.isEmpty())
        verify { Log.i("TestTag", "Info message") }
    }

    @Test
    fun `debug logs to logcat only and does not record breadcrumb`() {
        adapter.debug("TestTag", "Debug message")

        assertTrue(fakeTelemetry.breadcrumbs.isEmpty())
        assertTrue(fakeTelemetry.recordedExceptions.isEmpty())
        verify { Log.d("TestTag", "Debug message") }
    }

    @Test
    fun `warning delegates breadcrumb to crash telemetry and logs to logcat`() {
        adapter.warning("TestTag", "Warning message")

        assertEquals(listOf("TestTag: Warning message"), fakeTelemetry.breadcrumbs)
        assertTrue(fakeTelemetry.recordedExceptions.isEmpty())
        verify { Log.w("TestTag", "Warning message") }
    }

    @Test
    fun `error with throwable reports breadcrumb and non-fatal to crash telemetry`() {
        val throwable = RuntimeException("Test exception")

        adapter.error("TestTag", "Test message", throwable)

        assertEquals(listOf("TestTag: Test message"), fakeTelemetry.breadcrumbs)
        assertEquals(listOf(throwable), fakeTelemetry.recordedExceptions)
        verify { Log.e("TestTag", "Test message", throwable) }
    }

    @Test
    fun `error without throwable logs breadcrumb and skips recordNonFatal`() {
        adapter.error("TestTag", "Test message", null)

        assertEquals(listOf("TestTag: Test message"), fakeTelemetry.breadcrumbs)
        assertTrue(fakeTelemetry.recordedExceptions.isEmpty())
        verify { Log.e("TestTag", "Test message") }
    }

    @Test
    fun `errorSkippingNonFatal logs throwable and breadcrumb without recording non-fatal`() {
        val throwable = RuntimeException("Fatal crash")

        adapter.errorSkippingNonFatal("Uncaught", "Uncaught RuntimeException in thread main: Fatal crash", throwable)

        assertEquals(
            listOf("Uncaught: Uncaught RuntimeException in thread main: Fatal crash"),
            fakeTelemetry.breadcrumbs,
        )
        assertTrue(fakeTelemetry.recordedExceptions.isEmpty())
        verify {
            Log.e(
                "Uncaught",
                "Uncaught RuntimeException in thread main: Fatal crash",
                throwable,
            )
        }
    }
}

