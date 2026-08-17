package com.stratum.core.data.work

import com.stratum.domain.checkin.MarkCheckInPendingUseCase
import com.stratum.domain.importing.InsightsAnalysisScope
import com.stratum.domain.response.ExtractResponseProfileUseCase
import com.stratum.domain.response.RE_EXTRACT_MIN_COMPLETED_WORKOUTS
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionStatus
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Pure orchestration for the response-modeling background job. Extracted from
 * [ResponseModelingWorker] so unit tests can exercise the workout-count gate and extract call
 * without spinning up WorkManager.
 *
 * Gate (normal path): re-extract when at least [RE_EXTRACT_MIN_COMPLETED_WORKOUTS] completed
 * workouts have been logged since [com.stratum.domain.response.ResponseProfileMetadata.lastExtractedAt].
 * Calendar time alone does not trigger work (idle months stay quiet; sparse training waits for
 * real signal).
 *
 * Force path ([forceHistoryInvalidation]): used when a historical session that was already part of
 * the last extract is amended. Skips the workout-count gate so the model re-runs on corrected
 * data. Does **not** open a coach check-in (data correction, not new training signal).
 *
 * On a successful **normal re-extract** (prior [lastExtractedAt] was non-null and the workout gate
 * passed), marks a pending coach check-in via [MarkCheckInPendingUseCase]. First-ever extract
 * and history-invalidation force runs do not open a check-in.
 *
 * Design note (ARCHITECTURE.md §6): the worker lives in `:core:data` because WorkManager is
 * Android-only; this runner keeps the gate + [ExtractResponseProfileUseCase] call injectable and
 * testable on the JVM via fakes.
 */
class ResponseModelingWork @Inject constructor(
    private val extractResponseProfile: ExtractResponseProfileUseCase,
    private val metadataRepository: ResponseProfileMetadataRepository,
    private val trainingLogRepository: TrainingLogRepository,
    private val markCheckInPending: MarkCheckInPendingUseCase,
) {

    sealed interface Outcome {
        /** Fewer than [RE_EXTRACT_MIN_COMPLETED_WORKOUTS] completed workouts since last extract. */
        data object Skipped : Outcome

        /** Full extract completed and was persisted. */
        data object Success : Outcome

        /** Extract threw or returned failure; WorkManager may retry. */
        data class Failed(val error: Throwable) : Outcome
    }

    suspend fun run(forceHistoryInvalidation: Boolean = false): Outcome =
        runCatching {
            val lastExtracted = metadataRepository.get()?.lastExtractedAt
            val isReExtract = lastExtracted != null
            if (!forceHistoryInvalidation && lastExtracted != null) {
                val sessions = trainingLogRepository.getAllSessions()
                val newWorkouts = countCompletedWorkoutsSince(sessions, lastExtracted)
                if (newWorkouts < RE_EXTRACT_MIN_COMPLETED_WORKOUTS) {
                    return@runCatching Outcome.Skipped
                }
            }
            // Null metadata, enough new workouts, or forced history invalidation → full All-history extract.

            extractResponseProfile(
                provenLiftIds = emptySet(),
                scope = InsightsAnalysisScope.All,
            ).fold(
                onSuccess = {
                    if (isReExtract && !forceHistoryInvalidation) {
                        // Extract watermark already advanced; mark pending check-in.
                        // History-invalidation force runs intentionally skip check-in.
                        markCheckInPending(Instant.now())
                    }
                    Outcome.Success
                },
                onFailure = { Outcome.Failed(it) },
            )
        }.getOrElse { error ->
            if (error is CancellationException) {
                throw error
            }
            Outcome.Failed(error)
        }

    companion object {
        /**
         * Completed program/ad-hoc sessions strictly after [since] (by [TrainingSession.endedAt],
         * falling back to [TrainingSession.startedAt]). Rest days do not count.
         */
        fun countCompletedWorkoutsSince(
            sessions: List<TrainingSession>,
            since: Instant,
        ): Int =
            sessions.count { session ->
                session.status == TrainingSessionStatus.COMPLETED &&
                    session.kind != TrainingSessionKind.REST_DAY &&
                    (session.endedAt ?: session.startedAt).isAfter(since)
            }
    }
}
