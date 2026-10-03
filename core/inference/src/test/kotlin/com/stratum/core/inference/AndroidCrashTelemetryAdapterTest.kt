@file:Suppress("ForbiddenMockKInTests") // Firebase Crashlytics SDK boundary — no fake available.

package com.stratum.core.inference

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.stratum.domain.telemetry.SanitizedTelemetryException
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
    fun `setFlagState sets namespaced flag key`() {
        adapter.setFlagState("enable_sample_flag", true)
        adapter.setFlagState("enable_other_flag", false)

        verify { crashlytics.setCustomKey("flag_enable_sample_flag", "true") }
        verify { crashlytics.setCustomKey("flag_enable_other_flag", "false") }
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
    fun `recordNonFatal sets original-class key when sanitizer falls back`() {
        val throwable = UnreconstructibleException(42)
        adapter.recordNonFatal(throwable)

        verify {
            crashlytics.setCustomKey(
                "sanitized_original_class",
                UnreconstructibleException::class.java.name,
            )
        }
        verify {
            crashlytics.recordException(
                match { exception ->
                    exception is SanitizedTelemetryException &&
                        exception.originalClassName == UnreconstructibleException::class.java.name &&
                        exception.message?.contains(TelemetryPiiScrubber.REDACTED_EMAIL) == true &&
                        exception.message?.contains("user@example.com") == false
                },
            )
        }
    }

    @Test
    fun `recordNonFatal clears original-class keys when exception type is preserved`() {
        val throwable = RuntimeException("Failed processing user@example.com")
        adapter.recordNonFatal(throwable)

        verify { crashlytics.setCustomKey("sanitized_original_class", "") }
        verify { crashlytics.setCustomKey("sanitized_original_class_2", "") }
        verify { crashlytics.setCustomKey("sanitized_original_class_3", "") }
        verify {
            crashlytics.recordException(
                match { exception ->
                    exception.javaClass == RuntimeException::class.java
                },
            )
        }
    }

    @Test
    fun `recordNonFatal forwards only the first three original-class names`() {
        val throwable = RuntimeException("wrapper user@example.com")
        throwable.addSuppressed(UnreconstructibleA())
        throwable.addSuppressed(UnreconstructibleB())
        throwable.addSuppressed(UnreconstructibleC())
        throwable.addSuppressed(UnreconstructibleD())
        adapter.recordNonFatal(throwable)

        verify { crashlytics.setCustomKey("sanitized_original_class", UnreconstructibleA::class.java.name) }
        verify { crashlytics.setCustomKey("sanitized_original_class_2", UnreconstructibleB::class.java.name) }
        verify { crashlytics.setCustomKey("sanitized_original_class_3", UnreconstructibleC::class.java.name) }
        verify(exactly = 0) { crashlytics.setCustomKey("sanitized_original_class_4", any<String>()) }
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

    private class UnreconstructibleException(val code: Int) :
        RuntimeException("failed for user@example.com ($code)")

    private class UnreconstructibleA : RuntimeException("failed for user@example.com (a)")

    private class UnreconstructibleB : RuntimeException("failed for user@example.com (b)")

    private class UnreconstructibleC : RuntimeException("failed for user@example.com (c)")

    private class UnreconstructibleD : RuntimeException("failed for user@example.com (d)")
}
