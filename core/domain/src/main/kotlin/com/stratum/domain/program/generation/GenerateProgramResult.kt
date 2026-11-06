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
 * Failure branches carry typed values. The one exception is
 * [GenerateProgramResult.Unexpected]: its domain-owned
 * [GenerationFailureDiagnostic]s carry the real [Throwable] so the outcome
 * consumer can record it at its boundary (STR-237 Special 1). Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface GenerateProgramResult {
    /**
     * Generation produced a program draft (not yet persisted). [synthesis]
     * carries the completed build's facts so the outcome boundary records
     * them exactly once (STR-237 chunk 3).
     */
    data class Success(
        val program: Program,
        val synthesis: GenerationSynthesisSummary? = null,
    ) : GenerateProgramResult

    /**
     * The generated program failed domain validation. Expected: the report is
     * the payload; presentation derives the user-facing copy from it via
     * [validationUserMessage].
     */
    data class ValidationRejected(
        val report: ValidationReport,
        val synthesis: GenerationSynthesisSummary? = null,
    ) : GenerateProgramResult

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
     * An unbounded operational failure inside generation. The branch carries
     * the [kind] plus [diagnostics] holding the real throwable; the outcome
     * consumer records each diagnostic exactly once at its boundary
     * (STR-237 Special 1: the propagation path has no logging or telemetry
     * capability, so the record rides the outcome).
     */
    data class Unexpected(
        val kind: GenerateProgramFailureKind,
        val diagnostics: List<GenerationFailureDiagnostic>,
    ) : GenerateProgramResult
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
