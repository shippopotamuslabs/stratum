package com.stratum.domain.telemetry

/**
 * Correlation identity for a client stage event (STR-19).
 *
 * A stage belongs to exactly one namespace: either a server synthesis job
 * or a client-generated session (on-device generation has no server job).
 * The namespaces are never mixed in a single param, so funnel queries can
 * join on correlation_id without silent mis-joins.
 */
sealed interface ClientStageCorrelation {
    val id: String

    data class Job(override val id: String) : ClientStageCorrelation

    data class Session(override val id: String) : ClientStageCorrelation
}

/**
 * Typed client processing stages (STR-19). Wire names are the stable funnel
 * strings; add entries here instead of passing raw stage strings.
 */
enum class ClientStage(val wireName: String) {
    DECODE_RESULT("decode_result"),
    GENERATE_PROGRAM("generate_program"),
    LOAD_PROGRAM_INTO_EDITOR("load_program_into_editor"),
}

/**
 * Centralized STR-13 synthesis waterfall telemetry contract.
 *
 * Single owner for the synthesis funnel event names, param keys, and timing.
 * Started/ready-observed carry the cloud job_id (logged where the cloud jobId
 * is visible, i.e. the synthesis adapter). Rendered always carries the client
 * session_id and carries job_id only when the call site knows the cloud
 * jobId, so the waterfall joins deterministically without time-window
 * heuristics whenever both legs exist. job_id is never overloaded with a
 * session id: the two namespaces stay in separate params (STR-19).
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
     * Current millis on the telemetry clock. Anchors tap-time for
     * [synthesisReadyObserved]/[synthesisRendered] when the start event and
     * the end event are logged by different layers (e.g. tap in UI, ready in
     * the synthesis adapter).
     */
    fun nowMs(): Long = clock()

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

    /**
     * Logs the tap-to-rendered leg. [sessionId] is always the client session;
     * [jobId] is the cloud synthesis jobId when the call site can see it (the
     * synthesis adapter legs) and null otherwise. job_id is omitted — never
     * filled with a session id — so funnel queries join on one ID namespace
     * per param: job_id for cloud legs, session_id for client legs, and
     * correlation_id for whichever identifies the generation end to end.
     */
    fun synthesisRendered(sessionId: String, startMs: Long, jobId: String? = null) {
        val durationMs = clock() - startMs
        logEvent(
            EVENT_SYNTHESIS_RENDERED,
            buildMap {
                put(PARAM_CORRELATION_ID, jobId ?: sessionId)
                put(PARAM_SESSION_ID, sessionId)
                if (jobId != null) {
                    put(PARAM_JOB_ID, jobId)
                }
                put(PARAM_DURATION_MS, durationMs.toString())
            },
        )
    }

    /**
     * Starts a client-side processing stage timer (STR-19). Covers the
     * post-ready client window (result decode, program assembly, editor load)
     * that the server trace cannot see.
     *
     * Timing is owned by this helper and uses the injectable [clock] for both
     * the start and stop timestamps, so durations stay deterministic under
     * fakes and call sites carry no timestamp boilerplate. Call [StageTimer.stop]
     * once the stage completes; if the stage fails, simply do not stop the
     * timer and no event is emitted.
     */
    fun startStage(
        correlation: ClientStageCorrelation,
        stage: ClientStage,
    ): StageTimer = StageTimer(clock(), correlation, stage)

    /** Running stage timer. See [startStage]. */
    inner class StageTimer(
        private val startMs: Long,
        private val correlation: ClientStageCorrelation,
        private val stage: ClientStage,
    ) {
        /**
         * Stops the timer and emits the stage event. Returns the measured
         * duration in milliseconds.
         */
        fun stop(): Long {
            val durationMs = clock() - startMs
            val params =
                buildMap {
                    put(PARAM_CORRELATION_ID, correlation.id)
                    when (correlation) {
                        is ClientStageCorrelation.Job -> put(PARAM_JOB_ID, correlation.id)
                        is ClientStageCorrelation.Session -> put(PARAM_SESSION_ID, correlation.id)
                    }
                    put(PARAM_STAGE, stage.wireName)
                    put(PARAM_DURATION_MS, durationMs.toString())
                }
            logEvent(EVENT_SYNTHESIS_CLIENT_STAGE, params)
            return durationMs
        }
    }

    companion object {
        const val EVENT_SYNTHESIS_STARTED = "synthesis_started"
        const val EVENT_SYNTHESIS_READY_OBSERVED = "synthesis_ready_observed"
        const val EVENT_SYNTHESIS_RENDERED = "synthesis_rendered"
        const val EVENT_SYNTHESIS_CLIENT_STAGE = "synthesis_client_stage"

        const val PARAM_JOB_ID = "job_id"
        const val PARAM_CORRELATION_ID = "correlation_id"
        const val PARAM_SESSION_ID = "session_id"
        const val PARAM_DURATION_MS = "duration_ms"
        const val PARAM_STAGE = "stage"
    }
}
