@file:Suppress("ForbiddenMockKInTests") // Firebase Crashlytics SDK boundary — no fake available.

package com.stratum.core.inference

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.stratum.domain.telemetry.TelemetryPiiScrubber
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AndroidLoggingAdapterTest {
    private lateinit var adapter: AndroidLoggingAdapter
    private lateinit var crashlytics: FirebaseCrashlytics

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        mockkStatic(FirebaseCrashlytics::class)
        crashlytics = mockk(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        adapter = AndroidLoggingAdapter()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `error with throwable reports non-fatal to Crashlytics`() {
        val throwable = RuntimeException("Test exception")

        adapter.error("TestTag", "Test message", throwable)

        verify {
            crashlytics.log(match { it.contains("TestTag") && it.contains("Test message") })
            crashlytics.recordException(throwable)
        }
    }

    @Test
    fun `error without throwable skips Crashlytics`() {
        adapter.error("TestTag", "Test message", null)

        verify(exactly = 0) { crashlytics.log(any()) }
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `error scrubs email from Crashlytics breadcrumb`() {
        adapter.error("TestTag", "Failed for user@example.com", RuntimeException("Test exception"))

        verify {
            crashlytics.log(
                match {
                    it.contains(TelemetryPiiScrubber.REDACTED_EMAIL) &&
                        !it.contains("user@example.com")
                },
            )
        }
    }

    @Test
    fun `error handles Crashlytics failure gracefully`() {
        every { FirebaseCrashlytics.getInstance() } throws RuntimeException("Firebase not initialized")

        val throwable = RuntimeException("Test exception")
        adapter.error("TestTag", "Test message", throwable)

        verify {
            Log.e("AndroidLoggingAdapter", "Crashlytics not available. Error: Test message", any())
        }
    }
}
