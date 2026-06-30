package com.stratum.domain.onboarding

/**
 * How strongly a [MuscleGroup] is prioritized in the generated program.
 *
 * - [Maintenance] — target MAV (productive hypertrophy volume) for muscles not explicitly
 *   specialized. This is the **implicit default** for any muscle not explicitly set in a
 *   [SpecializationPreference] (absence == Maintenance). Maintenance does not mean minimum
 *   MEV — muscles trained on their day still receive a full hypertrophy dose.
 * - [Specialize] — push volume toward the upper productive range (MAV → MRV). Capped at
 *   [ValidateSpecializationStepUseCase.MAX_SPECIALIZE] selections.
 * - [Omit] — drop the muscle entirely. Reserved for OS-6 generation tuning; not surfaced in
 *   the OS-3 selector UI.
 *
 * Grounded in Renaissance Periodization's volume-landmark framework: specialization boosts
 * 1–3 focus muscles toward MRV while other trained muscles stay at MAV. Maintenance is the
 * natural state of any non-prioritized muscle, hence the implicit default.
 */
enum class SpecializationLevel {
    Specialize,
    Maintenance,
    /** Bare minimum weekly volume to maintain muscle size (MV landmark), below MAV hypertrophy dose. */
    MinimumVolume,
    Omit,
    ;
}
