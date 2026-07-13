package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.training.ComputePersonalRecordsUseCase
import com.stratum.domain.training.RebuildLocalDerivedTrainingDataUseCase
import com.stratum.domain.training.TrainingLogRepository
import javax.inject.Inject

/**
 * Merges lift **history** into a single survivor lift.
 *
 * - Logged sets for each source get `liftId` = [targetLiftId] and snapshot names rewritten to
 *   the target's catalog name. Other snapshot metadata is left unchanged.
 * - Survivor catalog metadata (muscles, equipment, increments, etc.) is never modified.
 * - Program `workout_lifts` that pointed at sources are re-pointed to the target.
 * - **Seed** sources are **hidden** (`enabled = false`), not deleted — keeps the shared catalog
 *   row for re-enable / reseed integrity (same pattern as import non-recent customs).
 * - **Custom** sources are hard-deleted after re-point.
 * - Structural mutations run via [MergeLiftsPort] in one data-layer transaction.
 * - Personal records are demoted inside that transaction, then recomputed post-commit.
 */
class MergeLiftsUseCase @Inject constructor(
    private val liftRepository: LiftRepository,
    private val trainingLogRepository: TrainingLogRepository,
    private val mergeLiftsPort: MergeLiftsPort,
    private val computePersonalRecords: ComputePersonalRecordsUseCase,
    private val rebuildLocalDerived: RebuildLocalDerivedTrainingDataUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        targetLiftId: LiftId,
        sourceLiftIds: Set<LiftId>,
    ): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to merge lifts") {
            require(sourceLiftIds.isNotEmpty()) { "At least one source lift is required" }
            require(targetLiftId !in sourceLiftIds) { "Target lift cannot also be a source" }

            val allIds = sourceLiftIds + targetLiftId
            val lifts = liftRepository.getLiftsByIds(allIds)
            require(lifts.size == allIds.size) { "One or more selected lifts were not found" }
            val target = lifts.first { it.id == targetLiftId }
            val sources = lifts.filter { it.id in sourceLiftIds }

            val sourcesToHide = sources.filter { !it.isCustom }.map { it.id }.toSet()
            val sourcesToDelete = sources.filter { it.isCustom }.map { it.id }.toSet()

            mergeLiftsPort.applyMerge(
                targetLiftId = targetLiftId,
                targetName = target.name,
                sourcesToHide = sourcesToHide,
                sourcesToDelete = sourcesToDelete,
            )

            recomputePersonalRecords(targetLiftId)

            rebuildLocalDerived().onFailure { error ->
                loggingPort.warning(
                    TAG,
                    "Merge succeeded but derived training rebuild failed: ${error.message}",
                )
            }
        }

    private suspend fun recomputePersonalRecords(targetLiftId: LiftId) {
        val allForTarget = trainingLogRepository.getSetsForLift(targetLiftId)
        if (allForTarget.isEmpty()) return

        val prResult = computePersonalRecords(allForTarget).getOrThrow()
        val toPersist = prResult.updatedIncomingSets + prResult.oldPrSetsToUpdate
        if (toPersist.isNotEmpty()) {
            trainingLogRepository.updateSets(toPersist)
        }
    }

    private companion object {
        const val TAG = "MergeLifts"
    }
}
