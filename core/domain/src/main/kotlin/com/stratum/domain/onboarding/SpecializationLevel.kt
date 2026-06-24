package com.stratum.domain.onboarding

/**
 * How strongly a [MuscleGroup] is prioritized in the generated program.
 *
 * - [Specialize] — push volume toward MRV (a focus muscle). Capped at
 *   [ValidateSpecializationStepUseCase.MAX_SPECIALIZE] selections.
 * - [Maintenance] — keep at maintenance volume. This is the **implicit default** for any
 *   muscle not explicitly set in a [SpecializationPreference] (absence == Maintenance), so
 *   a balanced/maintenance program needs no explicit rows.
 * - [Omit] — drop the muscle entirely. Reserved for OS-6 generation tuning; not surfaced in
 *   the OS-3 selector UI.
 *
 * Grounded in Renaissance Periodization's volume-landmark framework: specialization blocks
 * push 1–3 muscles toward MRV while the rest sit at MEV/MV. Maintenance is the natural state
 * of any non-prioritized muscle, hence the implicit default.
 */
enum class SpecializationLevel {
    Specialize,
    Maintenance,
    Omit,
    ;
}
