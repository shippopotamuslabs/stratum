package com.stratum.domain.telemetry

import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.EVENT_SYNTHESIS_READY_OBSERVED
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.EVENT_SYNTHESIS_RENDERED
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.EVENT_SYNTHESIS_STARTED
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.EVENT_SYNTHESIS_CLIENT_STAGE
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.PARAM_CORRELATION_ID
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.PARAM_DURATION_MS
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.PARAM_JOB_ID
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.PARAM_SESSION_ID
import com.stratum.domain.telemetry.SynthesisTelemetry.Companion.PARAM_STAGE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SynthesisTelemetryTest {
    @Test
    fun `startStage logs stage event with duration from injectable clock`() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        var now = 1_000L
        val telemetry =
            SynthesisTelemetry(
                logEvent = { name, params -> events.add(name to params) },
                clock = { now },
            )

        val timer =
            telemetry.startStage(
                ClientStageCorrelation.Job("job-123"),
                ClientStage.DECODE_RESULT,
            )
        now = 1_250L
        val durationMs = timer.stop()

        assertEquals(250L, durationMs)
        val (name, params) = events.single()
        assertEquals(EVENT_SYNTHESIS_CLIENT_STAGE, name)
        assertEquals("job-123", params[PARAM_CORRELATION_ID])
        assertEquals("job-123", params[PARAM_JOB_ID])
        assertNull(params[PARAM_SESSION_ID])
        assertEquals("decode_result", params[PARAM_STAGE])
        assertEquals("250", params[PARAM_DURATION_MS])
    }

    @Test
    fun `startStage uses session namespace for client-generated sessions`() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val telemetry =
            SynthesisTelemetry(logEvent = { name, params -> events.add(name to params) })

        val timer =
            telemetry.startStage(
                ClientStageCorrelation.Session("session-abc"),
                ClientStage.LOAD_PROGRAM_INTO_EDITOR,
            )
        timer.stop()

        val (_, params) = events.single()
        assertEquals("session-abc", params[PARAM_CORRELATION_ID])
        assertEquals("session-abc", params[PARAM_SESSION_ID])
        assertNull(params[PARAM_JOB_ID])
        assertEquals("load_program_into_editor", params[PARAM_STAGE])
    }

    @Test
    fun `unstopped stage timer emits nothing`() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val telemetry =
            SynthesisTelemetry(logEvent = { name, params -> events.add(name to params) })

        telemetry.startStage(
            ClientStageCorrelation.Job("job-123"),
            ClientStage.GENERATE_PROGRAM,
        )

        assertEquals(0, events.size)
    }

    @Test
    fun `synthesisReadyObserved logs tap-to-ready duration`() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        var now = 10_000L
        val telemetry =
            SynthesisTelemetry(
                logEvent = { name, params -> events.add(name to params) },
                clock = { now },
            )

        val startMs = telemetry.synthesisStarted("job-123")
        now = 190_000L
        telemetry.synthesisReadyObserved("job-123", startMs)

        assertEquals(EVENT_SYNTHESIS_STARTED, events[0].first)
        assertEquals("job-123", events[0].second[PARAM_JOB_ID])
        val (_, params) = events[1]
        assertEquals(EVENT_SYNTHESIS_READY_OBSERVED, events[1].first)
        assertEquals("job-123", params[PARAM_JOB_ID])
        assertEquals("180000", params[PARAM_DURATION_MS])
    }

    @Test
    fun `synthesisRendered logs tap-to-rendered duration`() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        var now = 10_000L
        val telemetry =
            SynthesisTelemetry(
                logEvent = { name, params -> events.add(name to params) },
                clock = { now },
            )

        val tapMs = telemetry.nowMs()
        now = 200_000L
        telemetry.synthesisRendered("job-123", tapMs)

        val (name, params) = events.single()
        assertEquals(EVENT_SYNTHESIS_RENDERED, name)
        assertEquals("job-123", params[PARAM_JOB_ID])
        assertEquals("190000", params[PARAM_DURATION_MS])
    }
}
