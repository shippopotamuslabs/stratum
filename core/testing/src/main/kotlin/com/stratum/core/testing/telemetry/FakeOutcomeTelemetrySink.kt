package com.stratum.core.testing.telemetry

import com.stratum.core.infrastructure.telemetry.OutcomeTelemetry
import com.stratum.core.infrastructure.telemetry.OutcomeTelemetrySink

/** In-memory [OutcomeTelemetrySink] for unit tests. Records outcomes for state-based assertions. */
class FakeOutcomeTelemetrySink : OutcomeTelemetrySink {
    val outcomes = mutableListOf<OutcomeTelemetry>()

    override fun record(outcome: OutcomeTelemetry) {
        outcomes += outcome
    }

    fun clear() {
        outcomes.clear()
    }
}
