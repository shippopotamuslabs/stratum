package com.stratum.application.training

import com.stratum.domain.lift.Lift
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.toStorageLb
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.expectedFailure
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.Program
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.Workout
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import com.stratum.domain.training.LiftBestSet
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.PersonalRecordScoring
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionDisplayName
import com.stratum.domain.training.WorkoutSummary
import com.stratum.domain.training.normalizedPartialReps

/**
 * Builds a [WorkoutSummary] for the workout completion screen. Loads the completed session,
 * its logged sets, and the lift catalog (for human-readable lift names), then computes the
 * duration and the best set per lift ([PersonalRecordScoring.bestPersonalRecordSet]).
 * Copies [LoggedSet.isPersonalRecord] from that pick so the recap PR icon cannot miss.
 */
class GetWorkoutSummaryUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): Result<WorkoutSummary> {
        // A missing session is expected (stale deep link), not an operational fault: load all
        // reads in one logged block, then validate outside it so the miss leaves a breadcrumb
        // only, never a Crashlytics non-fatal.
        val reads =
            runCatchingLogged(loggingPort, TAG, "Failed to load workout summary") {
                SummaryReads(
                    session = trainingLogRepository.getSession(sessionId),
                    sets = trainingLogRepository.getSetsForSession(sessionId),
                    lifts = observeLiftCatalog().first(),
                    program = programRepository.getProgram(),
                )
            }.getOrElse { return Result.failure(it) }
        val session = reads.session
            ?: return loggingPort.expectedFailure(
                TAG,
                IllegalStateException("Training session ${sessionId.value} not found"),
            )
        val loggedSets = reads.sets
        val liftNamesById = reads.lifts.associate { it.id to it.name }
        val program = reads.program
        return runCatchingLogged(loggingPort, TAG, "Failed to load workout summary") {
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
                val end = session.liveEnd()
                end.toEpochMilli() - session.startedAt.toEpochMilli()
            }

            val workout = session.workoutId?.let { workoutId ->
                program?.workouts?.firstOrNull { it.id == workoutId }
            }

            WorkoutSummary(
                workoutName = workoutName,
                durationMillis = durationMillis.coerceAtLeast(0),
                bestSets = bestSetsInWorkoutOrder(loggedSets, workout, liftNamesById),
            )
        }
    }

    private data class SummaryReads(
        val session: TrainingSession?,
        val sets: List<LoggedSet>,
        val lifts: List<Lift>,
        val program: Program?,
    )

    /**
     * Emit best-set rows in program workout-lift order, then any orphans (no matching
     * template row) ordered by first completion time.
     */
    private fun bestSetsInWorkoutOrder(
        loggedSets: List<LoggedSet>,
        workout: Workout?,
        liftNamesById: Map<LiftId, String>,
    ): List<LiftBestSet> {
        // Prefer workoutLiftId so the same catalog lift twice stays distinct.
        val groups = loggedSets
            .filter { it.liftId != null }
            .groupBy { set ->
                set.liftContext.workoutLiftId?.value ?: set.liftId!!.value
            }
            .toMutableMap()

        fun bestOf(sets: List<LoggedSet>): LiftBestSet {
            val liftId = sets.first().liftId!!
            val best =
                PersonalRecordScoring.bestPersonalRecordSet(sets) ?: sets.first()
            return LiftBestSet(
                liftName = liftNamesById[liftId] ?: "Unknown",
                weight = best.weight.toStorageLb(),
                reps = best.reps,
                rir = best.rir,
                isPersonalRecord = best.isPersonalRecord,
                estimatedOneRm =
                    best.estimatedOneRm
                        ?: PersonalRecordScoring.estimatedOneRmLb(
                            best.weight,
                            best.reps,
                            best.rir,
                        ),
                partialReps = best.normalizedPartialReps,
            )
        }

        return buildList {
            for (wl in workout?.workoutLifts.orEmpty().sortedBy { it.position }) {
                val sets = groups.remove(wl.id.value)
                    // Fallback for logs that never stamped workoutLiftId.
                    ?: groups.remove(wl.liftId.value)
                if (sets != null) add(bestOf(sets))
            }
            groups.values
                .sortedWith(
                    compareBy(
                        { sets -> sets.minOf { it.completedAt } },
                        { sets ->
                            val liftId = sets.first().liftId!!
                            liftNamesById[liftId] ?: sets.first().exerciseNameRaw
                        },
                    ),
                )
                .forEach { add(bestOf(it)) }
        }
    }

    private companion object {
        const val TAG = "GetWorkoutSummary"
    }
}
