package com.stratum.domain.telemetry

/**
 * Centralized STR-13 synthesis waterfall telemetry contract.
 *
 * Single owner for the synthesis funnel event names, param keys, and timing.
 * All three events carry the job_id so the waterfall can be joined from
 * client tap through server trace to rendered program.
 *
 * Takes a logEvent lambda (instead of AnalyticsPort directly) so it can be
 * used from both @Inject singletons and manually-constructed controllers.
 * The clock is injectable (defaults to wall-clock) so duration math stays
 * unit-testable. Durations are kept as Long until the analytics boundary,
 * where a single helper does the String conversion.
 */
class SynthesisTelemetry(
    private val logEvent: (name: String, params: Map<String, String>) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Start a timed synthesis session. Returns the start timestamp for
     * [synthesisReadyObserved].
     */
    fun synthesisStarted(jobId: String): Long {
        val startMs = clock()
        logEvent(
            EVENT_SYNTHESIS_STARTED,
            mapOf(PARAM_JOB_ID to jobId),
        )
        return startMs
    }

    fun synthesisReadyObserved(jobId: String, startMs: Long) {
        val durationMs = clock() - startMs
        logEvent(
            EVENT_SYNTHESIS_READY_OBSERVED,
            mapOf(
                PARAM_JOB_ID to jobId,
                PARAM_DURATION_MS to durationMs.toString(),
            ),
        )
    }

    fun synthesisRendered(jobId: String) {
        logEvent(
            EVENT_SYNTHESIS_RENDERED,
            mapOf(PARAM_JOB_ID to jobId),
        )
    }

    companion object {
        const val EVENT_SYNTHESIS_STARTED = "synthesis_started"
        const val EVENT_SYNTHESIS_READY_OBSERVED = "synthesis_ready_observed"
        const val EVENT_SYNTHESIS_RENDERED = "synthesis_rendered"

        const val PARAM_JOB_ID = "job_id"
        const val PARAM_DURATION_MS = "duration_ms"
    }
}
