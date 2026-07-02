@file:Suppress("MagicNumber", "MaxLineLength")

package com.stratum.domain.program.generation.integration

import com.stratum.domain.program.SetType
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.LiftSetDraft
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.reference.LiftStressClass
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.reference.calendarDayIndexGap
import com.stratum.domain.program.generation.reference.muscleRecoveryGapViolated
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleSessionStress
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the metadata-truth adjacent-day recovery repair in [repairWeeklyVolume]. The
 * solver's pattern-credit adjacent forbid can diverge from realized per-lift metadata volume; the
 * repair pass trims sets from the violating muscle's contributors on the later day to get both days
 * below the meaningful threshold. These tests construct [ProgramDraft]s by hand so the trim behaviour
 * can be asserted directly without running the CP-SAT solver.
 */
class CalendarAdjacentRecoveryRepairTest {

    @Test
    fun doesNotTrimWhenEarlierDayIsIsolationLowDose() {
        // Friday: 2 direct curl sets (2.0 — isolation low). Saturday: 5 curl sets (5.0 — moderate
        // isolation). Earlier low-dose isolation debt is only 24h, so gap-1 is allowed.
        val context = bicepSpecializationContext()
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout("Arm A", "Friday", lift("barbell-curl", 2, isBicepPrimary = true)),
                        workout("Arm B", "Saturday", lift("barbell-curl", 5, isBicepPrimary = true)),
                    ),
            )

        val repaired = repairWeeklyVolume(program, emptyMap(), context)

        val fridaySets = repaired.workouts[0].workoutLifts.orEmpty()[0].workoutLiftSets.orEmpty().size
        val saturdaySets = repaired.workouts[1].workoutLifts.orEmpty()[0].workoutLiftSets.orEmpty().size
        assertTrue(fridaySets == 2, "Low-dose earlier day must not be trimmed: Friday sets=$fridaySets")
        assertTrue(saturdaySets == 5, "Later moderate day must not be trimmed: Saturday sets=$saturdaySets")
    }

    @Test
    fun trimsToClearAdjacentIsolationModerateViolation() {
        // Friday + Saturday both carry moderate direct bicep (4 sets each). Earlier moderate isolation
        // requires 48h (gap 2); repair clears the gap-1 violation.
        val context = bicepSpecializationContext()
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout("Arm A", "Friday", lift("barbell-curl", 4, isBicepPrimary = true)),
                        workout("Arm B", "Saturday", lift("barbell-curl", 4, isBicepPrimary = true)),
                    ),
            )

        val repaired = repairWeeklyVolume(program, emptyMap(), context)

        val fridayStress = computeWorkoutMuscleSessionStress(repaired.workouts[0], context)["Bicep"]!!
        val saturdayDose = computeWorkoutMuscleVolume(repaired.workouts[1], context)["Bicep"] ?: 0.0
        assertTrue(
            !muscleRecoveryGapViolated(
                fridayStress,
                saturdayDose,
                calendarDayIndexGap(Weekday.Friday.ordinal, Weekday.Saturday.ordinal),
            ),
            "Moderate adjacent isolation must be repaired: Friday=${fridayStress.fractionalSets} Saturday=$saturdayDose",
        )
    }

    @Test
    fun doesNotTrimAdjacentDirectCurlAfterRowBicepSecondary() {
        // Monday: 4 lat pulldown sets tag Bicep at 2.0 (secondary, isolation recovery class).
        // Tuesday: 2 direct curl sets (2.0). Gap-1 is allowed — Back carries upper-compound debt,
        // not Bicep.
        val context = bicepSpecializationContext(weekdays = listOf("Monday", "Tuesday"))
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout(
                            "Pull A",
                            "Monday",
                            lift("lat-pulldown", 4, pattern = "VerticalPull", primary = "Back"),
                        ),
                        workout("Arm A", "Tuesday", lift("barbell-curl", 2, isBicepPrimary = true)),
                    ),
            )

        val repaired = repairWeeklyVolume(program, emptyMap(), context)

        val mondayStress = computeWorkoutMuscleSessionStress(repaired.workouts[0], context)["Bicep"]!!
        val tuesdayBicep = computeWorkoutMuscleVolume(repaired.workouts[1], context)["Bicep"] ?: 0.0
        assertEquals(LiftStressClass.Isolation, mondayStress.stressClass)
        assertTrue(
            !muscleRecoveryGapViolated(
                mondayStress,
                tuesdayBicep,
                calendarDayIndexGap(Weekday.Monday.ordinal, Weekday.Tuesday.ordinal),
            ),
            "Row bicep secondary must not force 48h before direct curl: Mon=${mondayStress.fractionalSets} Tue=$tuesdayBicep",
        )
    }

    @Test
    fun trimsEarlierDayWhenLaterDayHasNoTrimmableCandidate() {
        // Friday: 6 curl sets (Bicep 6.0). Saturday: a protected lower anchor (hip-thrust, HipHinge,
        // 3 sets — protected, not trimmable) plus a chin-up tagging Bicep secondary at 4 sets
        // (4 × 0.5 = 2.0 — meaningful). Both days ≥ 2.0 → violation. The later day's only trimmable
        // Bicep contributor is the chin-up (secondary, score 0); it trims 4 → 2 (1.0 < 2.0), which
        // clears the violation from the later side without touching the protected hip-thrust.
        val context = bicepSpecializationContext()
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout("Arm A", "Friday", lift("barbell-curl", 6, isBicepPrimary = true)),
                        workout(
                            "Lower B",
                            "Saturday",
                            lift("hip-thrust", 3, pattern = "HipHinge", primary = "Glute"),
                            lift("chin-up", 4, isBicepSecondary = true, pattern = "VerticalPull", primary = "Back"),
                        ),
                    ),
            )

        val repaired = repairWeeklyVolume(program, emptyMap(), context)

        val fridayBicep = computeWorkoutMuscleVolume(repaired.workouts[0], context)["Bicep"] ?: 0.0
        val saturdayBicep = computeWorkoutMuscleVolume(repaired.workouts[1], context)["Bicep"] ?: 0.0
        assertTrue(
            fridayBicep < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE || saturdayBicep < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE,
            "At least one day should drop below meaningful: Friday=$fridayBicep Saturday=$saturdayBicep",
        )
        // Lower anchor on Saturday must be preserved at 3 sets.
        val hipThrustSets = repaired.workouts[1].workoutLifts.orEmpty()[0].workoutLiftSets.orEmpty().size
        assertTrue(hipThrustSets == 3, "Protected lower anchor must not be trimmed: hip-thrust sets=$hipThrustSets")
    }

    @Test
    fun doesNotTrimWhenDaysAreNotCalendarAdjacent() {
        // Monday and Wednesday both have Bicep ≥ 2.0, but they are not adjacent (gap = 2). The repair
        // must leave both untouched.
        val context = bicepSpecializationContext(weekdays = listOf("Monday", "Wednesday"))
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout("Arm A", "Monday", lift("barbell-curl", 4, isBicepPrimary = true)),
                        workout("Arm B", "Wednesday", lift("barbell-curl", 4, isBicepPrimary = true)),
                    ),
            )

        val repaired = repairWeeklyVolume(program, emptyMap(), context)

        val mondaySets = repaired.workouts[0].workoutLifts.orEmpty()[0].workoutLiftSets.orEmpty().size
        val wednesdaySets = repaired.workouts[1].workoutLifts.orEmpty()[0].workoutLiftSets.orEmpty().size
        assertTrue(mondaySets == 4, "Non-adjacent day must not be trimmed: Monday sets=$mondaySets")
        assertTrue(wednesdaySets == 4, "Non-adjacent day must not be trimmed: Wednesday sets=$wednesdaySets")
    }

    @Test
    fun doesNotTrimWhenOneDayIsSubThreshold() {
        // Friday Bicep = 1.5 (3 sets × 0.5 secondary — sub-meaningful), Saturday Bicep = 5.0. Only one
        // day reaches the threshold, so the adjacent-day rule does not apply — no trim.
        val context = bicepSpecializationContext()
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout("Pull A", "Friday", lift("lat-pulldown", 3, isBicepSecondary = true)),
                        workout("Arm B", "Saturday", lift("barbell-curl", 5, isBicepPrimary = true)),
                    ),
            )

        val repaired = repairWeeklyVolume(program, emptyMap(), context)

        val fridaySets = repaired.workouts[0].workoutLifts.orEmpty()[0].workoutLiftSets.orEmpty().size
        val saturdaySets = repaired.workouts[1].workoutLifts.orEmpty()[0].workoutLiftSets.orEmpty().size
        assertTrue(fridaySets == 3, "Sub-threshold day must not be trimmed: Friday sets=$fridaySets")
        assertTrue(saturdaySets == 5, "Non-violating day must not be trimmed: Saturday sets=$saturdaySets")
    }

    @Test
    fun prefersSecondaryContributorOverPrimaryOnTheLaterDay() {
        // Saturday (later day) has two Bicep contributors: a chin-up (Bicep secondary, score 0) and a
        // curl (Bicep primary, score 1). The repair should trim the chin-up first — dropping one set
        // removes 0.5 Bicep while preserving Back primary work. Friday Bicep = 4.0 (curl primary),
        // Saturday Bicep = 1.5 (chin-up secondary × 3) + 5.0 (curl × 5) = 6.5. Both ≥ 2.0.
        // The repair trims Saturday's chin-up first (secondary, score 0): 3 → 2 (Saturday Bicep 6.0,
        // still ≥ 2.0). Once the chin-up is at the 2-set floor the curl (primary, score 1) takes over.
        val context = bicepSpecializationContext()
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout("Arm A", "Friday", lift("barbell-curl", 4, isBicepPrimary = true)),
                        workout(
                            "Mixed B",
                            "Saturday",
                            lift("chin-up", 3, isBicepSecondary = true, pattern = "VerticalPull", primary = "Back"),
                            lift("barbell-curl", 5, isBicepPrimary = true),
                        ),
                    ),
            )

        val repaired = repairWeeklyVolume(program, emptyMap(), context)

        val chinUpSets = repaired.workouts[1].workoutLifts.orEmpty()[0].workoutLiftSets.orEmpty().size
        val curlSets = repaired.workouts[1].workoutLifts.orEmpty()[1].workoutLiftSets.orEmpty().size
        assertTrue(
            chinUpSets == 2,
            "Secondary Bicep contributor (chin-up) should be trimmed to the 2-set floor before the " +
                "primary (curl) is touched: chin-up sets=$chinUpSets",
        )
        assertTrue(
            curlSets < 5,
            "Primary Bicep contributor (curl) should be trimmed after the secondary hits the floor: curl sets=$curlSets",
        )
        for (workout in repaired.workouts) {
            for (liftDraft in workout.workoutLifts.orEmpty()) {
                val sets = liftDraft.workoutLiftSets.orEmpty().size
                assertTrue(sets >= 2, "Active lift must keep ≥2 sets: ${liftDraft.liftId}=$sets")
            }
        }
    }

    @Test
    fun trimsGapTwoHeavyAxialQuadViolation() {
        // Mon + Wed moderate barbell squat days (gap 2) violate heavy-axial quad recovery (requires gap 3).
        val context = quadSpecializationContext(weekdays = listOf("Monday", "Wednesday"))
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout(
                            "Lower A",
                            "Monday",
                            lift("barbell-back-squat", 4, pattern = "SquatPattern", primary = "Quad"),
                        ),
                        workout(
                            "Lower B",
                            "Wednesday",
                            lift("barbell-back-squat", 4, pattern = "SquatPattern", primary = "Quad"),
                        ),
                    ),
            )

        val repaired = repairWeeklyVolume(program, emptyMap(), context)

        val mondayStress = computeWorkoutMuscleSessionStress(repaired.workouts[0], context)["Quad"]
        val wednesdayStress = computeWorkoutMuscleSessionStress(repaired.workouts[1], context)["Quad"]
        val gap = calendarDayIndexGap(0, 2)
        val violated =
            mondayStress != null &&
                wednesdayStress != null &&
                muscleRecoveryGapViolated(
                    mondayStress,
                    wednesdayStress.fractionalSets,
                    gap,
                )
        assertTrue(
            !violated,
            "Gap-2 heavy-axial quad exposure should be repaired: Monday=${mondayStress?.fractionalSets} " +
                "Wednesday=${wednesdayStress?.fractionalSets}",
        )
        for (workout in repaired.workouts) {
            for (liftDraft in workout.workoutLifts.orEmpty()) {
                val sets = liftDraft.workoutLiftSets.orEmpty().size
                assertTrue(sets >= 2, "Active lift must keep ≥2 sets: ${liftDraft.liftId}=$sets")
            }
        }
    }

    @Test
    fun protectedLowerAnchorIsNotTrimmed() {
        // Friday Bicep = 4.0 (curl), Saturday Bicep = 2.0 (chin-up secondary × 4). Saturday also has
        // a squat at 2 sets (protected lower-anchor floor). The repair must trim the chin-up (the Bicep
        // contributor), never the squat.
        val context = bicepSpecializationContext()
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout("Arm A", "Friday", lift("barbell-curl", 4, isBicepPrimary = true)),
                        workout(
                            "Lower B",
                            "Saturday",
                            lift("back-squat", 2, pattern = "SquatPattern", primary = "Quad"),
                            lift("chin-up", 4, isBicepSecondary = true, pattern = "VerticalPull", primary = "Back"),
                        ),
                    ),
            )

        val repaired = repairWeeklyVolume(program, emptyMap(), context)

        val squatSets = repaired.workouts[1].workoutLifts.orEmpty()[0].workoutLiftSets.orEmpty().size
        assertTrue(squatSets == 2, "Protected squat must not be trimmed to clear a Bicep violation: squat sets=$squatSets")
    }

    @Test
    fun trimsGapTwoHeavyAxialModerateQuadViolation() {
        // Monday + Wednesday squat days (gap 2). Quad 4.0 each session is HeavyAxial-moderate → required
        // gap 3. Repair trims the later day first (Wednesday squat 4 → 2 sets) while keeping the 2-set floor.
        val context = lowerBodyContext(weekdays = listOf("Monday", "Wednesday"))
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout("Lower A", "Monday", lift("back-squat", 4, pattern = "SquatPattern", primary = "Quad")),
                        workout("Lower B", "Wednesday", lift("back-squat", 4, pattern = "SquatPattern", primary = "Quad")),
                    ),
            )

        val repaired = repairWeeklyVolume(program, emptyMap(), context)

        val mondayStress = computeWorkoutMuscleSessionStress(repaired.workouts[0], context)["Quad"]!!
        val wednesdayQuad = computeWorkoutMuscleVolume(repaired.workouts[1], context)["Quad"] ?: 0.0
        assertTrue(
            !muscleRecoveryGapViolated(
                mondayStress,
                wednesdayQuad,
                calendarDayIndexGap(0, 2),
            ),
            "Gap-2 heavy-axial moderate quad pair should be repaired: Mon=${mondayStress.fractionalSets} Wed=$wednesdayQuad",
        )
        for (workout in repaired.workouts) {
            for (liftDraft in workout.workoutLifts.orEmpty()) {
                val sets = liftDraft.workoutLiftSets.orEmpty().size
                assertTrue(sets >= 2, "Active lift must keep ≥2 sets: ${liftDraft.liftId}=$sets")
            }
        }
    }

    @Test
    fun escalatesGluteStressClassFromSquatSecondariesUsesStandardRecovery() {
        // Glute credit from squat secondaries uses isolation recovery (48h), not heavy-axial gap-3.
        val context = lowerBodyContext(weekdays = listOf("Monday", "Wednesday"))
        val program =
            ProgramDraft(
                name = "Test",
                cycleLength = 4,
                workouts =
                    listOf(
                        workout("Lower A", "Monday", lift("back-squat", 8, pattern = "SquatPattern", primary = "Quad")),
                        workout("Lower B", "Wednesday", lift("back-squat", 4, pattern = "SquatPattern", primary = "Quad")),
                    ),
            )

        val mondayStress = computeWorkoutMuscleSessionStress(program.workouts[0], context)["Glute"]!!
        val wednesdayGlute = computeWorkoutMuscleVolume(program.workouts[1], context)["Glute"] ?: 0.0
        assertEquals(LiftStressClass.Isolation, mondayStress.stressClass)
        assertTrue(
            !muscleRecoveryGapViolated(
                mondayStress,
                wednesdayGlute,
                calendarDayIndexGap(0, 2),
            ),
            "Glute squat-secondaries use standard 48h (gap-2) recovery: Mon=${mondayStress.fractionalSets} Wed=$wednesdayGlute",
        )
    }

    // --- fixtures ---

    private fun quadSpecializationContext(weekdays: List<String>): ProgramGenerationContext =
        ProgramGenerationContext(
            trainingAge = "Intermediate",
            lastTrained = null,
            sessionLengthMinutes = null,
            trainingDaysPerWeek = weekdays.size,
            weekdays = weekdays,
            specialization = mapOf("Quad" to "Specialize"),
            liftsById = mapOf(
                "barbell-back-squat" to liftContext("barbell-back-squat", "SquatPattern", "Quad", emptyList()),
            ),
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = setOf("barbell-back-squat"),
            movementPatternByLiftId = mapOf("barbell-back-squat" to "SquatPattern"),
            liftEffectivenessByLiftId = emptyMap(),
        )

    private fun bicepSpecializationContext(weekdays: List<String> = listOf("Friday", "Saturday")): ProgramGenerationContext =
        ProgramGenerationContext(
            trainingAge = "Intermediate",
            lastTrained = null,
            sessionLengthMinutes = null,
            trainingDaysPerWeek = weekdays.size,
            weekdays = weekdays,
            specialization = mapOf("Bicep" to "Specialize"),
            liftsById = mapOf(
                "barbell-curl" to liftContext("barbell-curl", "ElbowFlexion", "Bicep", emptyList()),
                "lat-pulldown" to liftContext("lat-pulldown", "VerticalPull", "Back", listOf("Bicep")),
                "chin-up" to liftContext("chin-up", "VerticalPull", "Back", listOf("Bicep")),
                "back-squat" to liftContext("back-squat", "SquatPattern", "Quad", listOf("Glute")),
                "hip-thrust" to liftContext("hip-thrust", "HipHinge", "Glute", emptyList()),
            ),
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = setOf("barbell-curl", "lat-pulldown", "chin-up", "back-squat", "hip-thrust"),
            movementPatternByLiftId = mapOf(
                "barbell-curl" to "ElbowFlexion",
                "lat-pulldown" to "VerticalPull",
                "chin-up" to "VerticalPull",
                "back-squat" to "SquatPattern",
                "hip-thrust" to "HipHinge",
            ),
            liftEffectivenessByLiftId = emptyMap(),
        )

    private fun lowerBodyContext(weekdays: List<String>): ProgramGenerationContext =
        ProgramGenerationContext(
            trainingAge = "Intermediate",
            lastTrained = null,
            sessionLengthMinutes = null,
            trainingDaysPerWeek = weekdays.size,
            weekdays = weekdays,
            specialization = mapOf("Quad" to "Specialize", "Glute" to "Maintenance"),
            liftsById = mapOf(
                "back-squat" to liftContext("back-squat", "SquatPattern", "Quad", listOf("Glute")),
            ),
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = setOf("back-squat"),
            movementPatternByLiftId = mapOf("back-squat" to "SquatPattern"),
            liftEffectivenessByLiftId = emptyMap(),
        )

    private fun liftContext(id: String, pattern: String, primary: String, secondary: List<String>): LiftContext =
        LiftContext(
            id = id,
            name = id,
            movementPattern = pattern,
            movementPatterns = listOf(pattern),
            primaryMuscles = listOf(primary),
            secondaryMuscles = secondary,
            proven = true,
        )

    private fun workout(name: String, weekday: String, vararg lifts: ProgramLiftDraft): ProgramWorkoutDraft =
        ProgramWorkoutDraft(
            name = name,
            weekday = weekday,
            workoutLifts = lifts.toMutableList(),
        )

    private fun lift(
        liftId: String,
        sets: Int,
        isBicepPrimary: Boolean = false,
        isBicepSecondary: Boolean = false,
        @Suppress("UNUSED_PARAMETER") pattern: String = "ElbowFlexion",
        @Suppress("UNUSED_PARAMETER") primary: String = "Bicep",
    ): ProgramLiftDraft =
        ProgramLiftDraft(
            liftId = liftId,
            position = 0,
            workoutLiftSets = (1..sets).map { index ->
                LiftSetDraft(
                    position = index,
                    setType = SetType.Standard,
                    repMin = 8,
                    repMax = 12,
                    rirGoal = 2,
                )
            }.toMutableList(),
        )
}