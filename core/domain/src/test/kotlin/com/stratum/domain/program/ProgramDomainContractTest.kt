package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Program domain hierarchy and identity type contract tests. Scenarios PROG-GEN-101 (IDs reject
 * blank; enums closed) and PROG-GEN-102 (LiftSet carries no weight/load field).
 *
 * These verify already-defined types and so are green; they pin the domain contract for the
 * mapper/schema/function tests that follow. No reflection is used (keeps `kotlin-reflect` off the
 * domain test classpath).
 */
class ProgramDomainContractTest {
    @Nested
    @DisplayName("PROG-GEN-101 IDs reject blank and enums are closed")
    inner class IdentityAndEnums {
        @Test
        fun `blank ids are rejected with a cannot-be-blank message`() {
            assertThrows(IllegalArgumentException::class.java) { ProgramId("") }
            assertThrows(IllegalArgumentException::class.java) { WorkoutId("") }
            assertThrows(IllegalArgumentException::class.java) { WorkoutLiftId("") }
            assertThrows(IllegalArgumentException::class.java) { LiftSetId("") }
            val error = assertThrows(IllegalArgumentException::class.java) { ProgramId("  ") }
            assertTrue(error.message!!.contains("cannot be blank"))
        }

        @Test
        fun `ProgressionScheme has exactly Linear and Double`() {
            assertEquals(listOf("Linear", "Double"), ProgressionScheme.entries.map { it.name })
        }

        @Test
        fun `SetType has exactly Standard MyoRep and DropSet`() {
            assertEquals(
                listOf("Standard", "MyoRep", "DropSet"),
                SetType.entries.map { it.name },
            )
        }
    }

    @Nested
    @DisplayName("PROG-GEN-102 LiftSet carries no weight or load field")
    inner class HierarchyShape {
        @Test
        fun `LiftSet round-trips its exact approved fields with no weight or load field`() {
            val set = LiftSet(
                id = LiftSetId("ls-1"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                position = 0,
                setType = SetType.Standard,
                repMin = 5,
                repMax = 5,
                rirGoal = 2,
                isTopSet = true,
            )
            // Reading every approved field asserts they exist; the data class has no other
            // constructor parameter, so any weight/load field would have to appear here.
            assertEquals(LiftSetId("ls-1"), set.id)
            assertEquals(WorkoutLiftId("wl-1"), set.workoutLiftId)
            assertEquals(0, set.position)
            assertEquals(SetType.Standard, set.setType)
            assertEquals(5, set.repMin)
            assertEquals(5, set.repMax)
            assertEquals(2, set.rirGoal)
            assertTrue(set.isTopSet)
            // toString cannot leak a weight/load property because none exists on the type.
            val dump = set.toString()
            assertFalse(dump.contains("weight", ignoreCase = true))
            assertFalse(dump.contains("load", ignoreCase = true))
        }

        @Test
        fun `WorkoutLift exposes progressionScheme and Workout accepts a null weekday`() {
            val lift = WorkoutLift(
                id = WorkoutLiftId("wl-1"),
                workoutId = WorkoutId("w-1"),
                liftId = LiftId("back-squat"),
                progressionScheme = ProgressionScheme.Linear,
                position = 0,
                sets = emptyList(),
            )
            assertEquals(ProgressionScheme.Linear, lift.progressionScheme)

            val unbound = Workout(
                id = WorkoutId("w-1"),
                programId = ProgramId("p-1"),
                name = "Session A",
                weekday = null,
                position = 0,
                workoutLifts = emptyList(),
            )
            assertNull(unbound.weekday)
            // And a bound weekday round-trips.
            assertEquals(
                Weekday.Monday,
                unbound.copy(weekday = Weekday.Monday).weekday,
            )
        }

        @Test
        fun `Program composes the full hierarchy`() {
            val program = Program(
                id = ProgramId("p-1"),
                name = "Full Body",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                workouts = listOf(
                    Workout(
                        id = WorkoutId("w-1"),
                        programId = ProgramId("p-1"),
                        name = "A",
                        weekday = Weekday.Monday,
                        position = 0,
                        workoutLifts = emptyList(),
                    ),
                ),
            )
            assertEquals("Full Body", program.name)
            assertEquals(1, program.workouts.size)
            assertEquals(Weekday.Monday, program.workouts[0].weekday)
        }
    }
}
