package com.stratum.domain.telemetry

import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.EVENT_SYNTHESIS_CLIENT_STAGE
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.PARAM_DURATION_MS
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.PARAM_JOB_ID
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.PARAM_STAGE
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.STAGE_DECODE_RESULT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SynthesisTelemetryTest {
    @Test
    fun `synthesisClientStage logs stage event with duration from injectable clock`() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        var now = 1_000L
        val telemetry =
            SynthesisTelemetry(
                logEvent = { name, params -> events.add(name to params) },
                clock = { now },
            )

        telemetry.synthesisClientStage("job-123", STAGE_DECODE_RESULT, startMs = 1_000L)
        now = 1_250L
        telemetry.synthesisClientStage("job-123", STAGE_DECODE_RESULT, startMs = 1_000L)

        assertEquals(2, events.size)
        val (name, params) = events[1]
        assertEquals(EVENT_SYNTHESIS_CLIENT_STAGE, name)
        assertEquals("job-123", params[PARAM_JOB_ID])
        assertEquals(STAGE_DECODE_RESULT, params[PARAM_STAGE])
        assertEquals("250", params[PARAM_DURATION_MS])
    }

    @Test
    fun `synthesisClientStage accepts a client session id as correlation id`() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val telemetry = SynthesisTelemetry(logEvent = { name, params -> events.add(name to params) })

        telemetry.synthesisClientStage("session-abc", "load_program_into_editor", startMs = 0L)

        val (_, params) = events.single()
        assertEquals("session-abc", params[PARAM_JOB_ID])
        assertEquals("load_program_into_editor", params[PARAM_STAGE])
    }
}
