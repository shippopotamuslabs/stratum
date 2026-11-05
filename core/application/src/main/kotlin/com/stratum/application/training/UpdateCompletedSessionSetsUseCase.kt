package com.stratum.application.training

import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.LoggingPort
import com.stratum.application.response.ResponseModelingSchedulerPort
import com.stratum.application.response.ResponseProfileMetadataRepository
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.PersonalRecordScoring
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionStatus
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.response.ResponseProfileMetadata

/**
 * Outcome of updating a completed session's sets (STR-243).
 *
 * The guard branches carry the typed facts the former constructed exceptions
 * carried ([SessionNotFound] / [SessionNotCompleted] with the session id and
 * actual status / [MustRetainAtLeastOneSet] / [SetsBelongToAnotherSession]).
 * The originating throwable of a failed write goes once to the diagnostics
 * channel ([LoggingPort.error]) at the use-case boundary; it never crosses
 * as a payload. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface UpdateCompletedSessionSetsResult {
    data object Success : UpdateCompletedSessionSetsResult

    data class SessionNotFound(val sessionId: TrainingSessionId) : UpdateCompletedSessionSetsResult

    data class SessionNotCompleted(
        val sessionId: TrainingSessionId,
        val status: TrainingSessionStatus,
    ) : UpdateCompletedSessionSetsResult

    data class MustRetainAtLeastOneSet(val sessionId: TrainingSessionId) :
        UpdateCompletedSessionSetsResult

    data class SetsBelongToAnotherSession(val sessionId: TrainingSessionId) :
        UpdateCompletedSessionSetsResult

    /** The set update failed (repository / store I/O). */
    data object PersistenceFailed : UpdateCompletedSessionSetsResult
}

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
    ): UpdateCompletedSessionSetsResult {
        // The session load sits inside the boundary try (C3 precedent): a throwing
        // load is logged once and returned as PersistenceFailed instead of escaping.
        return try {
            val session =
                trainingLogRepository.getSession(sessionId)
                    ?: return UpdateCompletedSessionSetsResult.SessionNotFound(sessionId)
            if (session.status != TrainingSessionStatus.COMPLETED) {
                return UpdateCompletedSessionSetsResult.SessionNotCompleted(session.id, session.status)
            }
            if (desiredSets.isEmpty()) {
                return UpdateCompletedSessionSetsResult.MustRetainAtLeastOneSet(sessionId)
            }
            if (desiredSets.any { it.sessionId != sessionId }) {
                return UpdateCompletedSessionSetsResult.SetsBelongToAnotherSession(sessionId)
            }

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
            UpdateCompletedSessionSetsResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to update completed session sets: ${t.message}", t)
            UpdateCompletedSessionSetsResult.PersistenceFailed
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
