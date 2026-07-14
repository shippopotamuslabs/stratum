package com.stratum.core.data.program

import kotlinx.serialization.Serializable

/**
 * Typed DataStore root for the last-expanded Programs workout card.
 * [expandedWorkoutId] is null when no card is expanded.
 */
@Serializable
data class ProgramsUiPreferencesRecord(
    val expandedWorkoutId: String? = null,
)
