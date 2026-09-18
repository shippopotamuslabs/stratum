package com.stratum.core.testing.telemetry

import com.stratum.domain.telemetry.AnalyticsPort

/** In-memory [AnalyticsPort] for unit tests. Records events for state-based assertions. */
class FakeAnalyticsPort : AnalyticsPort {
    data class Event(
        val name: String,
        val params: Map<String, String>,
    )

    val events = mutableListOf<Event>()

    override fun logEvent(
        name: String,
        params: Map<String, String>,
    ) {
        events += Event(name, params)
    }

    fun clear() {
        events.clear()
    }
}
