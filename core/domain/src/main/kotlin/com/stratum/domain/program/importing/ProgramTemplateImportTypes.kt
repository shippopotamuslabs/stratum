package com.stratum.domain.program.importing

/**
 * Type-safe identifier for a program-template import job. The value is generated client-side
 * (UUID) and echoed in the Firestore job path `users/{uid}/programImportJobs/{id}`.
 */
@JvmInline
value class ProgramTemplateImportJobId(val value: String)

/**
 * Provider-agnostic normalized representation of an imported program template, produced by the
 * server's deterministic parser and consumed by the client.
 *
 * Lifts are **name-keyed** (`[ProgramTemplateLift.name]`) — the client resolves them to catalog
 * `LiftId`s via [com.stratum.domain.importing.LiftNameResolverPort], creating custom lifts for any
 * unmatched names. This mirrors how the historical-import pipeline treats `exerciseNameRaw`.
 *
 * Distinct from `com.stratum.domain.program.generation.schema.NormalizedProgram` (which is the
 * deterministic generation output and is already lift-id-resolved).
 *
 * @param programName Display name; null if the source had none (caller assigns a default).
 * @param cycleLength Sessions per full cycle before repeating; null when unknown (defaults to the
 *  workout count on the client). MacroFactor exposes this as "Cycles"; Alpha Progression as the
 *  plan duration in weeks.
 */
data class NormalizedProgramTemplate(
    val programName: String?,
    val cycleLength: Int?,
    val workouts: List<NormalizedProgramTemplateWorkout>,
)

data class NormalizedProgramTemplateWorkout(
    val name: String,
    /** Weekday label from the source if present (e.g. "Monday"); null when the program is day-positioned. */
    val weekday: String? = null,
    val position: Int,
    val lifts: List<NormalizedProgramTemplateLift>,
    /**
     * True for explicit rest days in the cycle (e.g. MacroFactor interleaves Rest rows between
     * training days). A rest day carries no lifts; the resolver uses it to consume a weekday slot
     * when assigning the schedule but does not create a domain `Workout` for it.
     */
    val restDay: Boolean = false,
)

data class NormalizedProgramTemplateLift(
    val name: String,
    val position: Int,
    val sets: List<NormalizedProgramTemplateSet>,
    /**
     * Opaque superset group key shared by lifts in the same superset (e.g. MacroFactor `∈ SSn`).
     * Null for straight lifts. The client maps equal keys to a `WorkoutSupersetGroupId`.
     */
    val supersetGroupKey: String? = null,
)

/**
 * One set in a template lift. All numeric fields are nullable because providers vary in what they
 * capture (Alpha Progression carries only reps; MacroFactor carries type/reps/RIR/rest).
 */
data class NormalizedProgramTemplateSet(
    val position: Int,
    /** Source set-type label (e.g. "Standard Set", "Failure Set", "Myo Set"); null when unspecified. */
    val setTypeLabel: String? = null,
    val repMin: Int?,
    val repMax: Int?,
    val rirGoal: Int?,
    val restSeconds: Int?,
    val isTopSet: Boolean = false,
)
