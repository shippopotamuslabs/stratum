package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgramLiftSanitizerTest {
    @Test
    fun `retainOnlyKnownLifts drops unknown lift ids and empty workouts`() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Test",
                createdAt = Instant.EPOCH,
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId("w-1"),
                            programId = ProgramId("p-1"),
                            name = "Day 1",
                            weekday = null,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    workoutLift("wl-1", "w-1", "known-lift"),
                                    workoutLift("wl-2", "w-1", "unknown-lift"),
                                ),
                        ),
                        Workout(
                            id = WorkoutId("w-2"),
                            programId = ProgramId("p-1"),
                            name = "Day 2",
                            weekday = null,
                            position = 1,
                            workoutLifts = listOf(workoutLift("wl-3", "w-2", "missing-lift")),
                        ),
                    ),
            )

        val sanitized = program.retainOnlyKnownLifts(setOf(LiftId("known-lift")))

        assertEquals(1, sanitized.workouts.size)
        assertEquals(1, sanitized.workouts[0].workoutLifts.size)
        assertEquals(LiftId("known-lift"), sanitized.workouts[0].workoutLifts[0].liftId)
    }

    @Test
    fun `retainOnlyKnownLifts keeps program unchanged when all lifts are known`() {
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Test",
                createdAt = Instant.EPOCH,
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId("w-1"),
                            programId = ProgramId("p-1"),
                            name = "Day 1",
                            weekday = null,
                            position = 0,
                            workoutLifts = listOf(workoutLift("wl-1", "w-1", "known-lift")),
                        ),
                    ),
            )

        val known = setOf(LiftId("known-lift"))
        assertEquals(program, program.retainOnlyKnownLifts(known))
        assertTrue(program.retainOnlyKnownLifts(known).workouts.isNotEmpty())
    }

    private fun workoutLift(
        workoutLiftId: String,
        workoutId: String,
        liftId: String,
    ): WorkoutLift =
        WorkoutLift(
            id = WorkoutLiftId(workoutLiftId),
            workoutId = WorkoutId(workoutId),
            liftId = LiftId(liftId),
            progressionScheme = ProgressionScheme.Linear,
            position = 0,
            sets =
                listOf(
                    LiftSet(
                        id = LiftSetId("set-1"),
                        workoutLiftId = WorkoutLiftId(workoutLiftId),
                        position = 0,
                        setType = SetType.Standard,
                        repMin = 8,
                        repMax = 12,
                        rirGoal = 2,
                        isTopSet = true,
                    ),
                ),
        )
}
