package com.stratum.domain.program

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.schedule.GenerationCycleHints
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Program aggregate (STR-167)")
class ProgramAggregateTest {

    private val programId = ProgramId("p-1")

    private fun set(id: String, liftId: WorkoutLiftId, position: Int): LiftSet =
        LiftSet(
            id = LiftSetId(id),
            workoutLiftId = liftId,
            position = position,
            setType = SetType.Standard,
            repMin = 8,
            repMax = 12,
            rirGoal = 2,
            isTopSet = position == 0,
        )

    private fun lift(id: String, workoutId: WorkoutId, position: Int, setCount: Int = 1): WorkoutLift {
        val liftId = WorkoutLiftId(id)
        return WorkoutLift(
            id = liftId,
            workoutId = workoutId,
            liftId = LiftId("lift-$id"),
            progressionScheme = ProgressionScheme.Double,
            position = position,
            sets = (0 until setCount).map { index -> set("$id-s$index", liftId, index) },
        )
    }

    private fun workout(id: String, position: Int, lifts: List<WorkoutLift> = emptyList()): Workout =
        Workout(
            id = WorkoutId(id),
            programId = programId,
            name = "Workout $id",
            weekday = null,
            position = position,
            workoutLifts = lifts,
        )

    private fun program(
        workouts: List<Workout> = listOf(workout("w-0", 0), workout("w-1", 1)),
        cycleLength: Int = 2,
        cyclePosition: Int = 0,
        currentCycleNumber: Int = 1,
        restSlotIndices: Set<Int> = emptySet(),
        isActive: Boolean = true,
    ): Program =
        Program(
            id = programId,
            name = "Test",
            createdAt = Instant.parse("2026-06-26T12:00:00Z"),
            cycleLength = cycleLength,
            workouts = workouts,
            cyclePosition = cyclePosition,
            currentCycleNumber = currentCycleNumber,
            restSlotIndices = restSlotIndices,
            isActive = isActive,
        )

    @Nested
    @DisplayName("constructor invariants")
    inner class ConstructorInvariants {
        @Test
        fun `non-positive cycleLength is rejected`() {
            assertThrows<IllegalArgumentException> { program(cycleLength = 0) }
        }

        @Test
        fun `cyclePosition outside the cycle is rejected`() {
            assertThrows<IllegalArgumentException> { program(cyclePosition = 2) }
            assertThrows<IllegalArgumentException> { program(cyclePosition = -1) }
        }

        @Test
        fun `rest slot index outside the cycle is rejected`() {
            assertThrows<IllegalArgumentException> { program(restSlotIndices = setOf(2)) }
        }

        @Test
        fun `currentCycleNumber below 1 is rejected`() {
            assertThrows<IllegalArgumentException> { program(currentCycleNumber = 0) }
        }

        @Test
        fun `workout belonging to another program is rejected`() {
            val foreign =
                Workout(
                    id = WorkoutId("w-x"),
                    programId = ProgramId("other"),
                    name = "Foreign",
                    weekday = null,
                    position = 0,
                    workoutLifts = emptyList(),
                )
            assertThrows<IllegalArgumentException> { program(workouts = listOf(foreign)) }
        }

        @Test
        fun `duplicate workout positions are rejected`() {
            assertThrows<IllegalArgumentException> {
                program(workouts = listOf(workout("w-0", 0), workout("w-1", 0)))
            }
        }

        @Test
        fun `workout position gap is legitimate (pruned program shape)`() {
            // ProgramLiftSanitizer drops a workout and leaves the survivor's
            // position untouched; that state must stay constructible.
            val pruned = program(workouts = listOf(workout("w-1", 1)))
            assertEquals(1, pruned.workouts.single().position)
        }

        @Test
        fun `duplicate lift positions within a workout are rejected`() {
            val w = WorkoutId("w-0")
            assertThrows<IllegalArgumentException> {
                workout("w-0", 0, listOf(lift("wl-0", w, 0), lift("wl-1", w, 0)))
            }
        }

        @Test
        fun `lift belonging to another workout is rejected`() {
            assertThrows<IllegalArgumentException> {
                workout("w-0", 0, listOf(lift("wl-0", WorkoutId("w-other"), 0)))
            }
        }

        @Test
        fun `duplicate set positions within a lift are rejected`() {
            val liftId = WorkoutLiftId("wl-0")
            assertThrows<IllegalArgumentException> {
                WorkoutLift(
                    id = liftId,
                    workoutId = WorkoutId("w-0"),
                    liftId = LiftId("lift-1"),
                    progressionScheme = ProgressionScheme.Double,
                    position = 0,
                    sets = listOf(set("s-0", liftId, 0), set("s-1", liftId, 0)),
                )
            }
        }

        @Test
        fun `negative positions are rejected on nested types`() {
            assertThrows<IllegalArgumentException> { workout("w-0", -1) }
            assertThrows<IllegalArgumentException> { lift("wl-0", WorkoutId("w-0"), -1) }
            assertThrows<IllegalArgumentException> {
                set("s-0", WorkoutLiftId("wl-0"), -1)
            }
        }

        @Test
        fun `valid rest-bearing program constructs`() {
            val p =
                program(
                    workouts = listOf(workout("w-0", 0)),
                    cycleLength = 3,
                    restSlotIndices = setOf(1, 2),
                )
            assertEquals(3, p.cycleLength)
            assertEquals(setOf(1, 2), p.restSlotIndices)
        }
    }

