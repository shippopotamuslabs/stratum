package com.stratum.application.training

import com.stratum.application.lift.LiftRepository
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionDisplayName
import com.stratum.domain.training.TrainingSessionStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of loading a completed session for edit (STR-243).
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
sealed interface GetCompletedSessionForEditResult {
    data class Success(val snapshot: CompletedSessionEditSnapshot) : GetCompletedSessionForEditResult

    data class SessionNotFound(val sessionId: TrainingSessionId) : GetCompletedSessionForEditResult

    data class SessionNotCompleted(
        val sessionId: TrainingSessionId,
        val status: TrainingSessionStatus,
    ) : GetCompletedSessionForEditResult

    data class NoLoggedSets(val sessionId: TrainingSessionId) : GetCompletedSessionForEditResult

    /** The session load failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : GetCompletedSessionForEditResult
}

/**
 * Loads a completed session for historical edit: display name, raw [LoggedSet] rows (with ids),
 * and per-lift [LoadType] so the presentation layer can show/hide weight columns without
 * depending on repositories from a ViewModel.
 */
class GetCompletedSessionForEditUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val liftRepository: LiftRepository,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): GetCompletedSessionForEditResult {
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
                return GetCompletedSessionForEditResult.PersistenceFailed(
                    diagnostics = listOf(
                        OperationalDiagnostic(
                            tag = TAG,
                            message = "Failed to load session for edit: ${t.message}",
                            cause = t,
                        ),
                    ),
                )
            }
        val session = loaded.first
            ?: return GetCompletedSessionForEditResult.SessionNotFound(sessionId)
        if (session.status != TrainingSessionStatus.COMPLETED) {
            return GetCompletedSessionForEditResult.SessionNotCompleted(session.id, session.status)
        }
        val sets = loaded.second.sortedWith(compareBy({ it.setIndex }, { it.completedAt }))
        if (sets.isEmpty()) {
            return GetCompletedSessionForEditResult.NoLoggedSets(sessionId)
        }
        return try {
            val program = programRepository.getProgram()
            val liftFallback =
                sets.firstOrNull()?.liftContext?.liftName?.takeIf { it.isNotBlank() }
                    ?: sets.firstOrNull()?.exerciseNameRaw
            val workoutName =
                TrainingSessionDisplayName.resolve(
                    session = session,
                    program = program,
                    liftOrExerciseFallback = liftFallback,
                )
            val liftsById = liftRepository.observeLifts().first().associateBy { it.id }
            val loadTypeByLiftId =
                sets
                    .mapNotNull { it.liftId }
                    .distinct()
                    .associateWith { liftId -> liftsById[liftId]?.loadType ?: LoadType.Weighted }
            val isPlateLoadedByLiftId =
                sets
                    .mapNotNull { it.liftId }
                    .distinct()
                    .associateWith { liftId -> liftsById[liftId]?.isPlateLoaded ?: false }
            val isDoubleSidedByLiftId =
                sets
                    .mapNotNull { it.liftId }
                    .distinct()
                    .associateWith { liftId -> liftsById[liftId]?.isDoubleSidedPlateLoad ?: true }

            GetCompletedSessionForEditResult.Success(
                CompletedSessionEditSnapshot(
                    sessionId = sessionId,
                    workoutName = workoutName,
                    sets = sets,
                    loadTypeByLiftId = loadTypeByLiftId,
                    isPlateLoadedByLiftId = isPlateLoadedByLiftId,
                    isDoubleSidedByLiftId = isDoubleSidedByLiftId,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            GetCompletedSessionForEditResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to load session for edit: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "GetCompletedSessionForEdit"
    }
}

data class CompletedSessionEditSnapshot(
    val sessionId: TrainingSessionId,
    val workoutName: String,
    val sets: List<LoggedSet>,
    val loadTypeByLiftId: Map<LiftId, LoadType>,
    val isPlateLoadedByLiftId: Map<LiftId, Boolean> = emptyMap(),
    val isDoubleSidedByLiftId: Map<LiftId, Boolean> = emptyMap(),
)
