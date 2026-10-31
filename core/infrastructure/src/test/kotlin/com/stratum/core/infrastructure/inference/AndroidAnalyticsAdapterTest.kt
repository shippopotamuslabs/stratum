@file:Suppress("ForbiddenMockKInTests") // Firebase Analytics SDK boundary — no fake available.

package com.stratum.core.infrastructure.inference

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.stratum.domain.telemetry.TelemetryPiiScrubber
import com.stratum.domain.telemetry.TelemetryRouter
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class AndroidAnalyticsAdapterTest {
    private lateinit var adapter: AndroidAnalyticsAdapter
    private lateinit var analytics: FirebaseAnalytics

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        mockkStatic(FirebaseAnalytics::class)
        analytics = mockk(relaxed = true)
        every { FirebaseAnalytics.getInstance(any()) } returns analytics
        adapter = AndroidAnalyticsAdapter(RuntimeEnvironment.getApplication())
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("Given an event when logged then delegates scrubbed name and params to Analytics")
    fun delegatesEventToAnalytics() {
        val bundle = slot<Bundle>()
        adapter.logEvent("flag_exposure", mapOf("flag_key" to "enable_sample_flag", "enabled" to "true"))

        verify { analytics.logEvent("flag_exposure", capture(bundle)) }
        assertEquals("enable_sample_flag", bundle.captured.getString("flag_key"))
        assertEquals("true", bundle.captured.getString("enabled"))
    }

    @Test
    @DisplayName("Given PII in params when logged then values are scrubbed before Analytics")
    fun scrubsPiiFromParams() {
        val bundle = slot<Bundle>()
        adapter.logEvent("flag_exposure", mapOf("note" to "contact user@example.com for access"))

        verify { analytics.logEvent("flag_exposure", capture(bundle)) }
        val value = bundle.captured.getString("note").orEmpty()
        assertTrue(value.contains(TelemetryPiiScrubber.REDACTED_EMAIL))
    }

    @Test
    @DisplayName("Given a blank event name when logged then skips Analytics without throwing")
    fun blankNameSkipsAnalytics() {
        adapter.logEvent("   ")

        verify(exactly = 0) { analytics.logEvent(any(), any()) }
    }

    @Test
    @DisplayName("Given the SDK throws when logged then swallows the failure without throwing")
    fun sdkFailureIsSwallowed() {
        every { FirebaseAnalytics.getInstance(any()) } throws RuntimeException("Firebase not initialized")

        assertDoesNotThrow { adapter.logEvent("flag_exposure") }
    }

    @Test
    @DisplayName("Given the exceptional counter when logged then boundary and class pass through intact")
    fun exceptionalCounterPassesThrough() {
        val bundle = slot<Bundle>()
        adapter.logEvent(
            TelemetryRouter.EVENT_EXCEPTIONAL_OCCURRED,
            mapOf(
                TelemetryRouter.PARAM_BOUNDARY to "cloud_sync_worker",
                TelemetryRouter.PARAM_CLASS to "IllegalStateException",
            ),
        )

        verify { analytics.logEvent(TelemetryRouter.EVENT_EXCEPTIONAL_OCCURRED, capture(bundle)) }
        assertEquals("cloud_sync_worker", bundle.captured.getString(TelemetryRouter.PARAM_BOUNDARY))
        assertEquals("IllegalStateException", bundle.captured.getString(TelemetryRouter.PARAM_CLASS))
    }
}
