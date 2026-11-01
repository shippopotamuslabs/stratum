package com.stratum.core.infrastructure.repository

import android.content.Context
import com.stratum.core.infrastructure.program.programsUiPreferencesDataStore
import com.stratum.core.infrastructure.program.toDomain
import com.stratum.domain.program.ProgramsUiPreferences
import com.stratum.application.program.ProgramsUiPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [ProgramsUiPreferencesRepository]. Emits defaults (no expanded card) until
 * the first successful read.
 */
@Singleton
class DataStoreProgramsUiPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProgramsUiPreferencesRepository {

    override fun observe(): Flow<ProgramsUiPreferences> =
        context.programsUiPreferencesDataStore.data.map { record -> record.toDomain() }

    override suspend fun setExpandedWorkoutId(workoutId: String?) {
        context.programsUiPreferencesDataStore.updateData { current ->
            current.copy(expandedWorkoutId = workoutId)
        }
    }
}
