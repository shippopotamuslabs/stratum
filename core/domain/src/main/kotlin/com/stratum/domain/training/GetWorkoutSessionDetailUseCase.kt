package com.stratum.domain.training

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject

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
    suspend operator fun invoke(sessionId: TrainingSessionId): Result<WorkoutSessionDetail> =
        runCatchingLogged(loggingPort, TAG, "Failed to load workout session detail") {
            val session = trainingLogRepository.getSession(sessionId)
                ?: error("Training session ${sessionId.value} not found")
            require(session.status == TrainingSessionStatus.COMPLETED) {
                "Training session ${sessionId.value} is not completed"
            }
            val loggedSets = trainingLogRepository.getSetsForSession(sessionId)
                .sortedWith(compareBy({ it.setIndex }, { it.completedAt }))
            require(loggedSets.isNotEmpty()) {
                "Training session ${sessionId.value} has no logged sets"
            }

            val completedAt = session.endedAt ?: session.startedAt
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
                                    partialReps = set.partialReps.normalizedPartialReps(),
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
            )
        }

    private companion object {
        const val TAG = "GetWorkoutSessionDetail"
    }
}
