package com.stratum.domain.training

import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow

class ObserveCycleProgressUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val trainingLogRepository: TrainingLogRepository,
) {
    operator fun invoke(): Flow<CycleProgress> =
        combine(
            programRepository.observeProgram(),
            trainingLogRepository.observeActiveSession(),
        ) { program, activeSession ->
            program to activeSession
        }.flatMapConcat { (program, activeSession) ->
            flow {
                emit(resolveCycleProgress(program, activeSession))
            }
        }

    private suspend fun resolveCycleProgress(
        program: Program?,
        activeSession: TrainingSession?,
    ): CycleProgress {
        if (program == null) {
            return CycleProgress(
                program = null,
                activeSession = activeSession,
                completedWorkoutIdsThisCycle = emptySet(),
                nextWorkout = null,
                isCycleComplete = false,
            )
        }
        val sessions = trainingLogRepository.getAllSessions()
        val completedWorkoutIds =
            program.workouts
                .map { it.id }
                .filter { workoutId ->
                    CycleProgressResolver.isWorkoutCompletedThisCycle(program, sessions, workoutId)
                }
                .toSet()
        return CycleProgress(
            program = program,
            activeSession = activeSession,
            completedWorkoutIdsThisCycle = completedWorkoutIds,
            nextWorkout = CycleProgressResolver.resolveNextWorkout(program, sessions),
            isCycleComplete = CycleProgressResolver.isCycleComplete(program, sessions),
        )
    }
}
