package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import com.stratum.domain.lift.Lift

/**
 * Outcome of setting a lift's default rest (STR-243).
 *
 * [InvalidRest] carries the rejected value (the allowed range is
 * [SetLiftDefaultRestUseCase.MIN_REST_SECONDS]..[SetLiftDefaultRestUseCase.MAX_REST_SECONDS]).
 * The originating throwable of a failed read or write rides the
 * [PersistenceFailed] branch as an [OperationalDiagnostic] (legacy tag +
 * rendered message, real throwable as cause); the presentation boundary
 * drains it exactly once through its module's DiagnosticDrain. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface SetLiftDefaultRestResult {
    data object Success : SetLiftDefaultRestResult

    data class InvalidRest(val restSeconds: Int) : SetLiftDefaultRestResult

    data class LiftNotFound(val liftId: LiftId) : SetLiftDefaultRestResult

    /** The catalog read or the rest write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : SetLiftDefaultRestResult
}

/**
 * Persists a per-lift default rest override used when the lift is later added or swapped into
 * a workout. Does not mutate existing workout templates. Allowed for catalog and custom lifts
 * (personal programming preference).
 */
class SetLiftDefaultRestUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(id: LiftId, restSeconds: Int): SetLiftDefaultRestResult {
        if (restSeconds < MIN_REST_SECONDS || restSeconds > MAX_REST_SECONDS) {
            return SetLiftDefaultRestResult.InvalidRest(restSeconds)
        }
        return try {
            repository.observeLifts().first().firstOrNull { it.id == id }
                ?: return SetLiftDefaultRestResult.LiftNotFound(id)
            repository.setDefaultRestSeconds(id, restSeconds)
            SetLiftDefaultRestResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            SetLiftDefaultRestResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set default rest: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    companion object {
        const val TAG = "SetLiftDefaultRest"
        const val MIN_REST_SECONDS = 0
        const val MAX_REST_SECONDS = 600
    }
}
