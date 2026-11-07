package com.stratum.application.training

import com.stratum.domain.logging.LoggingPort
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import com.stratum.domain.training.PersonalRecordScoring
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionDisplayName
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.WorkoutSessionDetail
import com.stratum.domain.training.WorkoutSessionLiftDetail
import com.stratum.domain.training.WorkoutSessionSetDetail
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.training.normalizedPartialReps

/**
 * Outcome of loading a workout session detail (STR-243).
 *
 * The expected branches carry the typed facts the former constructed
 * exceptions carried ([SessionNotFound] / [SessionNotCompleted] with the
 * session id and actual status / [NoLoggedSets]); each leaves a warning
 * breadcrumb only, never a Crashlytics non-fatal, exactly as the former
 * `expectedFailure` path did. The originating throwable of an operational
 * load failure goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface GetWorkoutSessionDetailResult {
    data class Success(val detail: WorkoutSessionDetail) : GetWorkoutSessionDetailResult

    data class SessionNotFound(val sessionId: TrainingSessionId) : GetWorkoutSessionDetailResult

    data class SessionNotCompleted(
        val sessionId: TrainingSessionId,
        val status: TrainingSessionStatus,
    ) : GetWorkoutSessionDetailResult

    data class NoLoggedSets(val sessionId: TrainingSessionId) : GetWorkoutSessionDetailResult

    /** The session load failed (repository / store I/O). */
    data object PersistenceFailed : GetWorkoutSessionDetailResult
}

/**
 * Loads a completed training session for the history detail screen: display name (preferring
 * [TrainingSession.workoutNameSnapshot]), duration, set list grouped by lift, PR counts, and
 * muscle volume from logged-set context tags.
 */
class GetWorkoutSessionDetailUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val computeLoggedSessionMuscleVolume: ComputeLoggedSessionMuscleVolumeUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): GetWorkoutSessionDetailResult {
        // Not-found / wrong-status / empty outcomes are expected (stale deep links, mid-edit
        // reads), not operational faults: validate outside the logged blocks so they leave a
        // breadcrumb only and never become Crashlytics non-fatals.
        val loaded =
            try {
                Pair(
                    trainingLogRepository.getSession(sessionId),
                    trainingLogRepository.getSetsForSession(sessionId),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loggingPort.error(TAG, "Failed to load workout session detail: ${t.message}", t)
                return GetWorkoutSessionDetailResult.PersistenceFailed
            }
        val session = loaded.first
            ?: run {
                loggingPort.warning(TAG, "Training session ${sessionId.value} not found")
                return GetWorkoutSessionDetailResult.SessionNotFound(sessionId)
            }
        if (session.status != TrainingSessionStatus.COMPLETED) {
            loggingPort.warning(
                TAG,
                "Training session ${session.id.value} is not completed (status=${session.status})",
            )
            return GetWorkoutSessionDetailResult.SessionNotCompleted(session.id, session.status)
        }
        val loggedSets = loaded.second.sortedWith(compareBy({ it.setIndex }, { it.completedAt }))
        if (loggedSets.isEmpty()) {
            loggingPort.warning(TAG, "Training session ${sessionId.value} has no logged sets")
            return GetWorkoutSessionDetailResult.NoLoggedSets(sessionId)
        }
        return try {
            val completedAt = session.completedAt
            val durationMillis =
                (completedAt.toEpochMilli() - session.startedAt.toEpochMilli()).coerceAtLeast(0)
            val program = programRepository.getProgram()
            val liftFallback =
                loggedSets.firstOrNull()?.liftContext?.liftName?.takeIf { it.isNotBlank() }
                    ?: loggedSets.firstOrNull()?.exerciseNameRaw
            val workoutName =
                TrainingSessionDisplayName.resolve(
                    session = session,
                    program = program,
                    liftOrExerciseFallback = liftFallback,
                )

            val lifts =
                loggedSets
                    .groupBy { set ->
                        set.liftId?.value
                            ?: set.liftContext.liftName.takeIf { it.isNotBlank() }
                            ?: set.exerciseNameRaw
                    }
                    .entries
                    .sortedBy { (_, sets) -> sets.minOf { it.setIndex } }
                    .map { (_, sets) ->
                        val ordered = sets.sortedWith(compareBy({ it.setIndex }, { it.completedAt }))
                        val liftName =
                            ordered.first().liftContext.liftName.takeIf { it.isNotBlank() }
                                ?: ordered.first().exerciseNameRaw
                        val mappedSets =
                            ordered.map { set ->
                                WorkoutSessionSetDetail(
                                    setIndex = set.setIndex,
                                    weight = set.weight,
                                    reps = set.reps,
                                    rir = set.rir,
                                    isPersonalRecord = set.isPersonalRecord,
                                    partialReps = set.normalizedPartialReps,
                                )
                            }
                        val bestLogged =
                            PersonalRecordScoring.bestPersonalRecordSet(ordered)
                                ?: ordered.first()
                        WorkoutSessionLiftDetail(
                            liftName = liftName,
                            sets = mappedSets,
                            bestSet =
                                mappedSets.find { it.setIndex == bestLogged.setIndex }
                                    ?: mappedSets.firstOrNull(),
                        )
                    }

            GetWorkoutSessionDetailResult.Success(
                WorkoutSessionDetail(
                    sessionId = session.id,
                    workoutName = workoutName,
                    startedAt = session.startedAt,
                    endedAt = session.endedAt,
                    completedAt = completedAt,
                    durationMillis = durationMillis,
                    totalSets = loggedSets.size,
                    personalRecordCount = loggedSets.count { it.isPersonalRecord },
                    muscleVolumes = computeLoggedSessionMuscleVolume(loggedSets),
                    lifts = lifts,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to load workout session detail: ${t.message}", t)
            GetWorkoutSessionDetailResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "GetWorkoutSessionDetail"
    }
}
