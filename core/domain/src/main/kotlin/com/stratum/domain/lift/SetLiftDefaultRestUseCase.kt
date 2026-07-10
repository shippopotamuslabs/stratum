package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Persists a per-lift default rest override used when the lift is later added or swapped into
 * a workout. Does not mutate existing workout templates. Allowed for catalog and custom lifts
 * (personal programming preference).
 */
class SetLiftDefaultRestUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(id: LiftId, restSeconds: Int): Result<Unit> {
        if (restSeconds < MIN_REST_SECONDS || restSeconds > MAX_REST_SECONDS) {
            return Result.failure(
                IllegalArgumentException(
                    "Rest must be between $MIN_REST_SECONDS and $MAX_REST_SECONDS seconds",
                ),
            )
        }
        repository.observeLifts().first().firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Lift not found"))
        repository.setDefaultRestSeconds(id, restSeconds)
        return Result.success(Unit)
    }

    companion object {
        const val MIN_REST_SECONDS = 0
        const val MAX_REST_SECONDS = 600
    }
}
