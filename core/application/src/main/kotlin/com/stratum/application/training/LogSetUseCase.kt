package com.stratum.application.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.lift.LiftRepository
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.TrainingSessionStatus
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import com.stratum.domain.training.LogSetCommand
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedLiftContextFactory
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.SetEquipmentSource

/**
 * Outcome of logging a set (STR-243).
 *
 * The session-guard branches carry the seam's typed facts ([SessionNotFound] /
 * [SessionNotInProgress]); [LiftNotFound] carries the missing lift id. The
 * originating throwable of a failed write rides the [PersistenceFailed] branch
 * as an [OperationalDiagnostic] (legacy tag + rendered message, real throwable
 * as cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. The guard branches' former error breadcrumbs are
 * dropped: their facts are the branches themselves (STR-237). Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface LogSetResult {
    data class Success(val loggedSet: LoggedSet) : LogSetResult

    data class SessionNotFound(val sessionId: TrainingSessionId) : LogSetResult

    data class SessionNotInProgress(
        val sessionId: TrainingSessionId,
        val status: TrainingSessionStatus,
    ) : LogSetResult

    data class LiftNotFound(val liftId: LiftId) : LogSetResult

    /** The set write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : LogSetResult
}

class LogSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftRepository: LiftRepository,
    private val programRepository: ProgramRepository,
    private val loggedLiftContextFactory: LoggedLiftContextFactory,
) {
    suspend operator fun invoke(command: LogSetCommand): LogSetResult {
        // Single load via the seam check: a missing / non-live session is returned as a
        // typed branch (never thrown); an operational load failure is captured by the
        // seam callback as a diagnostic with the same legacy line the write envelope
        // below produces and returned on PersistenceFailed.
        // Cancellation from the load propagates as cancellation out of the check.
        var loadDiagnostic: OperationalDiagnostic? = null
        when (val check = trainingLogRepository.checkInProgressSession(command.sessionId) { error ->
            loadDiagnostic =
                OperationalDiagnostic(
                    tag = TAG,
                    message = "Failed to log set: ${error.message}",
                    cause = error,
                )
        }) {
            is InProgressSessionCheck.NotFound ->
                return LogSetResult.SessionNotFound(check.sessionId)
            is InProgressSessionCheck.NotInProgress ->
                return LogSetResult.SessionNotInProgress(check.sessionId, check.status)
            InProgressSessionCheck.LoadFailed ->
                return LogSetResult.PersistenceFailed(
                    diagnostics = listOf(checkNotNull(loadDiagnostic)),
                )
            is InProgressSessionCheck.Ready -> Unit
        }
        return try {
            val lift =
                liftRepository.observeLifts().first().firstOrNull { it.id == command.liftId }
                    ?: return LogSetResult.LiftNotFound(command.liftId)

            val liftContext = resolveLiftContext(command, lift)

            val loggedSet =
                LoggedSet(
                    id = LoggedSetId(UUID.randomUUID().toString()),
                    sessionId = command.sessionId,
                    exerciseNameRaw = lift.name,
                    liftId = command.liftId,
                    equipmentId = when (val source = command.equipment) {
                        SetEquipmentSource.FromLift -> lift.equipmentId
                        is SetEquipmentSource.Explicit -> source.equipmentId
                    },
                    setIndex = command.setIndex,
                    weight = command.weight,
                    reps = command.reps,
                    rir = command.rir,
                    // Raw value in; LoggedSet owns the normalization rule (STR-168).
                    partialReps = command.partialReps,
                    completedAt = command.completedAt,
                    liftContext = liftContext,
                )

            trainingLogRepository.insertSet(loggedSet)
            LogSetResult.Success(loggedSet)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            LogSetResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to log set: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    /**
     * Template lifts resolve progression/set fields from the program.
     * Mid-session adds and ad-hoc empty sessions use ephemeral workout-lift ids that are
     * not on the template until the user opts to keep changes at finalize — fall back to a
     * session-only context that still stamps [LogSetCommand.workoutLiftId] for amend/unlog
     * matching.
     */
    private suspend fun resolveLiftContext(command: LogSetCommand, lift: Lift): LoggedLiftContext {
        val workoutLiftId = command.workoutLiftId
            ?: return loggedLiftContextFactory.fromLiftCatalog(lift, lift.name)

        val program = programRepository.getProgram()
        val workoutLift = program
            ?.workouts
            ?.flatMap { it.workoutLifts }
            ?.firstOrNull { it.id == workoutLiftId }

        return if (workoutLift != null) {
            val templateSet = workoutLift.sets.firstOrNull { it.position == command.setIndex }
            val base = loggedLiftContextFactory.fromLiveTemplate(lift, workoutLift, templateSet)
            // Prefer live slot shape when the session path stamped it. Template lookup by
            // setIndex is wrong after renumber (minis between match backoffs) and would
            // mis-label auto minis as the trailing template row — which then pollutes
            // fatigue slope + post-workout rep-range gates.
            if (command.setType != null) {
                base.copy(
                    setType = command.setType,
                    repMin = command.repMin,
                    repMax = command.repMax,
                    rirGoal = command.rirGoal,
                )
            } else {
                base
            }
        } else {
            // Overlay/ad-hoc lift: default Double matches LiveWorkoutOverlayOps.addLifts.
            loggedLiftContextFactory.fromLiveSessionLift(
                lift = lift,
                workoutLiftId = workoutLiftId,
                progressionScheme = ProgressionScheme.Double,
                setType = command.setType,
                repMin = command.repMin,
                repMax = command.repMax,
                rirGoal = command.rirGoal,
            )
        }
    }

    private companion object {
        const val TAG = "LogSetUseCase"
    }
}
