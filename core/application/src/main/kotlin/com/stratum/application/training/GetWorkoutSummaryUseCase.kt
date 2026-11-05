package com.stratum.application.training

import com.stratum.domain.lift.Lift
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.toStorageLb
import com.stratum.domain.logging.LoggingPort
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
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of loading a workout summary (STR-243).
 *
 * [SessionNotFound] carries the missing session id and leaves a warning
 * breadcrumb only, never a Crashlytics non-fatal, exactly as the former
 * `expectedFailure` path did. The originating throwable of an operational
 * load failure goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface GetWorkoutSummaryResult {
    data class Success(val summary: WorkoutSummary) : GetWorkoutSummaryResult

    data class SessionNotFound(val sessionId: TrainingSessionId) : GetWorkoutSummaryResult

    /** The summary load failed (repository / store I/O). */
    data object PersistenceFailed : GetWorkoutSummaryResult
}

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
    suspend operator fun invoke(sessionId: TrainingSessionId): GetWorkoutSummaryResult {
        // A missing session is expected (stale deep link), not an operational fault: load all
        // reads in one logged block, then validate outside it so the miss leaves a breadcrumb
        // only, never a Crashlytics non-fatal.
        val reads =
            try {
                SummaryReads(
                    session = trainingLogRepository.getSession(sessionId),
                    sets = trainingLogRepository.getSetsForSession(sessionId),
                    lifts = observeLiftCatalog().first(),
                    program = programRepository.getProgram(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loggingPort.error(TAG, "Failed to load workout summary: ${t.message}", t)
                return GetWorkoutSummaryResult.PersistenceFailed
            }
        val session = reads.session
            ?: run {
                loggingPort.warning(TAG, "Training session ${sessionId.value} not found")
                return GetWorkoutSummaryResult.SessionNotFound(sessionId)
            }
        val loggedSets = reads.sets
        val liftNamesById = reads.lifts.associate { it.id to it.name }
        val program = reads.program
        return try {
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

            GetWorkoutSummaryResult.Success(
                WorkoutSummary(
                    workoutName = workoutName,
                    durationMillis = durationMillis.coerceAtLeast(0),
                    bestSets = bestSetsInWorkoutOrder(loggedSets, workout, liftNamesById),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to load workout summary: ${t.message}", t)
            GetWorkoutSummaryResult.PersistenceFailed
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
