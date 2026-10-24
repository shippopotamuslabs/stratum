package com.stratum.domain.training

import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.response.ResponseModelingSchedulerPort
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Amends a **completed** training session's logged sets (edit / add / delete weight-reps-RIR
 * rows). Recomputes personal records across full lift history so demotions promote the next-best
 * historical set. Conditionally forces dose-response re-extract when the edited session was
 * included in the last modeling run.
 */
class UpdateCompletedSessionSetsUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val localDerivedStages: LocalDerivedTrainingStages,
    private val metadataRepository: ResponseProfileMetadataRepository,
    private val responseModelingScheduler: ResponseModelingSchedulerPort =
        NoOpResponseModelingSchedulerForHistoryEdit,
    private val loggingPort: LoggingPort,
    @ApplicationScope private val refreshScope: CoroutineScope =
        CoroutineScope(Dispatchers.Main.immediate),
) {

    /**
     * Replaces the session's set list with [desiredSets] (full desired state).
     * Sets whose ids already exist are updated; new ids are inserted; missing ids are deleted.
     * Session set changes and PR flag updates land in one
     * [TrainingLogRepository.applySetMutations] transaction.
     */
    suspend operator fun invoke(
        sessionId: TrainingSessionId,
        desiredSets: List<LoggedSet>,
    ): Result<Unit> {
        val session =
            trainingLogRepository.getSession(sessionId)
                ?: return Result.failure(
                    IllegalArgumentException("Training session ${sessionId.value} not found"),
                )
        runCatching { session.requireCompleted() }.getOrElse {
            return Result.failure(it)
        }
        if (desiredSets.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Completed session ${sessionId.value} must retain at least one set"),
            )
        }
        if (desiredSets.any { it.sessionId != sessionId }) {
            return Result.failure(
                IllegalArgumentException("All desired sets must belong to session ${sessionId.value}"),
            )
        }

        return runCatching {
            val currentSets = trainingLogRepository.getSetsForSession(sessionId)
        val currentById = currentSets.associateBy { it.id }
        val desiredById = desiredSets.associateBy { it.id }

        val toDelete = currentById.keys - desiredById.keys
        val insertIds = desiredSets.filter { it.id !in currentById }.map { it.id }.toSet()

        val affectedLiftIds =
            (currentSets + desiredSets)
                .mapNotNull { it.liftId }
                .toSet()

        val inserts = mutableListOf<LoggedSet>()
        val updates = mutableListOf<LoggedSet>()

        // Lifts with catalog ids: merge history, recompute PR flags, write diffs.
        for (liftId in affectedLiftIds) {
            val history = trainingLogRepository.getSetsForLift(liftId)
            val historyById = history.associateBy { it.id }
            val merged = mutableListOf<LoggedSet>()
            for (historical in history) {
                if (historical.id in toDelete) continue
                merged += desiredById[historical.id] ?: historical
            }
            for (desired in desiredSets) {
                if (desired.liftId == liftId && desired.id in insertIds) {
                    merged += desired
                }
            }
            val recomputed = assignPersonalRecords(merged)
            for (set in recomputed) {
                if (set.id in insertIds) {
                    inserts += set
                } else {
                    val prior = historyById[set.id]
                    if (prior == null || prior != set) {
                        updates += set
                    }
                }
            }
        }

        // Session sets without a liftId skip PR math but still need ledger writes.
        for (desired in desiredSets) {
            if (desired.liftId != null) continue
            if (desired.id in insertIds) {
                inserts += desired.copy(isPersonalRecord = false, estimatedOneRm = null)
            } else if (currentById[desired.id] != desired) {
                updates += desired.copy(isPersonalRecord = false, estimatedOneRm = null)
            }
        }

        trainingLogRepository.applySetMutations(
            deleteIds = toDelete,
            inserts = inserts,
            updates = updates.distinctBy { it.id },
        )

        refreshScope.launch {
            localDerivedStages.run(
                includeInsights = true,
                failPolicy = LocalDerivedTrainingStages.FailPolicy.LogAndContinue,
                logTag = TAG,
            )
        }

        if (sessionWasInLastModelingRun(session)) {
            // REPLACE so amended sets always re-enter the model even if a job is pending.
            responseModelingScheduler.enqueueForceExtract(replaceExisting = true)
        }
    }.onFailure { error ->
        loggingPort.error(TAG, "Failed to update completed session sets: ${error.message}", error)
    }
    }

    /**
     * Session was included in the last dose-response extract when modeling metadata exists and the
     * session's completion time is not after [ResponseProfileMetadata.lastExtractedAt].
     * Workouts completed after the watermark have not yet entered a modeling run.
     */
    private suspend fun sessionWasInLastModelingRun(session: TrainingSession): Boolean {
        val lastExtracted = metadataRepository.get()?.lastExtractedAt ?: return false
        val sessionAt = session.completedAt
        return !sessionAt.isAfter(lastExtracted)
    }

    /**
     * Recomputes e1RM (null for zero-load) and marks one PR per (lift, equipment)
     * pair via [PersonalRecordScoring.withPersonalRecordsPerPair].
     */
    internal fun assignPersonalRecords(sets: List<LoggedSet>): List<LoggedSet> =
        PersonalRecordScoring.withPersonalRecordsPerPair(sets)

    private companion object {
        const val TAG = "UpdateCompletedSessionSets"
    }
}

/** Default no-op for JVM unit tests that construct the use case without Hilt. */
private object NoOpResponseModelingSchedulerForHistoryEdit : ResponseModelingSchedulerPort {
    override fun enqueueRefresh() = Unit

    override fun enqueueForceExtract(replaceExisting: Boolean) = Unit
}
