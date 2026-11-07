package com.stratum.application.program

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.program.ProgramsUiPreferences

/**
 * Outcome of a Programs UI preferences write (STR-243; STR-237 Route (i)).
 *
 * The originating throwable rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates as cancellation.
 */
sealed interface ProgramsUiPreferenceResult {
    data object Success : ProgramsUiPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : ProgramsUiPreferenceResult
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
) {
    suspend operator fun invoke(workoutId: String?): ProgramsUiPreferenceResult =
        try {
            repository.setExpandedWorkoutId(workoutId)
            ProgramsUiPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            ProgramsUiPreferenceResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set expanded workout id: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SetExpandedWorkoutId"
    }
}
