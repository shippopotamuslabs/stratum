package com.stratum.domain.training

import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutId

object CycleProgressResolver {
    fun isWorkoutCompletedThisCycle(
        program: Program,
        sessions: List<TrainingSession>,
        workoutId: WorkoutId,
    ): Boolean =
        completedProgramWorkoutSessions(program, sessions).any { it.workoutId == workoutId }

    fun resolveNextWorkout(
        program: Program,
        sessions: List<TrainingSession>,
    ): Workout? {
        val sorted = program.workouts.sortedBy { it.position }
        val incomplete = sorted.filter { workout ->
            !isWorkoutCompletedThisCycle(program, sessions, workout.id)
        }
        if (incomplete.isEmpty()) return null

        val maxCompletedPosition =
            completedProgramWorkoutSessions(program, sessions)
                .mapNotNull { session -> sorted.find { it.id == session.workoutId }?.position }
                .maxOrNull() ?: -1

        if (maxCompletedPosition < 0) {
            return incomplete.minByOrNull { it.position }
        }

        val candidatesAfterProgress = incomplete.filter { it.position > maxCompletedPosition }
        return when {
            candidatesAfterProgress.isEmpty() -> incomplete.minByOrNull { it.position }
            candidatesAfterProgress.size == 1 -> candidatesAfterProgress.first()
            else -> candidatesAfterProgress.maxByOrNull { it.position }
        }
    }

    fun isCycleComplete(
        program: Program,
        sessions: List<TrainingSession>,
    ): Boolean =
        program.workouts.isNotEmpty() &&
            program.workouts.all { workout ->
                isWorkoutCompletedThisCycle(program, sessions, workout.id)
            }

    fun reconcileCyclePosition(
        program: Program,
        sessions: List<TrainingSession>,
    ): Int =
        completedProgramWorkoutSessions(program, sessions)
            .mapNotNull { it.workoutId }
            .distinct()
            .size

    private fun completedProgramWorkoutSessions(
        program: Program,
        sessions: List<TrainingSession>,
    ): List<TrainingSession> =
        sessions.filter { session ->
            session.programId == program.id &&
                session.cycleNumberSnapshot == program.currentCycleNumber &&
                session.status == TrainingSessionStatus.COMPLETED &&
                session.kind == TrainingSessionKind.PROGRAM_WORKOUT &&
                session.workoutId != null
        }
}
