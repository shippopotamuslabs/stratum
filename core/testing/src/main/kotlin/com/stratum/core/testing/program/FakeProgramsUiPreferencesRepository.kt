package com.stratum.core.testing.program

import com.stratum.domain.program.ProgramsUiPreferences
import com.stratum.application.program.ProgramsUiPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [ProgramsUiPreferencesRepository] for ViewModel tests. */
class FakeProgramsUiPreferencesRepository(
    initial: ProgramsUiPreferences = ProgramsUiPreferences(),
) : ProgramsUiPreferencesRepository {

    private val preferences = MutableStateFlow(initial)

    val current: ProgramsUiPreferences
        get() = preferences.value

    fun seed(value: ProgramsUiPreferences) {
        preferences.value = value
    }

    fun seed(expandedWorkoutId: String?) {
        preferences.value = preferences.value.copy(expandedWorkoutId = expandedWorkoutId)
    }

    override fun observe(): Flow<ProgramsUiPreferences> = preferences.asStateFlow()

    override suspend fun setExpandedWorkoutId(workoutId: String?) {
        preferences.value = preferences.value.copy(expandedWorkoutId = workoutId)
    }
}