    @Nested
    @DisplayName("activation")
    inner class Activation {
        @Test
        fun `activate and deactivate flip only isActive`() {
            val inactive = program(isActive = false)
            val activated = inactive.activate()
            assertTrue(activated.isActive)
            assertEquals(program(isActive = true), activated)
            assertFalse(activated.deactivate().isActive)
        }
    }

    @Nested
    @DisplayName("template mutations")
    inner class TemplateMutations {
        @Test
        fun `addWorkoutLift appends to the owning workout only`() {
            val base = program()
            val newLift = lift("wl-new", WorkoutId("w-1"), 0)
            val updated = base.addWorkoutLift(newLift)
            assertTrue(updated.workouts.first { it.id.value == "w-0" }.workoutLifts.isEmpty())
            assertEquals(listOf(newLift), updated.workouts.first { it.id.value == "w-1" }.workoutLifts)
        }

        @Test
        fun `addWorkoutLift for an unknown workout is rejected`() {
            assertThrows<IllegalArgumentException> {
                program().addWorkoutLift(lift("wl-new", WorkoutId("w-missing"), 0))
            }
        }

        @Test
        fun `replaceWorkoutLift swaps the stored lift`() {
            val w = WorkoutId("w-0")
            val original = lift("wl-0", w, 0)
            val base = program(workouts = listOf(workout("w-0", 0, listOf(original)), workout("w-1", 1)))
            val replacement = original.copy(liftId = LiftId("lift-swapped"))
            val updated = base.replaceWorkoutLift(replacement)
            assertEquals(
                LiftId("lift-swapped"),
                updated.workouts.first().workoutLifts.single().liftId,
            )
        }

        @Test
        fun `replaceWorkoutLift for an unknown lift is rejected`() {
            assertThrows<IllegalArgumentException> {
                program().replaceWorkoutLift(lift("wl-missing", WorkoutId("w-0"), 0))
            }
        }
    }

    @Nested
    @DisplayName("cycle advancement")
    inner class CycleAdvancement {
        @Test
        fun `incomplete cycle advances the pointer without an event`() {
            val program = WorkoutLoggingFixtures.twoWorkoutProgram()
            val sessions =
                listOf(
                    WorkoutLoggingFixtures.completedProgramWorkoutSession(
                        "ts-a",
                        WorkoutLoggingFixtures.workoutAId,
                    ),
                )
            val effects = program.advanceCycle(sessions)
            assertEquals(1, effects.pointerUpdate.currentCycleNumber)
            assertEquals(1, effects.pointerUpdate.cyclePosition)
            assertNull(effects.cycleCompletedEvent)
        }

        @Test
        fun `completed cycle rolls the pointer and emits the completion event`() {
            val program =
                WorkoutLoggingFixtures.twoWorkoutProgram(currentCycleNumber = 3)
                    .copy(cycleStartEpochDay = 100L)
            val sessions =
                listOf(
                    WorkoutLoggingFixtures.completedProgramWorkoutSession(
                        "ts-a",
                        WorkoutLoggingFixtures.workoutAId,
                        cycleNumber = 3,
                    ),
                    WorkoutLoggingFixtures.completedProgramWorkoutSession(
                        "ts-b",
                        WorkoutLoggingFixtures.workoutBId,
                        cycleNumber = 3,
                    ),
                )
            val effects = program.advanceCycle(sessions)
            assertEquals(4, effects.pointerUpdate.currentCycleNumber)
            assertEquals(0, effects.pointerUpdate.cyclePosition)
            assertEquals(102L, effects.pointerUpdate.cycleStartEpochDay)
            val event = effects.cycleCompletedEvent
            assertEquals(ProgramChangeEventType.CYCLE_COMPLETED, event?.eventType)
            assertEquals(
                3,
                (event?.payload as? ProgramChangeEventPayload.CycleCompleted)?.completedCycleNumber,
            )
        }
    }

    @Nested
    @DisplayName("generation cycle hints geometry")
    inner class GenerationHintsGeometry {
        @Test
        fun `hints with a rest index outside the cycle are rejected`() {
            assertThrows<IllegalArgumentException> {
                GenerationCycleHints(cycleLength = 7, restSlotIndices = setOf(7))
            }
        }

        @Test
        fun `hints with a non-positive cycle length are rejected`() {
            assertThrows<IllegalArgumentException> {
                GenerationCycleHints(cycleLength = 0, restSlotIndices = emptySet())
            }
        }
    }
}
