package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.training.RebuildLocalDerivedTrainingDataUseCase
import javax.inject.Inject

/**
 * Deletes a lift (STR-131): seed and custom alike, delete means delete — the
 * row is tombstoned via [DeleteLiftPort] in one transaction with the template
 * slot + stash-note sweep. Logged history is never rewritten.
 *
 * After the commit, the derived rebuild runs so no derived/catalog surface
 * keeps presenting the lift; a rebuild failure is logged, not fatal — the
 * delete itself already succeeded.
 */
class DeleteLiftUseCase @Inject constructor(
    private val deleteLiftPort: DeleteLiftPort,
    private val rebuildDerived: RebuildLocalDerivedTrainingDataUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: LiftId): Result<Unit> {
        val tombstoned =
            runCatchingLogged(loggingPort, TAG, "Failed to delete lift") {
                deleteLiftPort.deleteLift(id)
            }.getOrElse { return Result.failure(it) }

        if (tombstoned) {
            runCatchingLogged(loggingPort, TAG, "Derived rebuild after lift delete failed") {
                rebuildDerived()
            }
        }
        return Result.success(Unit)
    }

    private companion object {
        const val TAG = "DeleteLift"
    }
}
