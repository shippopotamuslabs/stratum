package com.stratum.domain.training

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ActiveSessionOrphanTest {
    private val program = WorkoutLoggingFixtures.twoWorkoutProgram()

    @Test
    @DisplayName("Bound program workout is not orphaned when template still exists")
    fun boundWorkoutNotOrphaned() {
        val session = WorkoutLoggingFixtures.inProgressSession()
        assertFalse(session.isOrphanedAgainstActiveProgram(program))
    }

    @Test
    @DisplayName("Program workout is orphaned when its template was deleted")
    fun deletedWorkoutIsOrphaned() {
        val session =
            WorkoutLoggingFixtures.inProgressSession(
                workoutId = WorkoutId("deleted-workout"),
            )
        assertTrue(session.isOrphanedAgainstActiveProgram(program))
    }

    @Test
    @DisplayName("Program workout is orphaned when a different program is active")
    fun differentProgramIsOrphaned() {
        val session = WorkoutLoggingFixtures.inProgressSession()
        val other =
            program.copy(
                id = ProgramId("other-program"),
                workouts =
                    program.workouts.map { workout ->
                        workout.copy(programId = ProgramId("other-program"))
                    },
            )
        assertTrue(session.isOrphanedAgainstActiveProgram(other))
    }

    @Test
    @DisplayName("Program workout is orphaned when there is no active program")
    fun noProgramIsOrphaned() {
        val session = WorkoutLoggingFixtures.inProgressSession()
        assertTrue(session.isOrphanedAgainstActiveProgram(null))
    }

    @Test
    @DisplayName("Ad-hoc session is not orphaned against the same active program")
    fun adHocSameProgramNotOrphaned() {
        val session =
            WorkoutLoggingFixtures.inProgressSession(
                workoutId = null,
                kind = TrainingSessionKind.AD_HOC,
            )
        assertFalse(session.isOrphanedAgainstActiveProgram(program))
    }

    @Test
    @DisplayName("Completed sessions are never treated as orphans")
    fun completedNotOrphaned() {
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-done")
        assertFalse(session.isOrphanedAgainstActiveProgram(null))
    }
}
