package com.stratum.application.program

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.program.ProgramsUiPreferences

/**
 * Outcome of a Programs UI preferences write (STR-243).
 *
 * The originating throwable goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates as cancellation.
 */
sealed interface ProgramsUiPreferenceResult {
    data object Success : ProgramsUiPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data object PersistenceFailed : ProgramsUiPreferenceResult
}

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
    suspend operator fun invoke(workoutId: String?): ProgramsUiPreferenceResult =
        try {
            repository.setExpandedWorkoutId(workoutId)
            ProgramsUiPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set expanded workout id: ${t.message}", t)
            ProgramsUiPreferenceResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SetExpandedWorkoutId"
    }
}
