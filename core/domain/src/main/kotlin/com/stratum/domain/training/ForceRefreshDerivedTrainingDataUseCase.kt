package com.stratum.domain.training

import com.stratum.domain.response.ResponseModelingSchedulerPort
import javax.inject.Inject

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
    suspend operator fun invoke(): Result<Unit>
}

class ForceRefreshDerivedTrainingDataUseCase @Inject constructor(
    private val localDerivedStages: LocalDerivedTrainingStages,
    private val responseModelingScheduler: ResponseModelingSchedulerPort,
) : ForceRefreshDerivedTrainingData {
    override suspend operator fun invoke(): Result<Unit> {
        val stagesResult =
            localDerivedStages.run(
                includeInsights = true,
                failPolicy = LocalDerivedTrainingStages.FailPolicy.Throw,
                logTag = TAG,
            )
        if (stagesResult.isFailure) return stagesResult
        // Non-blocking: worker skips the 20-workout gate. KEEP on re-tap so we don't cancel
        // an in-flight recovery extract.
        responseModelingScheduler.enqueueForceExtract(replaceExisting = false)
        return Result.success(Unit)
    }

    private companion object {
        const val TAG = "ForceRefreshDerived"
    }
}
