package com.stratum.core.inference

import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramGenerationProgressPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [FirebaseProgramGenerator.parseProgram] contract tests. Scenarios PROG-GEN-500 (UUID value-class ids
 * assigned client-side at parse) and PROG-GEN-502 (unknown weekday maps to null).
 *
 * Mirrors `FirebaseImportEquipmentInferenceAdapterTest`: tests the pure `parseProgram` companion
 * directly with a recorded fixture `Map`, no Firebase mocking (MockK is off the classpath and the
 * repo pattern keeps the callable wrapper thin and integration-tested). PROG-GEN-501/503 (callable
 * invocation + auth gate) are exercised by instrumented tests against a deployed function.
 */
class FirebaseProgramGeneratorTest {

    @Nested
    @DisplayName("PROG-GEN-500 parseProgram assigns UUID value-class ids at parse time")
    inner class ParseIds {
        @Test
        fun `parsed ids are non-blank and the hierarchy is internally consistent`() {
            val program: Program = FirebaseProgramGenerator.parseProgram(FIXTURE_RESPONSE)

            assertTrue(program.id.value.isNotBlank())
            assertEquals(1, program.workouts.size)
            val workout = program.workouts[0]
            assertEquals(program.id, workout.programId)
            assertTrue(workout.id.value.isNotBlank())
            assertEquals(2, workout.workoutLifts.size)
            workout.workoutLifts.forEach { lift ->
                assertTrue(lift.id.value.isNotBlank())
                assertEquals(workout.id, lift.workoutId)
                assertTrue(lift.sets.isNotEmpty())
                lift.sets.forEach { set ->
                    assertTrue(set.id.value.isNotBlank())
                    assertEquals(lift.id, set.workoutLiftId)
                }
            }
            // Fixture: bench-press (2 sets) + lat-pulldown (1 set) = 3 sets total.
            assertEquals(3, program.workouts[0].workoutLifts.flatMap { it.sets }.size)
            assertEquals("Upper/Lower", program.name)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-502 unknown weekday string maps to null")
    inner class UnknownWeekday {
        @Test
        fun `an unrecognized weekday yields a null weekday on the parsed workout`() {
            val fixtureWithBadWeekday = responseMap(weekday = "Moonday")
            val program = FirebaseProgramGenerator.parseProgram(fixtureWithBadWeekday)
            assertNull(program.workouts[0].weekday)
        }
    }

    @Test
    @DisplayName("fixture sanity: the two fixtures differ only in weekday")
    fun `fixtures are well-formed`() {
        assertNotEquals(FIXTURE_RESPONSE, responseMap(weekday = "Moonday"))
        assertNotNull(FIXTURE_RESPONSE["program"])
    }

    @Test
    @DisplayName("PROG-GEN-504 invalid payload JSON fails before the callable is invoked")
    fun `invalid payload json is rejected client-side`() {
        val invalidPayload =
            """{"responseProfile":{"volumeResponses":[{"muscle":"Quad","anchorLiftId":"squat",,"correlationSlope":0.8}]}}"""

        val error =
            assertThrows(ProgramGenerationException::class.java) {
                FirebaseProgramGenerator.validatePayloadJson(invalidPayload)
            }

        assertTrue(error.message.contains("could not be serialized"))
    }

    @Test
    @DisplayName("PROG-GEN-505 refining_program maps to RefiningProgram phase for the UI headline")
    fun `mapGenerationStatusToProgress uses progressPhase for headline`() {
        val progress =
            FirebaseProgramGenerator.mapGenerationStatusToProgress(
                progressPhase = "refining_program",
                progressMessage = "Adjusting volume and exercise order…",
            )

        assertEquals(ProgramGenerationProgressPhase.RefiningProgram, progress?.phase)
        assertEquals("Adjusting volume and exercise order…", progress?.detail)
    }

    @Test
    @DisplayName("PROG-GEN-506 unknown progressPhase does not default to reviewing_profile")
    fun `mapGenerationStatusToProgress ignores unknown phases`() {
        assertEquals(
            null,
            FirebaseProgramGenerator.mapGenerationStatusToProgress(
                progressPhase = "refining_program_typo",
                progressMessage = "Adjusting volume and exercise order…",
            ),
        )
    }

    @Test
    @DisplayName("PROG-GEN-505b v2 pipeline phases map to the new progress enum values")
    fun `mapGenerationStatusToProgress maps v2 pipeline phases`() {
        assertEquals(
            ProgramGenerationProgressPhase.SplitPlanning,
            FirebaseProgramGenerator.mapGenerationStatusToProgress("split_planning", "Planning your weekly split…")?.phase,
        )
        assertEquals(
            ProgramGenerationProgressPhase.BuildingWorkouts,
            FirebaseProgramGenerator.mapGenerationStatusToProgress("building_workouts", "Building your workouts…")?.phase,
        )
        assertEquals(
            ProgramGenerationProgressPhase.Integrating,
            FirebaseProgramGenerator.mapGenerationStatusToProgress("integrating", "Putting it together…")?.phase,
        )
    }

    // region Recorded fixture (server post-pass output shape — no progressionScheme, no ids)
    private fun responseMap(weekday: String?): Map<String, Any?> = mapOf(
        "program" to mapOf(
            "name" to "Upper/Lower",
            "workouts" to listOf(
                mapOf(
                    "name" to "Upper A",
                    "weekday" to weekday,
                    "position" to 0,
                    "workoutLifts" to listOf(
                        mapOf(
                            "liftId" to "bench-press",
                            "position" to 0,
                            "workoutLiftSets" to listOf(
                                mapOf(
                                    "position" to 0,
                                    "setType" to "Standard",
                                    "repMin" to 8,
                                    "repMax" to 12,
                                    "rirGoal" to 2,
                                    "isTopSet" to true,
                                ),
                                mapOf(
                                    "position" to 1,
                                    "setType" to "Standard",
                                    "repMin" to 8,
                                    "repMax" to 12,
                                    "rirGoal" to 1,
                                    "isTopSet" to false,
                                ),
                            ),
                        ),
                        mapOf(
                            "liftId" to "lat-pulldown",
                            "position" to 1,
                            "workoutLiftSets" to listOf(
                                mapOf(
                                    "position" to 0,
                                    "setType" to "Standard",
                                    "repMin" to 10,
                                    "repMax" to 15,
                                    "rirGoal" to 2,
                                    "isTopSet" to true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private val FIXTURE_RESPONSE: Map<String, Any?> = responseMap(weekday = "Monday")
    // endregion
}
