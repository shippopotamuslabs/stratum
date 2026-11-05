package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.training.ComputePersonalRecordsUseCase
import com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase
import com.stratum.application.training.TrainingLogRepository
import com.stratum.application.workout.WorkoutOverlayStore
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.Lift
import com.stratum.domain.program.Program

/**
 * Outcome of merging lifts (STR-243; STR-237 Route (i)).
 *
 * The precondition branches enumerate the invoke guards: no sources, the
 * target named as its own source, and a selection that does not fully
 * resolve. [Success] carries the [MergeLiftsOutcome] (with its §10
 * diagnostics). The originating throwable of a failed merge rides the
 * [PersistenceFailed] branch as an [OperationalDiagnostic] (legacy tag +
 * rendered message, real throwable as cause); the presentation boundary
 * drains it exactly once through its module's DiagnosticDrain. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface MergeLiftsResult {
    data class Success(val outcome: MergeLiftsOutcome) : MergeLiftsResult

    /** No source lifts were supplied. */
    data object NoSourceLifts : MergeLiftsResult

    /** The target lift was also named as a source. */
    data object TargetIsSource : MergeLiftsResult

    /** One or more selected lifts were not found. */
    data object LiftsNotFound : MergeLiftsResult

    /** A repository read or write in the merge failed. */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : MergeLiftsResult
}

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
) {
    suspend operator fun invoke(
        targetLiftId: LiftId,
        sourceLiftIds: Set<LiftId>,
    ): MergeLiftsResult {
        if (sourceLiftIds.isEmpty()) {
            return MergeLiftsResult.NoSourceLifts
        }
        if (targetLiftId in sourceLiftIds) {
            return MergeLiftsResult.TargetIsSource
        }

        val allIds = sourceLiftIds + targetLiftId
        return try {
            val lifts = liftRepository.getLiftsByIds(allIds)
            if (lifts.size != allIds.size) {
                return MergeLiftsResult.LiftsNotFound
            }

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

            val prDiagnostic = recomputePersonalRecords(targetLiftId)

            // A failed rebuild is recorded inside the derived-training
            // stages with its throwable; the merge does not re-record it
            // (STR-237: no duplicate breadcrumb).
            rebuildLocalDerived()
            MergeLiftsResult.Success(MergeLiftsOutcome(diagnostics = listOfNotNull(prDiagnostic)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            MergeLiftsResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to merge lifts: ${t.message}",
                        cause = t,
                    ),
                ),
            )
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

    /**
     * Recomputes PR flags for the survivor's history. A recompute failure does not
     * fail the merge — it is returned as a diagnostic on the outcome (STR-255).
     * Throws from the surrounding reads/writes still propagate and fail the merge.
     */
    private suspend fun recomputePersonalRecords(targetLiftId: LiftId): MergeLiftsDiagnostic? {
        val allForTarget = trainingLogRepository.getSetsForLift(targetLiftId)
        if (allForTarget.isEmpty()) return null

        val prResult = computePersonalRecords(allForTarget)
        val failure = prResult.exceptionOrNull()
        if (failure != null) {
            // Cancellation is not a degraded recompute — it must cancel the merge.
            if (failure is CancellationException) throw failure
            return MergeLiftsDiagnostic.PersonalRecordsNotRecomputed(
                targetLiftId = targetLiftId,
                cause = failure,
            )
        }
        val computed = prResult.getOrThrow()
        val toPersist = computed.updatedIncomingSets + computed.oldPrSetsToUpdate
        if (toPersist.isNotEmpty()) {
            trainingLogRepository.updateSets(toPersist)
        }
        return null
    }

    private companion object {
        const val TAG = "MergeLifts"
    }
}
