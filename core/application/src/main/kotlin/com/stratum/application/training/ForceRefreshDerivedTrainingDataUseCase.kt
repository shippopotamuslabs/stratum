package com.stratum.application.training

import com.stratum.application.response.ResponseModelingSchedulerPort
import javax.inject.Inject
import com.stratum.domain.response.RE_EXTRACT_MIN_COMPLETED_WORKOUTS

/**
 * Outcome of a forced derived-data refresh (STR-243). The stage that failed is
 * identified on [LocalDerivedTrainingStagesResult] and was already logged
 * inside the stages; this outcome carries only the failure kind. Coroutine
 * cancellation is not a branch: it propagates out of the stages.
 */
sealed interface ForceRefreshDerivedTrainingDataResult {
    data object Success : ForceRefreshDerivedTrainingDataResult

    /** A derived-data stage failed (see [LocalDerivedTrainingStagesResult]). */
    data object PersistenceFailed : ForceRefreshDerivedTrainingDataResult
}

/**
 * Recovery path for Settings → Your data → Rebuild progress data.
 *
 * Rebuilds local derived tables and **forces** a full dose-response extract, bypassing the
 * [com.stratum.domain.response.RE_EXTRACT_MIN_COMPLETED_WORKOUTS] gate.
 *
 * Stages:
 *  1–2. [LocalDerivedTrainingStages] — insights (All) + weekly series on Default dispatcher.
 *  3. [ResponseModelingSchedulerPort.enqueueForceExtract] with `replaceExisting = false` so a
 *     re-tap does not cancel an already-running force extract (WorkManager KEEP).
 *
 * Does not open a coach check-in (force-extract path). On-device only.
 *
 * @see RebuildLocalDerivedTrainingDataUseCase gated post-pull path (weekly + gated enqueue only)
 */
fun interface ForceRefreshDerivedTrainingData {
    suspend operator fun invoke(): ForceRefreshDerivedTrainingDataResult
}

class ForceRefreshDerivedTrainingDataUseCase @Inject constructor(
    private val localDerivedStages: LocalDerivedTrainingStages,
    private val responseModelingScheduler: ResponseModelingSchedulerPort,
) : ForceRefreshDerivedTrainingData {
    override suspend operator fun invoke(): ForceRefreshDerivedTrainingDataResult {
        val stagesResult =
            localDerivedStages.run(
                includeInsights = true,
                failPolicy = LocalDerivedTrainingStages.FailPolicy.Throw,
                logTag = TAG,
            )
        if (stagesResult != LocalDerivedTrainingStagesResult.Success) {
            return ForceRefreshDerivedTrainingDataResult.PersistenceFailed
        }
        // Non-blocking: worker skips the 20-workout gate. KEEP on re-tap so we don't cancel
        // an in-flight recovery extract.
        responseModelingScheduler.enqueueForceExtract(replaceExisting = false)
        return ForceRefreshDerivedTrainingDataResult.Success
    }

    private companion object {
        const val TAG = "ForceRefreshDerived"
    }
}
