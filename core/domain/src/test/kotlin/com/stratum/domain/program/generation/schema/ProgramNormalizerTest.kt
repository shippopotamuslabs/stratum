package com.stratum.domain.program.generation.schema

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.SetType
import com.stratum.domain.program.generation.draft.LiftSetDraft
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.reference.TOP_SET_RIR
import com.stratum.domain.program.generation.reference.clampBackOffRir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgramNormalizerTest {

    @Test
    fun clampBackOffRir_isolationBackOffFloorsProposedZeroToOne() {
        assertEquals(1, clampBackOffRir(isMajorCompound = false, proposed = 0))
    }

    @Test
    fun clampBackOffRir_isolationBackOffPreservesProposedAtOrAboveFloor() {
        assertEquals(2, clampBackOffRir(isMajorCompound = false, proposed = 2))
        assertEquals(3, clampBackOffRir(isMajorCompound = false, proposed = 3))
    }

    @Test
    fun clampBackOffRir_majorCompoundBackOffUnchanged() {
        assertEquals(1, clampBackOffRir(isMajorCompound = true, proposed = 1))
        assertEquals(2, clampBackOffRir(isMajorCompound = true, proposed = 2))
        assertEquals(2, clampBackOffRir(isMajorCompound = true, proposed = 5))
    }

    @Test
    fun parseAndNormalizeProgram_isolationBackOffSetsGetMinimumOneRir() {
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 1,
                workouts =
                    listOf(
                        ProgramWorkoutDraft(
                            name = "Arms",
                            weekday = "Monday",
                            workoutLifts =
                                mutableListOf(
                                    ProgramLiftDraft(
                                        liftId = "barbell-curl",
                                        position = 0,
                                        workoutLiftSets =
                                            mutableListOf(
                                                LiftSetDraft(
                                                    position = 0,
                                                    setType = SetType.Standard,
                                                    repMin = 8,
                                                    repMax = 12,
                                                    rirGoal = TOP_SET_RIR,
                                                    isTopSet = true,
                                                ),
                                                LiftSetDraft(
                                                    position = 1,
                                                    setType = SetType.Standard,
                                                    repMin = 8,
                                                    repMax = 12,
                                                    rirGoal = 0,
                                                    isTopSet = false,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
        val request =
            ProgramNormalizationRequest(
                trainingAge = "Intermediate",
                enabledLiftIds = setOf("barbell-curl"),
                movementPatternByLiftId = mapOf("barbell-curl" to MovementPattern.ElbowFlexion),
            )

        val normalized = parseAndNormalizeProgram(program, request)
        val sets = normalized.workouts.single().workoutLifts.single().workoutLiftSets

        assertEquals(TOP_SET_RIR, sets.first { it.isTopSet }.rirGoal)
        assertEquals(1, sets.first { !it.isTopSet }.rirGoal)
    }

    @Test
    fun parseAndNormalizeProgram_majorCompoundBackOffStillClampedOneToTwo() {
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 1,
                workouts =
                    listOf(
                        ProgramWorkoutDraft(
                            name = "Upper",
                            weekday = "Monday",
                            workoutLifts =
                                mutableListOf(
                                    ProgramLiftDraft(
                                        liftId = "barbell-bench-press",
                                        position = 0,
                                        workoutLiftSets =
                                            mutableListOf(
                                                LiftSetDraft(
                                                    position = 0,
                                                    setType = SetType.Standard,
                                                    repMin = 6,
                                                    repMax = 8,
                                                    rirGoal = TOP_SET_RIR,
                                                    isTopSet = true,
                                                ),
                                                LiftSetDraft(
                                                    position = 1,
                                                    setType = SetType.Standard,
                                                    repMin = 8,
                                                    repMax = 10,
                                                    rirGoal = 5,
                                                    isTopSet = false,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
        val request =
            ProgramNormalizationRequest(
                trainingAge = "Intermediate",
                enabledLiftIds = setOf("barbell-bench-press"),
                movementPatternByLiftId = mapOf("barbell-bench-press" to MovementPattern.HorizontalPress),
            )

        val normalized = parseAndNormalizeProgram(program, request)
        val sets = normalized.workouts.single().workoutLifts.single().workoutLiftSets

        assertEquals(TOP_SET_RIR, sets.first { it.isTopSet }.rirGoal)
        assertEquals(2, sets.first { !it.isTopSet }.rirGoal)
    }
}
