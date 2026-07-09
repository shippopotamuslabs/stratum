package com.stratum.core.data.work

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

/**
 * Pure orchestration for the response-modeling background job. Extracted from
 * [ResponseModelingWorker] so unit tests can exercise the workout-count gate and extract call
 * without spinning up WorkManager.
 *
 * Gate: re-extract when at least [RE_EXTRACT_MIN_COMPLETED_WORKOUTS] completed workouts have been
 * logged since [com.stratum.domain.response.ResponseProfileMetadata.lastExtractedAt]. Calendar time
 * alone does not trigger work (idle months stay quiet; sparse training waits for real signal).
 *
 * Design note (ARCHITECTURE.md §6): the worker lives in `:core:data` because WorkManager is
 * Android-only; this runner keeps the gate + [ExtractResponseProfileUseCase] call injectable and
 * testable on the JVM via fakes.
 */
class ResponseModelingWork @Inject constructor(
    private val extractResponseProfile: ExtractResponseProfileUseCase,
    private val metadataRepository: ResponseProfileMetadataRepository,
    private val trainingLogRepository: TrainingLogRepository,
) {
    sealed interface Outcome {
        /** Fewer than [RE_EXTRACT_MIN_COMPLETED_WORKOUTS] completed workouts since last extract. */
        data object Skipped : Outcome

        /** Full extract completed and was persisted. */
        data object Success : Outcome

        /** Extract threw or returned failure; WorkManager may retry. */
        data class Failed(val error: Throwable) : Outcome
    }

    suspend fun run(): Outcome {
        val lastExtracted = metadataRepository.get()?.lastExtractedAt
        if (lastExtracted != null) {
            val sessions = trainingLogRepository.getAllSessions()
            val newWorkouts = countCompletedWorkoutsSince(sessions, lastExtracted)
            if (newWorkouts < RE_EXTRACT_MIN_COMPLETED_WORKOUTS) {
                return Outcome.Skipped
            }
        }
        // Null metadata (never extracted) or enough new workouts → run full All-history extract.

        return extractResponseProfile(
            provenLiftIds = emptySet(),
            scope = InsightsAnalysisScope.All,
        ).fold(
            onSuccess = { Outcome.Success },
            onFailure = { Outcome.Failed(it) },
        )
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
