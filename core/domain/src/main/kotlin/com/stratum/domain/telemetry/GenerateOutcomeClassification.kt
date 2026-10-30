package com.stratum.domain.telemetry

import com.stratum.domain.program.generation.synthesis.ProgramSynthesisException

/**
 * Canonical expected-vs-exceptional split for program generation (STR-242).
 *
 * The single home for which generation failures are countable: a translated
 * synthesis failure is expected (adapter-mapped provider condition) and maps
 * to an outcome fact; anything else is exceptional and stays on the
 * Crashlytics path until slice 2 routes it through the router. Terminal
 * boundaries call this instead of hand-rolling their own `is` check, so the
 * class-to-stream policy cannot drift between emitters.
 */
fun classifyGenerateOutcome(error: Throwable): OutcomeTelemetry? =
    if (error is ProgramSynthesisException) {
        OutcomeTelemetry(
            useCase = OutcomeTelemetry.USE_CASE_PROGRAM_GENERATION,
            result = OutcomeResult.ExpectedFailure(OutcomeTelemetry.REASON_SYNTHESIS_FAILED),
        )
    } else {
        null
    }
