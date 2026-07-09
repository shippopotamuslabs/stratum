package com.stratum.domain.training

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import javax.inject.Inject

/** One completed (or in-progress) session that logged [liftId], with its sets. */
data class LiftHistorySession(
    val sessionId: TrainingSessionId,
    val completedAt: Instant,
    val workoutLabel: String,
    val bestSet: LoggedSet,
    val sets: List<LoggedSet>,
)

/** Personal record + per-session history for a single catalog lift. */
data class LiftHistory(
    val personalRecord: LoggedSet?,
    val sessions: List<LiftHistorySession>,
)

/**
 * Builds the History tab model for lift configuration: current PR (if any) plus sessions
 * ordered newest-first, each with a best set (max e1RM, else weight×reps proxy).
 */
class GetLiftHistoryUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val loggingPort: LoggingPort,
) {
    /**
     * Returns history for [liftId]. On unexpected failure logs and returns empty history so
     * the UI stays up.
     */
    suspend operator fun invoke(liftId: LiftId): LiftHistory =
        runCatching { load(liftId) }
            .onFailure { error ->
                loggingPort.error(TAG, "Failed to load lift history: ${error.message}", error)
            }
            .getOrDefault(LiftHistory(personalRecord = null, sessions = emptyList()))

    private suspend fun load(liftId: LiftId): LiftHistory {
        val pr = trainingLogRepository.getPersonalRecordForLift(liftId)
        val sets = trainingLogRepository.getSetsForLift(liftId)
        if (sets.isEmpty()) {
            return LiftHistory(personalRecord = pr, sessions = emptyList())
        }

        val program = programRepository.getProgram()
        val workoutNames =
            program?.workouts?.associate { workout -> workout.id to workout.name }.orEmpty()

        val sessions =
            sets
                .groupBy { it.sessionId }
                .mapNotNull { (sessionId, sessionSets) ->
                    val session = trainingLogRepository.getSession(sessionId)
                    val ordered =
                        sessionSets.sortedWith(
                            compareBy<LoggedSet> { it.setIndex }.thenBy { it.completedAt },
                        )
                    val best = pickBestSet(ordered) ?: return@mapNotNull null
                    val label =
                        session?.workoutId?.let { workoutNames[it] }
                            ?: ordered.first().liftContext.liftName
                                .takeIf { it.isNotBlank() }
                            ?: session?.sourceApp
                            ?: "Workout"
                    val completedAt =
                        session?.endedAt
                            ?: ordered.maxOf { it.completedAt }
                    LiftHistorySession(
                        sessionId = sessionId,
                        completedAt = completedAt,
                        workoutLabel = label,
                        bestSet = best,
                        sets = ordered,
                    )
                }
                .sortedByDescending { it.completedAt }

        return LiftHistory(personalRecord = pr, sessions = sessions)
    }

    private fun pickBestSet(sets: List<LoggedSet>): LoggedSet? =
        sets.maxWithOrNull(
            compareBy<LoggedSet> { it.estimatedOneRm ?: 0.0 }
                .thenBy { it.weight.amount * it.reps },
        )

    private companion object {
        const val TAG = "GetLiftHistory"
    }
}
