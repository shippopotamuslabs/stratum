package com.stratum.domain.program

import kotlinx.coroutines.flow.Flow

/**
 * Observes and updates persisted [ProgramsUiPreferences]. Implementations typically use
 * Jetpack DataStore; callers must not block the main thread on writes.
 */
interface ProgramsUiPreferencesRepository {
    fun observe(): Flow<ProgramsUiPreferences>

    suspend fun setExpandedWorkoutId(workoutId: String?)
}
