package com.stratum.domain.program

/**
 * Persisted Programs-screen chrome (expanded workout card, etc.). Defaults leave all cards
 * collapsed until the user expands one.
 */
data class ProgramsUiPreferences(
    /** Workout id of the last expanded schedule card, or null when none is expanded. */
    val expandedWorkoutId: String? = null,
)
