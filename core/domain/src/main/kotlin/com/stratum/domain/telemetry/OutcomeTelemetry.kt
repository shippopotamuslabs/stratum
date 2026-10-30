package com.stratum.domain.telemetry

/**
 * Countable outcome fact for the OpenTelemetry outcome stream (STR-242).
 *
 * Outcomes are successes as well as expected failures — the success side is not
 * optional, because failure *rates* need a denominator and Crashlytics only ever
 * sees failures. Exceptional failures never travel here (canon §11: one home per
 * class); the handling layer records those via
 * [TelemetryRouter.recordExceptional][TelemetryRouter] (slice 2).
 *
 * The field names are the stable series vocabulary Grafana queries group by:
 * [useCase] identifies the emitting flow, [OutcomeResult.wireName] the outcome
 * class, [OutcomeResult.ExpectedFailure.reasonCode] the refusal cause.
 * Correlation (session, app version, platform) is stamped by the transport, not
 * the caller, so emitters cannot drift the attribute set.
 */
data class OutcomeTelemetry(
    val useCase: String,
    val result: OutcomeResult,
    val durationMs: Long? = null,
) {
    init {
        require(useCase.matches(USE_CASE_PATTERN)) {
            "useCase must match $USE_CASE_PATTERN_TEXT, was \"$useCase\""
        }
        require(durationMs == null || durationMs >= 0) {
            "durationMs must be non-negative, was $durationMs"
        }
    }

    companion object {
        const val USE_CASE_PROGRAM_GENERATION = "program_generation"
        const val USE_CASE_CLOUD_SYNC = "cloud_sync"

        const val REASON_SYNTHESIS_FAILED = "synthesis_failed"
        const val REASON_SYNC_NOT_STARTED = "sync_not_started"
        const val REASON_SYNC_RESIDUAL_BACKLOG = "sync_residual_backlog"

        private const val USE_CASE_PATTERN_TEXT = "[a-z][a-z0-9_]{0,31}"
        private val USE_CASE_PATTERN = Regex(USE_CASE_PATTERN_TEXT)
    }
}

/**
 * The outcome class of an [OutcomeTelemetry] fact. The class decides the store
 * (canon §11); the reason code only splits a class that already shares one home.
 */
sealed interface OutcomeResult {
    /** Stable series value for this class (`success`, `expected_failure`). */
    val wireName: String

    data object Success : OutcomeResult {
        override val wireName: String = "success"
    }

    /**
     * @param reasonCode stable refusal cause from the emitter's closed vocabulary
     * (e.g. [OutcomeTelemetry.REASON_SYNTHESIS_FAILED]); free-text detail stays
     * out so series cardinality stays bounded.
     */
    data class ExpectedFailure(val reasonCode: String) : OutcomeResult {
        override val wireName: String = "expected_failure"

        init {
            require(reasonCode.matches(REASON_PATTERN)) {
                "reasonCode must match $REASON_PATTERN_TEXT, was \"$reasonCode\""
            }
        }

        companion object {
            private const val REASON_PATTERN_TEXT = "[a-z][a-z0-9_]{0,63}"
            private val REASON_PATTERN = Regex(REASON_PATTERN_TEXT)
        }
    }
}
