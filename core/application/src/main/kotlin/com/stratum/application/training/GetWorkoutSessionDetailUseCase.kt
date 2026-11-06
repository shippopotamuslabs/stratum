package com.stratum.application.training

import com.stratum.application.diagnostics.OperationalDiagnostic
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
 * session id and actual status / [NoLoggedSets]); their former warning
 * breadcrumbs are dropped — the branches themselves are the record
 * (STR-237). The originating throwable of an operational load failure rides
 * the [PersistenceFailed] branch as an [OperationalDiagnostic] (legacy tag +
 * rendered message, real throwable as cause); the presentation boundary
 * drains it exactly once through its module's DiagnosticDrain. Coroutine
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
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : GetWorkoutSessionDetailResult
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
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): GetWorkoutSessionDetailResult {
        // Not-found / wrong-status / empty outcomes are expected (stale deep links, mid-edit
        // reads), not operational faults: validate outside the boundary blocks so they
        // return as typed branches and never become Crashlytics non-fatals.
        val loaded =
            try {
                Pair(
                    trainingLogRepository.getSession(sessionId),
                    trainingLogRepository.getSetsForSession(sessionId),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                return GetWorkoutSessionDetailResult.PersistenceFailed(
                    diagnostics = listOf(
                        OperationalDiagnostic(
                            tag = TAG,
                            message = "Failed to load workout session detail: ${t.message}",
                            cause = t,
                        ),
                    ),
                )
            }
        val session = loaded.first
            ?: return GetWorkoutSessionDetailResult.SessionNotFound(sessionId)
        if (session.status != TrainingSessionStatus.COMPLETED) {
            return GetWorkoutSessionDetailResult.SessionNotCompleted(session.id, session.status)
        }
        val loggedSets = loaded.second.sortedWith(compareBy({ it.setIndex }, { it.completedAt }))
        if (loggedSets.isEmpty()) {
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
            GetWorkoutSessionDetailResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to load workout session detail: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "GetWorkoutSessionDetail"
    }
}
