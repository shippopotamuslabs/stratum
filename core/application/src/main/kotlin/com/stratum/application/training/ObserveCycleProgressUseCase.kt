package com.stratum.application.training

import com.stratum.domain.program.Program
import com.stratum.application.program.ProgramRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import com.stratum.domain.training.CycleProgress
import com.stratum.domain.training.CycleProgressResolver
import com.stratum.domain.training.TrainingSession

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCycleProgressUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val trainingLogRepository: TrainingLogRepository,
) {
    operator fun invoke(): Flow<CycleProgress> =
        combine(
            programRepository.observeProgram(),
            trainingLogRepository.observeActiveSession(),
            trainingLogRepository.observeAllSessions(),
        ) { program, activeSession, sessions ->
            Triple(program, activeSession, sessions)
        }.flatMapConcat { (program, activeSession, sessions) ->
            flow {
                emit(resolveCycleProgress(program, activeSession, sessions))
            }
        }

    private fun resolveCycleProgress(
        program: Program?,
        activeSession: TrainingSession?,
        sessions: List<TrainingSession>,
    ): CycleProgress {
        if (program == null) {
            return CycleProgress(
                program = null,
                activeSession = activeSession,
                completedWorkoutIdsThisCycle = emptySet(),
                completedRestSlotIndicesThisCycle = emptySet(),
                nextWorkout = null,
                isCycleComplete = false,
                cyclePosition = 0,
            )
        }
        val slotProgress = CycleProgressResolver.reconcileSlotProgress(program, sessions)
        val completedWorkoutIds =
            program.workouts
                .map { it.id }
                .filter { workoutId ->
                    CycleProgressResolver.isWorkoutCompletedThisCycle(program, sessions, workoutId)
                }
                .toSet()
        // Ledger-only: auto mid-cycle rests are written as REST_DAY sessions at complete/skip.
        val completedRestSlots =
            CycleProgressResolver.completedRestSlotIndicesThisCycle(program, sessions)
        return CycleProgress(
            program = program,
            activeSession = activeSession,
            completedWorkoutIdsThisCycle = completedWorkoutIds,
            completedRestSlotIndicesThisCycle = completedRestSlots,
            nextWorkout = CycleProgressResolver.resolveNextWorkout(program, sessions),
            isCycleComplete = slotProgress.isComplete,
            cyclePosition = if (slotProgress.isComplete) 0 else slotProgress.cyclePosition,
        )
    }
}
