package com.stratum.domain.training

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow

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
                completedRestDayWeekdaysThisCycle = emptySet(),
                nextWorkout = null,
                isCycleComplete = false,
            )
        }
        val completedWorkoutIds =
            program.workouts
                .map { it.id }
                .filter { workoutId ->
                    CycleProgressResolver.isWorkoutCompletedThisCycle(program, sessions, workoutId)
                }
                .toSet()
        val completedRestDayWeekdays = sessions
            .filter { session ->
                session.programId == program.id &&
                    session.cycleNumberSnapshot == program.currentCycleNumber &&
                    session.status == TrainingSessionStatus.COMPLETED &&
                    session.kind == TrainingSessionKind.REST_DAY &&
                    session.weekdaySnapshot != null
            }
            .mapNotNull { it.weekdaySnapshot }
            .toSet()
        return CycleProgress(
            program = program,
            activeSession = activeSession,
            completedWorkoutIdsThisCycle = completedWorkoutIds,
            completedRestDayWeekdaysThisCycle = completedRestDayWeekdays,
            nextWorkout = CycleProgressResolver.resolveNextWorkout(program, sessions),
            isCycleComplete = CycleProgressResolver.isCycleComplete(program, sessions),
        )
    }
}
