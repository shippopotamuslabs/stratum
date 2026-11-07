package com.stratum.application.training

import com.stratum.domain.di.ApplicationScope
import com.stratum.application.healthconnect.HealthConnectSchedulerPort
import com.stratum.domain.logging.LoggingPort
import com.stratum.application.response.ResponseModelingSchedulerPort
import com.stratum.application.response.ResponseProfileMetadataRepository
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionStatus

/**
 * Outcome of updating a completed session's times (STR-243).
 *
 * The guard branches carry the typed facts the former constructed exceptions
 * carried ([SessionNotFound] / [SessionNotCompleted] with the session id and
 * actual status / [StartAfterFinish] with both timestamps). The originating
 * throwable of a failed write goes once to the diagnostics channel
 * ([LoggingPort.error]) at the use-case boundary; it never crosses as a
 * payload. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface UpdateCompletedSessionTimesResult {
    data object Success : UpdateCompletedSessionTimesResult

    data class SessionNotFound(val sessionId: TrainingSessionId) : UpdateCompletedSessionTimesResult

    data class SessionNotCompleted(
        val sessionId: TrainingSessionId,
        val status: TrainingSessionStatus,
    ) : UpdateCompletedSessionTimesResult

    data class StartAfterFinish(
        val startedAt: Instant,
        val endedAt: Instant,
    ) : UpdateCompletedSessionTimesResult

    /** The time update failed (repository / store I/O). */
    data object PersistenceFailed : UpdateCompletedSessionTimesResult
}

/**
 * Updates the start and finish timestamps of a **completed** training session.
 * Atomically shifts all [LoggedSet.completedAt] timestamps within the new time window so
 * volume queries, weekly series, and history calendar remain synchronized.
 * Re-enqueues Health Connect sync, triggers dose-response modeling re-extract if applicable,
 * and launches background derived training stages (insights, weekly series).
 */
class UpdateCompletedSessionTimesUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val localDerivedStages: LocalDerivedTrainingStages,
    private val responseProfileMetadataRepository: ResponseProfileMetadataRepository,
    private val responseModelingScheduler: ResponseModelingSchedulerPort,
    private val healthConnectScheduler: HealthConnectSchedulerPort,
    private val loggingPort: LoggingPort,
    @ApplicationScope private val refreshScope: CoroutineScope =
        CoroutineScope(Dispatchers.Main.immediate),
) {

    suspend operator fun invoke(
        sessionId: TrainingSessionId,
        startedAt: Instant,
        endedAt: Instant,
    ): UpdateCompletedSessionTimesResult {
        // The session load sits inside the boundary try (C3 precedent): a throwing
        // load is logged once and returned as PersistenceFailed instead of escaping.
        return try {
            val session =
                trainingLogRepository.getSession(sessionId)
                    ?: return UpdateCompletedSessionTimesResult.SessionNotFound(sessionId)
            // The completed guard and the start-after-finish rule are model rules (STR-166):
            // retime() enforces both as a backstop and returns the moved session; the use case
            // pre-checks both so the expected failures are returned as branches, never thrown.
            if (session.status != TrainingSessionStatus.COMPLETED) {
                return UpdateCompletedSessionTimesResult.SessionNotCompleted(session.id, session.status)
            }
            if (startedAt.isAfter(endedAt)) {
                return UpdateCompletedSessionTimesResult.StartAfterFinish(startedAt, endedAt)
            }
            val updatedSession = session.retime(newStartedAt = startedAt, newEndedAt = endedAt)

            val wasInModel = sessionWasInLastModelingRun(session)

        val currentSets = trainingLogRepository.getSetsForSession(sessionId)
        val shiftedSets =
            if (currentSets.isNotEmpty()) {
                shiftSetTimestamps(
                    sets = currentSets,
                    oldStartedAt = session.startedAt,
                    oldEndedAt = session.completedAt,
                    newStartedAt = startedAt,
                    newEndedAt = endedAt,
                )
            } else {
                emptyList()
            }

        trainingLogRepository.updateSessionAndSets(
            session = updatedSession,
            sets = shiftedSets,
        )

        try {
            healthConnectScheduler.enqueueSync(session.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loggingPort.error(
                TAG,
                "Health Connect sync enqueue failed for ${session.id.value}: ${error.message}",
                error,
            )
        }

        if (wasInModel || sessionWasInLastModelingRun(updatedSession)) {
            responseModelingScheduler.enqueueForceExtract(replaceExisting = true)
        }

            refreshScope.launch {
                localDerivedStages.run(
                    includeInsights = true,
                    failPolicy = LocalDerivedTrainingStages.FailPolicy.LogAndContinue,
                    logTag = TAG,
                )
            }
            UpdateCompletedSessionTimesResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to update completed session times: ${t.message}", t)
            UpdateCompletedSessionTimesResult.PersistenceFailed
        }
    }

    internal fun shiftSetTimestamps(
        sets: List<LoggedSet>,
        oldStartedAt: Instant,
        oldEndedAt: Instant,
        newStartedAt: Instant,
        newEndedAt: Instant,
    ): List<LoggedSet> {
        if (sets.isEmpty()) return emptyList()
        val sortedSets = sets.sortedWith(compareBy({ it.setIndex }, { it.completedAt }))
        val oldStartMillis = oldStartedAt.toEpochMilli()
        val oldEndMillis = oldEndedAt.toEpochMilli()
        val oldDuration = (oldEndMillis - oldStartMillis).coerceAtLeast(0)

        val newStartMillis = newStartedAt.toEpochMilli()
        val newEndMillis = newEndedAt.toEpochMilli()
        val newDuration = (newEndMillis - newStartMillis).coerceAtLeast(0)

        val allTimestampsEqual = sortedSets.map { it.completedAt.toEpochMilli() }.distinct().size <= 1

        return sortedSets.mapIndexed { index, set ->
            val newSetMillis =
                if (oldDuration > 0 && newDuration > 0 && !allTimestampsEqual) {
                    val ratio =
                        ((set.completedAt.toEpochMilli() - oldStartMillis).toDouble() / oldDuration)
                            .coerceIn(0.0, 1.0)
                    (newStartMillis + (ratio * newDuration)).toLong()
                } else if (newDuration > 0 && sortedSets.size > 1) {
                    val ratio = (index.toDouble() / (sortedSets.size - 1)).coerceIn(0.0, 1.0)
                    (newStartMillis + (ratio * newDuration)).toLong()
                } else {
                    val delta = newStartMillis - oldStartMillis
                    (set.completedAt.toEpochMilli() + delta).coerceIn(newStartMillis, newEndMillis)
                }
            set.copy(completedAt = Instant.ofEpochMilli(newSetMillis))
        }
    }

    private suspend fun sessionWasInLastModelingRun(session: TrainingSession): Boolean {
        val lastExtracted = responseProfileMetadataRepository.get()?.lastExtractedAt ?: return false
        val sessionAt = session.completedAt
        return !sessionAt.isAfter(lastExtracted)
    }

    private companion object {
        const val TAG = "UpdateCompletedSessionTimes"
    }
}
