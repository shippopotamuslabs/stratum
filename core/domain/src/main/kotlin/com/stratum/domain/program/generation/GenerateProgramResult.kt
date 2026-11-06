package com.stratum.domain.program.generation

import com.stratum.domain.program.Program
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisRefusal
import com.stratum.domain.program.generation.validation.ValidationReport
import com.stratum.domain.program.generation.validation.ValidationSeverity

/**
 * Outcome of program generation (STR-243).
 *
 * Lives in domain (unlike the per-use-case outcome types in the application
 * layer) because the STR-242 outcome classifier keys its
 * expected-vs-exceptional split on these branches, and domain
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
     * is disabled). Expected — see the STR-242 outcome classifier.
     */
    data class SynthesisRefused(val refusal: ProgramSynthesisRefusal) : GenerateProgramResult

    /**
     * The synthesis adapter reported a translated provider condition (quota,
     * timeout, sign-in, monthly limit, …). [message] is user-safe copy
     * produced at the adapter boundary. Expected — see the STR-242
     * outcome classifier.
     *
     * [diagnostics] carry the real caught
     * [com.stratum.domain.program.generation.synthesis.ProgramSynthesisException]
     * as the diagnostic cause (its own cause chain preserves the vendor
     * error), so the onboarding boundary records the real cause exactly
     * once instead of a constructed exception built from [message]
     * (STR-238, following the STR-237 Special-1 precedent for [Unexpected]).
     * The create-program boundary keeps its ExpectedFailure classification
     * for this branch and makes no error record from the diagnostics.
     * [diagnostics] is non-empty by construction (Thermos round 1): a
     * branch that carried no diagnostic would drain to zero records at
     * the onboarding boundary.
     */
    data class SynthesisFailed(
        val message: String,
        val diagnostics: List<GenerationFailureDiagnostic>,
    ) : GenerateProgramResult {
        init {
            require(diagnostics.isNotEmpty()) {
                "GenerateProgramResult.SynthesisFailed requires at least one diagnostic."
            }
        }
    }

    /**
     * An unbounded operational failure inside generation. The branch carries
     * the [kind] plus [diagnostics] holding the real throwable; the outcome
     * consumer records each diagnostic exactly once at its boundary
     * (STR-237 Special 1: the propagation path has no recording
     * capability, so the record rides the outcome).
     *
     * [synthesis] carries the completed build's facts when the pipeline
     * succeeded but a post-pipeline step failed (STR-237 review round 1),
     * so the boundary records them exactly once just as on [Success];
     * it is null when the failure happened before the pipeline returned.
     */
    data class Unexpected(
        val kind: GenerateProgramFailureKind,
        val diagnostics: List<GenerationFailureDiagnostic>,
        val synthesis: GenerationSynthesisSummary? = null,
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
