package com.stratum.domain.training

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.training.identity.TrainingSessionId

class AutoConsumedRestSessionsTest {
    private val now = Instant.parse("2025-06-01T12:00:00Z")

    @Test
    @DisplayName("Completing training prefix materialises mid-cycle rest as REST_DAY with slot snapshot")
    fun materialisesMidCycleRest() {
        val program = eightSlotProgram()
        // Train slots 0,1,2 — rest at 3 becomes auto-eligible after 0..2 complete.
        val trainSessions =
            program.workouts.take(3).mapIndexed { index, workout ->
                WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-$index", workout.id)
            }
        val auto = materializeAutoConsumedRestSessions(program, trainSessions, now)
        assertEquals(1, auto.size)
        assertEquals(3, auto.single().cycleSlotIndexSnapshot)
        assertEquals(TrainingSessionKind.REST_DAY, auto.single().kind)
        assertEquals(TrainingSessionStatus.COMPLETED, auto.single().status)
    }

    @Test
    @DisplayName("Trailing rest is never auto-materialised")
    fun trailingRestNotMaterialised() {
        val program = eightSlotProgram()
        val trainSessions =
            program.workouts.mapIndexed { index, workout ->
                WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-$index", workout.id)
            }
        val auto = materializeAutoConsumedRestSessions(program, trainSessions, now)
        // Mid-cycle rest@3 may materialise; trailing rest@7 must not.
        assertTrue(auto.none { it.cycleSlotIndexSnapshot == 7 })
        assertTrue(auto.all { it.cycleSlotIndexSnapshot == 3 })
    }

    @Test
    @DisplayName("Already-persisted rest is not re-materialised")
    fun noDuplicateRest() {
        val program = eightSlotProgram()
        val trainSessions =
            program.workouts.take(3).mapIndexed { index, workout ->
                WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-$index", workout.id)
            }
        val existingRest =
            TrainingSession(
                id = com.stratum.domain.training.identity.TrainingSessionId("rest-3"),
                programId = program.id,
                workoutId = null,
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.REST_DAY,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                cycleSlotIndexSnapshot = 3,
                startedAt = now,
                endedAt = now,
                sourceApp = null,
            )
        val auto = materializeAutoConsumedRestSessions(program, trainSessions + existingRest, now)
        assertTrue(auto.isEmpty())
    }

    @Test
    @DisplayName("Unskip of training that justified mid-cycle rest drops that auto-rest")
    fun unskipCascade_dropsNoLongerJustifiedAutoRest() {
        val program = eightSlotProgram()
        val trainSessions =
            program.workouts.take(3).mapIndexed { index, workout ->
                WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-$index", workout.id)
            }
        val auto = materializeAutoConsumedRestSessions(program, trainSessions, now)
        assertEquals(1, auto.size)
        assertEquals(3, auto.single().cycleSlotIndexSnapshot)

        // Remove last training of the solid prefix (wkt-2) → rest@3 no longer pure-auto.
        val remaining = trainSessions.take(2) + auto
        val drop =
            autoRestSessionsNoLongerJustified(
                program = program,
                remainingSessions = remaining,
                deletedWorkoutIds = setOf(WorkoutId("wkt-2")),
            )
        assertEquals(1, drop.size)
        assertEquals(3, drop.single().cycleSlotIndexSnapshot)
    }

    @Test
    @DisplayName("Unskip cascade keeps manual rest that was never pure-auto")
    fun unskipCascade_keepsManualNonAutoRest() {
        val program = eightSlotProgram()
        // Only first training complete — mid rest is not pure-auto yet.
        val trainSessions =
            listOf(
                WorkoutLoggingFixtures.completedProgramWorkoutSession("ts-0", WorkoutId("wkt-0")),
            )
        val manualRest =
            TrainingSession(
                id = com.stratum.domain.training.identity.TrainingSessionId("rest-3-manual"),
                programId = program.id,
                workoutId = null,
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.REST_DAY,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                cycleSlotIndexSnapshot = 3,
                startedAt = now,
                endedAt = now,
                sourceApp = null,
            )
        val drop =
            autoRestSessionsNoLongerJustified(
                program = program,
                remainingSessions = emptyList(),
                deletedWorkoutIds = setOf(WorkoutId("wkt-0")),
            )
        // With no remaining trainings, pure-auto is empty — but pureAutoBefore with only wkt-0
        // also does not include rest@3 (prefix not solid). Manual rest not in pureAutoBefore.
        assertTrue(
            autoRestSessionsNoLongerJustified(
                program = program,
                remainingSessions = listOf(manualRest),
                deletedWorkoutIds = setOf(WorkoutId("wkt-0")),
            ).isEmpty(),
        )
        assertTrue(drop.isEmpty())
    }

    private fun eightSlotProgram(): Program {
        val workouts =
            (0 until 6).map { index ->
                Workout(
                    id = WorkoutId("wkt-$index"),
                    programId = WorkoutLoggingFixtures.programId,
                    name = "W$index",
                    weekday = null,
                    position = index,
                    workoutLifts = emptyList(),
                )
            }
        return Program(
            id = WorkoutLoggingFixtures.programId,
            name = "8-day",
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            cycleLength = 8,
            workouts = workouts,
            restSlotIndices = setOf(3, 7),
        )
    }
}
