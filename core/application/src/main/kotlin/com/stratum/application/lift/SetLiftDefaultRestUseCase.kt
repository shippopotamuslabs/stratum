package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import com.stratum.domain.lift.Lift

/**
 * Outcome of setting a lift's default rest (STR-243).
 *
 * [InvalidRest] carries the rejected value (the allowed range is
 * [SetLiftDefaultRestUseCase.MIN_REST_SECONDS]..[SetLiftDefaultRestUseCase.MAX_REST_SECONDS]).
 * The originating throwable of a failed read or write goes once to the
 * diagnostics channel ([LoggingPort.error]) at the use-case boundary; it
 * never crosses as a payload. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface SetLiftDefaultRestResult {
    data object Success : SetLiftDefaultRestResult

    data class InvalidRest(val restSeconds: Int) : SetLiftDefaultRestResult

    data class LiftNotFound(val liftId: LiftId) : SetLiftDefaultRestResult

    /** The catalog read or the rest write failed (repository / store I/O). */
    data object PersistenceFailed : SetLiftDefaultRestResult
}

/**
 * Persists a per-lift default rest override used when the lift is later added or swapped into
 * a workout. Does not mutate existing workout templates. Allowed for catalog and custom lifts
 * (personal programming preference).
 */
class SetLiftDefaultRestUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
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
            loggingPort.error(TAG, "Failed to set default rest: ${t.message}", t)
            SetLiftDefaultRestResult.PersistenceFailed
        }
    }

    companion object {
        const val TAG = "SetLiftDefaultRest"
        const val MIN_REST_SECONDS = 0
        const val MAX_REST_SECONDS = 600
    }
}
