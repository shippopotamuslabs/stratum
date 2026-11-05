package com.stratum.application.training

import com.stratum.application.lift.LiftRepository
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
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
 * session id and actual status / [NoLoggedSets]); each leaves a warning
 * breadcrumb only, never a Crashlytics non-fatal, exactly as the former
 * `expectedFailure` path did. The originating throwable of an operational
 * load failure goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine
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
    data object PersistenceFailed : GetCompletedSessionForEditResult
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
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): GetCompletedSessionForEditResult {
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
                loggingPort.error(TAG, "Failed to load session for edit: ${t.message}", t)
                return GetCompletedSessionForEditResult.PersistenceFailed
            }
        val session = loaded.first
            ?: run {
                loggingPort.warning(TAG, "Training session ${sessionId.value} not found")
                return GetCompletedSessionForEditResult.SessionNotFound(sessionId)
            }
        if (session.status != TrainingSessionStatus.COMPLETED) {
            loggingPort.warning(
                TAG,
                "Training session ${session.id.value} is not completed (status=${session.status})",
            )
            return GetCompletedSessionForEditResult.SessionNotCompleted(session.id, session.status)
        }
        val sets = loaded.second.sortedWith(compareBy({ it.setIndex }, { it.completedAt }))
        if (sets.isEmpty()) {
            loggingPort.warning(TAG, "Training session ${sessionId.value} has no logged sets")
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
            loggingPort.error(TAG, "Failed to load session for edit: ${t.message}", t)
            GetCompletedSessionForEditResult.PersistenceFailed
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
