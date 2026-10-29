package com.stratum.application.program.importing

import com.stratum.core.testing.export.ExportTestFixtures
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramImportDedupeTest {

    @Test
    @DisplayName("IE-S5-005 Soft name and structure match skips without exported id")
    fun ieS5_005_softMatchSkip() {
        // Different lift IDs, same display names → still soft-match skip (spec: ordered lift names).
        val existingLiftId = LiftId("existing-squat-id")
        val candidateLiftId = LiftId("incoming-squat-id")
        val existing =
            programWithLift(
                id = "existing-1",
                name = "Push Pull",
                workoutName = "Day A",
                liftId = existingLiftId,
            )
        val candidate =
            programWithLift(
                id = "incoming-new",
                name = "Push Pull",
                workoutName = "Day A",
                liftId = candidateLiftId,
            )
        val liftNames =
            mapOf(
                existingLiftId to "Squat",
                candidateLiftId to "Squat",
            )
        val skip =
            ProgramImportDedupe.shouldSkip(
                candidate = candidate,
                existing = listOf(existing),
                exportedId = null,
                liftNamesById = liftNames,
            )
        assertTrue(skip)
    }

    @Test
    @DisplayName("IE-S5-005b Soft match still works when names fall back to liftId values")
    fun ieS5_005b_softMatchViaFixtureLiftIds() {
        // Fixtures encode the same name into the same liftId; empty name map falls back to id value.
        val existing =
            ExportTestFixtures.minimalProgram(
                id = "existing-1",
                name = "Push Pull",
                isActive = false,
                workoutName = "Day A",
                liftName = "Squat",
            )
        val candidate =
            ExportTestFixtures.minimalProgram(
                id = "incoming-new",
                name = "Push Pull",
                isActive = false,
                workoutName = "Day A",
                liftName = "Squat",
            )
        val skip =
            ProgramImportDedupe.shouldSkip(
                candidate = candidate,
                existing = listOf(existing),
                exportedId = null,
            )
        assertTrue(skip)
    }

    private fun programWithLift(
        id: String,
        name: String,
        workoutName: String,
        liftId: LiftId,
    ): Program {
        val programId = ProgramId(id)
        val workoutId = WorkoutId("$id-wkt")
        val workoutLiftId = WorkoutLiftId("$id-wl")
        return Program(
            id = programId,
            name = name,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 4,
            workouts =
                listOf(
                    Workout(
                        id = workoutId,
                        programId = programId,
                        name = workoutName,
                        weekday = null,
                        position = 0,
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = workoutLiftId,
                                    workoutId = workoutId,
                                    liftId = liftId,
                                    progressionScheme = ProgressionScheme.Double,
                                    position = 0,
                                    sets =
                                        listOf(
                                            LiftSet(
                                                id = LiftSetId("$id-set0"),
                                                workoutLiftId = workoutLiftId,
                                                position = 0,
                                                setType = SetType.Standard,
                                                repMin = 5,
                                                repMax = 8,
                                                rirGoal = 2,
                                                restSeconds = 120,
                                                isTopSet = true,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
            isActive = false,
        )
    }
}
