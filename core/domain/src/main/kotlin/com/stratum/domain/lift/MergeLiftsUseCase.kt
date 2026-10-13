package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.training.ComputePersonalRecordsUseCase
import com.stratum.domain.training.RebuildLocalDerivedTrainingDataUseCase
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.workout.WorkoutOverlayStore
import javax.inject.Inject

/**
 * Merges lift **history** into a single survivor lift.
 *
 * - Logged sets for each source get `liftId` = [targetLiftId] and snapshot names rewritten to
 *   the target's catalog name. Other snapshot metadata is left unchanged.
 * - Survivor catalog metadata (muscles, equipment, increments, etc.) is never modified.
 * - Program `workout_lifts` that pointed at sources are re-pointed to the target. The same
 *   catalog lift may appear more than once in a workout after re-point (no slot collapse).
 * - **Seed** sources are **tombstoned** ([Lift.isDeleted], STR-131), never merely
 *   disabled — merged away means deleted, same as a Gym delete. The row is kept
 *   so the shared catalog and any retained references keep resolving.
 * - **Custom** sources are hard-deleted after re-point.
 * - Active-session overlay catalog ids (swaps / mid-session adds) are remapped to the survivor.
 * - Structural mutations run via [MergeLiftsPort] in one data-layer transaction.
 * - Personal records are demoted inside that transaction, then recomputed post-commit.
 */
class MergeLiftsUseCase @Inject constructor(
    private val liftRepository: LiftRepository,
    private val trainingLogRepository: TrainingLogRepository,
    private val mergeLiftsPort: MergeLiftsPort,
    private val computePersonalRecords: ComputePersonalRecordsUseCase,
    private val rebuildLocalDerived: RebuildLocalDerivedTrainingDataUseCase,
    private val workoutOverlayStore: WorkoutOverlayStore,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        targetLiftId: LiftId,
        sourceLiftIds: Set<LiftId>,
    ): Result<Unit> {
        if (sourceLiftIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one source lift is required"))
        }
        if (targetLiftId in sourceLiftIds) {
            return Result.failure(IllegalArgumentException("Target lift cannot also be a source"))
        }

        val allIds = sourceLiftIds + targetLiftId
        val lifts = liftRepository.getLiftsByIds(allIds)
        if (lifts.size != allIds.size) {
            return Result.failure(IllegalArgumentException("One or more selected lifts were not found"))
        }

        return runCatchingLogged(loggingPort, TAG, "Failed to merge lifts") {
            val target = lifts.first { it.id == targetLiftId }
            val sources = lifts.filter { it.id in sourceLiftIds }

            val sourcesToTombstone = sources.filter { !it.isCustom }.map { it.id }.toSet()
            val sourcesToDelete = sources.filter { it.isCustom }.map { it.id }.toSet()

            mergeLiftsPort.applyMerge(
                targetLiftId = targetLiftId,
                targetName = target.name,
                sourcesToTombstone = sourcesToTombstone,
                sourcesToDelete = sourcesToDelete,
            )

            remapActiveSessionOverlay(
                sourceLiftIds = sourceLiftIds,
                targetLiftId = targetLiftId,
            )

            recomputePersonalRecords(targetLiftId)

            rebuildLocalDerived().onFailure { error ->
                loggingPort.warning(
                    TAG,
                    "Merge succeeded but derived training rebuild failed: ${error.message}",
                )
            }
        }
    }

    private suspend fun remapActiveSessionOverlay(
        sourceLiftIds: Set<LiftId>,
        targetLiftId: LiftId,
    ) {
        val session = trainingLogRepository.getActiveSession() ?: return
        val overlay = workoutOverlayStore.load(session.id) ?: return
        val remapped = overlay.remapCatalogLiftIds(sourceLiftIds, targetLiftId)
        if (remapped !== overlay) {
            workoutOverlayStore.save(session.id, remapped)
        }
    }

    private suspend fun recomputePersonalRecords(targetLiftId: LiftId) {
        val allForTarget = trainingLogRepository.getSetsForLift(targetLiftId)
        if (allForTarget.isEmpty()) return

        val prResult = computePersonalRecords(allForTarget).getOrElse { return }
        val toPersist = prResult.updatedIncomingSets + prResult.oldPrSetsToUpdate
        if (toPersist.isNotEmpty()) {
            trainingLogRepository.updateSets(toPersist)
        }
    }

    private companion object {
        const val TAG = "MergeLifts"
    }
}
