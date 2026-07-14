@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.integration

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.SetType
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.LiftSetDraft
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Post-solver polish is duration-only: trim when hard sets exceed band max + 5 min grace.
 * Soft aim 75 → max 90 → trim threshold floor(95/3) = 31 sets.
 */
class SessionDurationTrimTest {

    @Test
    @DisplayName("does not trim when under max + grace")
    fun doesNotTrimUnderGrace() {
        // 25 sets = 75 min estimated; band max 90 + 5 grace → no trim
        val context = context(sessionMinutes = 75)
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 1,
                workouts = listOf(workout("Day", "Monday", listOf(lift("curl", 12), lift("ext", 13)))),
            )
        val repaired = repairWeeklyVolume(program, emptyMap(), context)
        assertEquals(25, hardSets(repaired.workouts[0]))
    }

    @Test
    @DisplayName("trims when over max + grace")
    fun trimsWhenOverGrace() {
        // 40 sets = 120 min >> 90+5; must trim down toward threshold 31
        val context = context(sessionMinutes = 75)
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 1,
                workouts =
                    listOf(
                        workout(
                            "Day",
                            "Monday",
                            listOf(
                                lift("curl", 10),
                                lift("ext", 10),
                                lift("raise", 10),
                                lift("press", 10),
                            ),
                        ),
                    ),
            )
        val repaired = repairWeeklyVolume(program, emptyMap(), context)
        val sets = hardSets(repaired.workouts[0])
        assertTrue(sets <= 31, "expected trim to ≤31 (max+grace), got $sets")
        assertTrue(sets < 40, "must have trimmed from 40")
    }

    @Test
    @DisplayName("does not rewrite volume for adjacent recovery (trust the solver)")
    fun doesNotTrimAdjacentRecovery() {
        val context =
            context(sessionMinutes = 75).copy(
                specialization = mapOf(MuscleGroup.Bicep to SpecializationLevel.Specialize),
            )
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 2,
                workouts =
                    listOf(
                        workout("A", "Friday", listOf(lift("curl", 4))),
                        workout("B", "Saturday", listOf(lift("curl", 4))),
                    ),
            )
        val repaired = repairWeeklyVolume(program, emptyMap(), context)
        assertEquals(4, hardSets(repaired.workouts[0]))
        assertEquals(4, hardSets(repaired.workouts[1]))
    }

    private fun hardSets(workout: ProgramWorkoutDraft): Int =
        workout.workoutLifts.orEmpty().sumOf { it.workoutLiftSets?.size ?: 0 }

    private fun context(sessionMinutes: Int): ProgramGenerationContext =
        ProgramGenerationContext(
            trainingAge = "Advanced",
            lastTrained = null,
            sessionLengthMinutes = sessionMinutes,
            trainingDaysPerWeek = 4,
            specialization = emptyMap(),
            liftsById =
                mapOf(
                    "curl" to liftCtx("curl", "Barbell Curl", MovementPattern.ElbowFlexion, MuscleGroup.Bicep),
                    "ext" to liftCtx("ext", "Pushdown", MovementPattern.ElbowExtension, MuscleGroup.Tricep),
                    "raise" to
                        liftCtx("raise", "Lateral Raise", MovementPattern.ShoulderAbduction, MuscleGroup.LateralDeltoid),
                    "press" to
                        liftCtx(
                            "press",
                            "Bench",
                            MovementPattern.HorizontalPress,
                            MuscleGroup.Chest,
                            secondary = setOf(MuscleGroup.Tricep),
                        ),
                ),
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = setOf("curl", "ext", "raise", "press"),
            movementPatternByLiftId = emptyMap(),
            liftEffectivenessByLiftId = emptyMap(),
        )

    private fun liftCtx(
        id: String,
        name: String,
        pattern: MovementPattern,
        primary: MuscleGroup,
        secondary: Set<MuscleGroup> = emptySet(),
    ): LiftContext =
        LiftContext(
            id = id,
            name = name,
            movementPattern = pattern,
            movementPatterns = setOf(pattern),
            primaryMuscles = setOf(primary),
            secondaryMuscles = secondary,
        )

    private fun workout(
        name: String,
        weekday: String,
        lifts: List<ProgramLiftDraft>,
    ): ProgramWorkoutDraft =
        ProgramWorkoutDraft(
            name = name,
            weekday = weekday,
            position = 0,
            workoutLifts = lifts.toMutableList(),
        )

    private fun lift(id: String, sets: Int): ProgramLiftDraft =
        ProgramLiftDraft(
            liftId = id,
            position = 0,
            workoutLiftSets =
                (0 until sets)
                    .map { i ->
                        LiftSetDraft(
                            position = i,
                            setType = SetType.Standard,
                            repMin = 8,
                            repMax = 12,
                            rirGoal = 2,
                            isTopSet = i == 0,
                        )
                    }.toMutableList(),
        )
}
