package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.program.ProgramsUiPreferences

/**
 * Observes persisted Programs UI preferences. ViewModels use this instead of
 * [ProgramsUiPreferencesRepository] directly (ARCHITECTURE.md §4.4).
 */
class ObserveProgramsUiPreferencesUseCase @Inject constructor(
    private val repository: ProgramsUiPreferencesRepository,
) {
    operator fun invoke(): Flow<ProgramsUiPreferences> = repository.observe()
}

/** Persists which workout card is expanded on the Programs schedule strip. */
class SetExpandedWorkoutIdUseCase @Inject constructor(
    private val repository: ProgramsUiPreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(workoutId: String?): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set expanded workout id") {
            repository.setExpandedWorkoutId(workoutId)
        }

    private companion object {
        const val TAG = "SetExpandedWorkoutId"
    }
}
