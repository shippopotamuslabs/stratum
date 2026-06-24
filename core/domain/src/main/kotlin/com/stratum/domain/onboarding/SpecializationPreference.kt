package com.stratum.domain.onboarding

/**
 * The user's onboarding specialization answers (OS-3). Only muscles explicitly prioritized
 * appear in [levels]; any muscle absent from the map is implicitly
 * [SpecializationLevel.Maintenance] (see [levelFor]). This keeps storage and the "skip"
 * path small — a balanced preference is the empty map.
 *
 * Validation lives in [ValidateSpecializationStepUseCase]; this model is a pure value
 * container (ARCHITECTURE.md §0 — no framework deps).
 */
data class SpecializationPreference(
    val levels: Map<MuscleGroup, SpecializationLevel> = emptyMap(),
) {
    /** The muscles the user wants to specialize in. Drives the OS-3 cap. */
    val specializedMuscles: Set<MuscleGroup>
        get() = levels.filterValues { it == SpecializationLevel.Specialize }.keys

    /**
     * The level for [muscle], defaulting to [SpecializationLevel.Maintenance] when unset.
     * Absence is meaningful: a non-specified muscle is on maintenance.
     */
    fun levelFor(muscle: MuscleGroup): SpecializationLevel =
        levels[muscle] ?: SpecializationLevel.Maintenance

    companion object {
        /** Empty preference — the skip / maintenance-balanced default. */
        val Empty = SpecializationPreference()
    }
}
