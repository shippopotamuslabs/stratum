package com.stratum.domain.telemetry

import com.stratum.domain.program.generation.GenerateProgramResult

/**
 * Canonical expected-vs-exceptional split for program generation (STR-242).
 *
 * The single home for which generation failures are countable: a translated
 * synthesis failure is expected (adapter-mapped provider condition) and maps
 * to an outcome fact; anything else is exceptional and stays on the
 * Crashlytics path. Terminal boundaries call this instead of hand-rolling
 * their own branch check, so the branch-to-stream policy cannot drift
 * between emitters.
 *
 * STR-243: classification keys on the [GenerateProgramResult] branches
 * instead of the retired exception types — [GenerateProgramResult.SynthesisRefused]
 * and [GenerateProgramResult.SynthesisFailed] are the expected branches;
 * validation rejections and unexpected operational failures have no outcome
 * home and return null.
 */
fun classifyGenerateOutcome(result: GenerateProgramResult): OutcomeTelemetry? =
    when (result) {
        is GenerateProgramResult.SynthesisRefused,
        is GenerateProgramResult.SynthesisFailed,
        ->
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_PROGRAM_GENERATION,
                result = OutcomeResult.ExpectedFailure(OutcomeTelemetry.REASON_SYNTHESIS_FAILED),
            )
        is GenerateProgramResult.Success,
        is GenerateProgramResult.ValidationRejected,
        is GenerateProgramResult.Unexpected,
        -> null
    }
