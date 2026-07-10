package com.stratum.domain.training

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
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
    suspend operator fun invoke(sessionId: TrainingSessionId): Result<CompletedSessionEditSnapshot> =
        runCatchingLogged(loggingPort, TAG, "Failed to load session for edit") {
            val session =
                trainingLogRepository.getSession(sessionId)
                    ?: error("Training session ${sessionId.value} not found")
            require(session.status == TrainingSessionStatus.COMPLETED) {
                "Training session ${sessionId.value} is not completed"
            }
            val sets =
                trainingLogRepository.getSetsForSession(sessionId)
                    .sortedWith(compareBy({ it.setIndex }, { it.completedAt }))
            require(sets.isNotEmpty()) {
                "Training session ${sessionId.value} has no logged sets"
            }
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

            CompletedSessionEditSnapshot(
                sessionId = sessionId,
                workoutName = workoutName,
                sets = sets,
                loadTypeByLiftId = loadTypeByLiftId,
            )
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
)
