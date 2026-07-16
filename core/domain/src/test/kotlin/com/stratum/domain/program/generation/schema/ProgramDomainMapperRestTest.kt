package com.stratum.domain.program.generation.schema

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.resolvedDefaultRestSeconds
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * FPR-S3-* materialize rest matrix for [NormalizedProgram.toDomainProgram].
 */
class ProgramDomainMapperRestTest {

    @Test
    @DisplayName("FPR-S3-001 materialize stamps High rest 180 when lookup provided")
    fun fprS3001_highRest180() {
        val program =
            fixNormOneSet("lift-high").toDomainProgram(
                createdAt = Instant.EPOCH,
                restSecondsForLift = { id -> if (id.value == "lift-high") 180 else null },
            )
        assertEquals(180, singleSetRest(program))
    }

    @Test
    @DisplayName("FPR-S3-002 materialize stamps Medium rest 120 when lookup provided")
    fun fprS3002_mediumRest120() {
        val program =
            fixNormOneSet("lift-medium").toDomainProgram(
                createdAt = Instant.EPOCH,
                restSecondsForLift = { id -> if (id.value == "lift-medium") 120 else null },
            )
        assertEquals(120, singleSetRest(program))
    }

    @Test
    @DisplayName("FPR-S3-003 materialize stamps Low rest 120 when lookup provided")
    fun fprS3003_lowRest120() {
        val program =
            fixNormOneSet("lift-low").toDomainProgram(
                createdAt = Instant.EPOCH,
                restSecondsForLift = { id -> if (id.value == "lift-low") 120 else null },
            )
        assertEquals(120, singleSetRest(program))
    }

    @Test
    @DisplayName("FPR-S3-004 materialize falls back to 120 when lookup returns null")
    fun fprS3004_missingLiftFallsBack120() {
        val program =
            fixNormOneSet("unknown-lift").toDomainProgram(
                createdAt = Instant.EPOCH,
                restSecondsForLift = { null },
            )
        assertEquals(120, singleSetRest(program))
    }

    @Test
    @DisplayName("FPR-S3-005 materialize uses resolvedDefaultRestSeconds including override")
    fun fprS3005_overrideViaResolver() {
        val overrideLift =
            Lift(
                id = LiftId("lift-high"),
                name = "Override High",
                movementPatterns = setOf(MovementPattern.SquatPattern),
                primaryMuscles = setOf(MuscleGroup.Quad),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                fatigueProfile = FatigueProfile.High,
                defaultRestSeconds = 90,
            )
        val byId = mapOf(overrideLift.id to overrideLift)
        val program =
            fixNormOneSet("lift-high").toDomainProgram(
                createdAt = Instant.EPOCH,
                restSecondsForLift = { id -> byId[id]?.resolvedDefaultRestSeconds() },
            )
        assertEquals(90, singleSetRest(program))
    }

    @Test
    @DisplayName("FPR-S3-M001 High lookup must change restSeconds away from blanket 120")
    fun fprS3M001_highLookupNotHardCoded120() {
        val program =
            fixNormOneSet("lift-high").toDomainProgram(
                createdAt = Instant.EPOCH,
                restSecondsForLift = { id -> if (id.value == "lift-high") 180 else null },
            )
        assertEquals(180, singleSetRest(program))
        assertNotEquals(120, singleSetRest(program))
    }

    // FPR-S3-006 lives in GenerateProgramUseCaseTest (use-case catalog pass-through).

    private fun singleSetRest(program: com.stratum.domain.program.Program): Int {
        val set =
            program.workouts
                .single()
                .workoutLifts
                .single()
                .sets
                .single()
        return set.restSeconds
    }

    /** FIX-NORM-ONE-SET */
    private fun fixNormOneSet(liftId: String): NormalizedProgram =
        NormalizedProgram(
            name = "Rest Matrix Program",
            cycleLength = 1,
            restSlotIndices = emptySet(),
            workouts =
                listOf(
                    NormalizedWorkout(
                        name = "Day 1",
                        weekday = null,
                        position = 0,
                        workoutLifts =
                            listOf(
                                NormalizedLift(
                                    liftId = liftId,
                                    position = 0,
                                    progressionScheme = ProgressionScheme.Double,
                                    workoutLiftSets =
                                        listOf(
                                            NormalizedSet(
                                                position = 0,
                                                setType = SetType.Standard,
                                                repMin = 6,
                                                repMax = 10,
                                                rirGoal = 2,
                                                isTopSet = true,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )
}
