package com.stratum.application.training

import com.stratum.domain.di.ApplicationScope
import com.stratum.application.healthconnect.HealthConnectSchedulerPort
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
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
    ): Result<Unit> {
        val session =
            trainingLogRepository.getSession(sessionId)
                ?: return Result.failure(
                    IllegalArgumentException("Training session ${sessionId.value} not found"),
                )
        // The completed guard and the start-after-finish rule are model rules (STR-166):
        // retime() enforces both and returns the moved session.
        val updatedSession =
            runCatching { session.retime(newStartedAt = startedAt, newEndedAt = endedAt) }
                .getOrElse { return Result.failure(it) }

        return runCatchingLogged(
            loggingPort = loggingPort,
            tag = TAG,
            message = "Failed to update completed session times",
        ) {
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
