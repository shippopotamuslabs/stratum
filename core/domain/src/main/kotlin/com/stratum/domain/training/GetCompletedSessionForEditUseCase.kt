package com.stratum.domain.training

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.isDoubleSidedPlateLoad
import com.stratum.domain.lift.isPlateLoaded
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.expectedFailure
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

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
    suspend operator fun invoke(sessionId: TrainingSessionId): Result<CompletedSessionEditSnapshot> {
        // Not-found / wrong-status / empty outcomes are expected (stale deep links, mid-edit
        // reads), not operational faults: validate outside the logged block so they leave a
        // breadcrumb only and never become Crashlytics non-fatals.
        val loaded =
            runCatchingLogged(loggingPort, TAG, "Failed to load session for edit") {
                Pair(
                    trainingLogRepository.getSession(sessionId),
                    trainingLogRepository.getSetsForSession(sessionId),
                )
            }.getOrElse { return Result.failure(it) }
        val session = loaded.first
            ?: return loggingPort.expectedFailure(
                TAG,
                IllegalStateException("Training session ${sessionId.value} not found"),
            )
        if (session.status != TrainingSessionStatus.COMPLETED) {
            return loggingPort.expectedFailure(
                TAG,
                IllegalArgumentException("Training session ${sessionId.value} is not completed"),
            )
        }
        val sets = loaded.second.sortedWith(compareBy({ it.setIndex }, { it.completedAt }))
        if (sets.isEmpty()) {
            return loggingPort.expectedFailure(
                TAG,
                IllegalArgumentException("Training session ${sessionId.value} has no logged sets"),
            )
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to load session for edit") {
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

            CompletedSessionEditSnapshot(
                sessionId = sessionId,
                workoutName = workoutName,
                sets = sets,
                loadTypeByLiftId = loadTypeByLiftId,
                isPlateLoadedByLiftId = isPlateLoadedByLiftId,
                isDoubleSidedByLiftId = isDoubleSidedByLiftId,
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
