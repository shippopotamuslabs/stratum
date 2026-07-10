package com.stratum.domain.training

import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Builds a [WorkoutSummary] for the workout completion screen. Loads the completed session,
 * its logged sets, and the lift catalog (for human-readable lift names), then computes the
 * duration and the best set per lift (highest weight × reps).
 *
 * PR detection is deferred — the best set is purely "best within this session."
 */
class GetWorkoutSummaryUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): Result<WorkoutSummary> =
        runCatchingLogged(loggingPort, TAG, "Failed to load workout summary") {
        val session = trainingLogRepository.getSession(sessionId)
            ?: error("Training session ${sessionId.value} not found")
        val loggedSets = trainingLogRepository.getSetsForSession(sessionId)
        val lifts = observeLiftCatalog().first()
        val liftNamesById = lifts.associate { it.id to it.name }

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

        val durationMillis = run {
            val end = session.endedAt ?: java.time.Instant.now()
            end.toEpochMilli() - session.startedAt.toEpochMilli()
        }

        val bestSets = loggedSets
            .filter { it.liftId != null }
            .groupBy { it.liftId!! }
            .map { (liftId: LiftId, sets) ->
                val best = sets.maxByOrNull { it.estimatedOneRm ?: it.weight.amount } ?: sets.first()
                LiftBestSet(
                    liftName = liftNamesById[liftId] ?: "Unknown",
                    weight = best.weight.amount,
                    reps = best.reps,
                    rir = best.rir,
                    isPersonalRecord = best.isPersonalRecord,
                    estimatedOneRm = best.estimatedOneRm,
                )
            }
            .sortedBy { it.liftName }

        WorkoutSummary(
            workoutName = workoutName,
            durationMillis = durationMillis.coerceAtLeast(0),
            bestSets = bestSets,
        )
        }

    private companion object {
        const val TAG = "GetWorkoutSummary"
    }
}
