package com.stratum.domain.program.generation

import com.stratum.domain.program.Program
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisRefusal
import com.stratum.domain.program.generation.validation.ValidationReport
import com.stratum.domain.program.generation.validation.ValidationSeverity

/**
 * Outcome of program generation (STR-243).
 *
 * Lives in domain (unlike the per-use-case outcome types in the application
 * layer) because [com.stratum.domain.telemetry.classifyGenerateOutcome] keys
 * the STR-242 expected-vs-exceptional split on these branches, and domain
 * cannot import an application type.
 *
 * Failure branches carry typed values only — never a [Throwable]. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface GenerateProgramResult {
    /** Generation produced a program draft (not yet persisted). */
    data class Success(val program: Program) : GenerateProgramResult

    /**
     * The generated program failed domain validation. Expected: the report is
     * the payload; presentation derives the user-facing copy from it via
     * [validationUserMessage].
     */
    data class ValidationRejected(val report: ValidationReport) : GenerateProgramResult

    /**
     * Synthesis was refused before any solver work ran (e.g. the feature gate
     * is disabled). Expected — see [com.stratum.domain.telemetry.classifyGenerateOutcome].
     */
    data class SynthesisRefused(val refusal: ProgramSynthesisRefusal) : GenerateProgramResult

    /**
     * The synthesis adapter reported a translated provider condition (quota,
     * timeout, sign-in, monthly limit, …). [message] is user-safe copy
     * produced at the adapter boundary. Expected — see
     * [com.stratum.domain.telemetry.classifyGenerateOutcome].
     */
    data class SynthesisFailed(val message: String) : GenerateProgramResult

    /**
     * An unbounded operational failure inside generation. This branch carries
     * only the [kind]; the outcome consumer records the failure exactly once
     * at its boundary (STR-237: the propagation path has no logging or
     * telemetry capability).
     */
    data class Unexpected(val kind: GenerateProgramFailureKind) : GenerateProgramResult
}

/** Kinds for [GenerateProgramResult.Unexpected]; the set is deliberately unbounded. */
enum class GenerateProgramFailureKind {
    /** Any operational failure that is not a translated synthesis condition. */
    OperationalFailure,
}

/**
 * User-facing copy for a [GenerateProgramResult.ValidationRejected] report:
 * the error-severity issue messages joined with `"; "` — the exact derivation
 * the retired failure-value exception used, so visible copy does not change.
 */
fun ValidationReport.validationUserMessage(): String =
    issues
        .filter { it.severity == ValidationSeverity.Error }
        .joinToString("; ") { it.message }
