package com.stratum.application.program

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.program.ProgramsUiPreferences

/**
 * Observes and updates persisted [ProgramsUiPreferences]. Implementations typically use
 * Jetpack DataStore; callers must not block the main thread on writes.
 */
interface ProgramsUiPreferencesRepository {
    fun observe(): Flow<ProgramsUiPreferences>

    suspend fun setExpandedWorkoutId(workoutId: String?)
}
