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

class AndroidCrashTelemetryAdapterTest {
    private lateinit var adapter: AndroidCrashTelemetryAdapter
    private lateinit var crashlytics: FirebaseCrashlytics

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        mockkStatic(FirebaseCrashlytics::class)
        crashlytics = mockk(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        adapter = AndroidCrashTelemetryAdapter()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `setUserId delegates to Crashlytics`() {
        adapter.setUserId("user-123")

        verify { crashlytics.setUserId("user-123") }
    }

    @Test
    fun `setUserId with null delegates empty string to Crashlytics`() {
        adapter.setUserId(null)

        verify { crashlytics.setUserId("") }
    }

    @Test
    fun `setScreen sets current_screen key and logs breadcrumb`() {
        adapter.setScreen("WorkoutKey")

        verify {
            crashlytics.setCustomKey("current_screen", "WorkoutKey")
            crashlytics.log("Screen: WorkoutKey")
        }
    }

    @Test
    fun `setActiveWorkoutId sets custom key and clears with empty string`() {
        adapter.setActiveWorkoutId("workout-1")
        adapter.setActiveWorkoutId(null)

        verify { crashlytics.setCustomKey("active_workout_id", "workout-1") }
        verify { crashlytics.setCustomKey("active_workout_id", "") }
    }

    @Test
    fun `setSubscriptionTier sets custom key`() {
        adapter.setSubscriptionTier("play_subscription")

        verify { crashlytics.setCustomKey("subscription_tier", "play_subscription") }
    }

    @Test
    fun `recordBreadcrumb scrubs message and logs to Crashlytics`() {
        adapter.recordBreadcrumb("Opened user@example.com profile")

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
    fun `recordNonFatal preserves original throwable instance when message has no PII`() {
        val throwable = RuntimeException("Clean failure message")
        adapter.recordNonFatal(throwable)

        verify { crashlytics.recordException(throwable) }
    }

    @Test
    fun `recordNonFatal scrubs PII while preserving original exception class and omitting original cause`() {
        val throwable = RuntimeException("Failed processing user@example.com at /storage/emulated/0/file.json")
        adapter.recordNonFatal(throwable)

        verify {
            crashlytics.recordException(
                match { exception ->
                    exception !== throwable &&
                        exception.javaClass == RuntimeException::class.java &&
                        exception.cause == null &&
                        exception.stackTrace.contentEquals(throwable.stackTrace) &&
                        exception.message?.contains(TelemetryPiiScrubber.REDACTED_EMAIL) == true &&
                        exception.message?.contains(TelemetryPiiScrubber.REDACTED_PATH) == true &&
                        exception.message?.contains("user@example.com") == false
                },
            )
        }
    }

    @Test
    fun `handles Crashlytics failure gracefully`() {
        every { FirebaseCrashlytics.getInstance() } throws RuntimeException("Firebase not initialized")

        adapter.setUserId("user-123")
        adapter.setScreen("Screen")
        adapter.recordBreadcrumb("Breadcrumb")
        adapter.recordNonFatal(RuntimeException("Test"))

        verify {
            Log.e("CrashTelemetry", any(), any<Throwable>())
        }
    }
}
