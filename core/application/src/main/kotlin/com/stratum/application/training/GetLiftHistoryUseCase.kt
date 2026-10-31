package com.stratum.application.training

import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.PersonalRecordScoring
import com.stratum.domain.program.Workout

/** One completed (or in-progress) session that logged [liftId], with its sets. */
data class LiftHistorySession(
    val sessionId: TrainingSessionId,
    val completedAt: Instant,
    val workoutLabel: String,
    val bestSet: LoggedSet,
    val sets: List<LoggedSet>,
)

/**
 * Personal record + per-session history for a single catalog lift.
 *
 * A load failure degrades to empty content and rides along as [diagnostics]
 * (STR-259) — constructed, never logged, below the boundary; the feature
 * boundary drains them exactly once and they never reach UI state.
 */
data class LiftHistory(
    val personalRecord: LoggedSet?,
    val sessions: List<LiftHistorySession>,
    val diagnostics: List<LiftHistoryDiagnostic> = emptyList(),
)

/**
 * A non-fatal lift-history degradation recorded as data (STR-259).
 *
 * Diagnostics carry what was degraded and why — the identifying context plus the
 * failure as data — and are constructed, never logged, below the boundary.
 * Coroutine cancellation is not a diagnostic: it propagates.
 */
sealed interface LiftHistoryDiagnostic {
    /**
     * The history load failed, so the caller shows empty history. Empty history
     * from a lift with no logged sets is not a failure and carries no diagnostic.
     */
    data class LoadFailed(
        val liftId: LiftId,
        val cause: Throwable,
    ) : LiftHistoryDiagnostic
}

/**
 * Builds the History tab model for lift configuration: current PR (if any) plus sessions
 * ordered newest-first, each with a best set ([PersonalRecordScoring.bestPersonalRecordSet]).
 */
class GetLiftHistoryUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
) {
    /**
     * Returns history for [liftId]. On unexpected failure returns empty history
     * carrying the failure as a diagnostic (STR-259) so the UI stays up —
     * nothing is logged below the boundary; the feature boundary drains
     * [LiftHistory.diagnostics] exactly once. Cancellation propagates.
     */
    suspend operator fun invoke(liftId: LiftId): LiftHistory =
        try {
            load(liftId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            LiftHistory(
                personalRecord = null,
                sessions = emptyList(),
                diagnostics = listOf(LiftHistoryDiagnostic.LoadFailed(liftId, error)),
            )
        }

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
                    val lastSetCompletedAt = ordered.maxOf { it.completedAt }
                    val completedAt = session?.endOr(lastSetCompletedAt) ?: lastSetCompletedAt
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
        PersonalRecordScoring.bestPersonalRecordSet(sets) ?: sets.firstOrNull()
}
