package com.stratum.core.inference

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.stratum.domain.telemetry.AnalyticsPort
import com.stratum.domain.telemetry.TelemetryPiiScrubber
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AnalyticsPort] backed by Firebase Analytics. Event names and parameters are scrubbed for
 * PII before transmission. All SDK calls are guarded so telemetry can never crash the app.
 */
@Singleton
class AndroidAnalyticsAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) : AnalyticsPort {
    override fun logEvent(
        name: String,
        params: Map<String, String>,
    ) {
        val eventName = TelemetryPiiScrubber.scrub(name)?.trim().orEmpty()
        if (eventName.isBlank()) {
            Log.w(TAG, "Ignoring analytics event with blank name")
            return
        }
        runCatching {
            val bundle =
                Bundle().apply {
                    params.forEach { (key, value) ->
                        putString(scrubbed(key), scrubbed(value))
                    }
                }
            FirebaseAnalytics.getInstance(context).logEvent(eventName, bundle)
        }.onFailure { error ->
            Log.e(TAG, "Analytics logEvent failed: $eventName", error)
        }
    }

    private fun scrubbed(value: String): String = TelemetryPiiScrubber.scrub(value).orEmpty()

    private companion object {
        const val TAG = "Analytics"
    }
}
