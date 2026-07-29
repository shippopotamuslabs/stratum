package com.stratum.domain.program.generation.naming

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HeuristicProgramNamingTest {

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-07-28T18:03:05Z"), ZoneId.of("UTC"))

    @Nested
    @DisplayName("program name")
    inner class ProgramName {
        @Test
        @DisplayName("uses local date/time from clock as yyyy-MM-dd HH:mm:ss")
        fun fixedClockFormatsProgramName() {
            val draft = programOf(workout(position = 0, liftIds = emptyList()))
            val named = applyHeuristicProgramNames(draft, emptyMap(), fixedClock)
            assertEquals("2026-07-28 18:03:05", named.name)
        }
    }

    @Nested
    @DisplayName("workout region threshold")
    inner class RegionThreshold {
        @Test
        @DisplayName("Given 8/10 upper lifts then Upper A")
        fun eightOfTenUpperIsUpper() {
            val lifts = upperIds(8) + lowerIds(2)
            val named = nameSingle(lifts)
            assertEquals("Upper A", named.workouts.single().name)
        }

        @Test
        @DisplayName("Given 8/10 lower lifts then Lower A")
        fun eightOfTenLowerIsLower() {
            val lifts = lowerIds(8) + upperIds(2)
            val named = nameSingle(lifts)
            assertEquals("Lower A", named.workouts.single().name)
        }

        @Test
        @DisplayName("Given 5/10 upper and 5/10 lower then Full Body A")
        fun balancedIsFullBody() {
            val lifts = upperIds(5) + lowerIds(5)
            val named = nameSingle(lifts)
            assertEquals("Full Body A", named.workouts.single().name)
        }

        @Test
        @DisplayName("Given exactly 7/10 upper (70 percent) then Full Body A — strict greater-than")
        fun exactlySeventyPercentIsFullBody() {
            val lifts = upperIds(7) + lowerIds(3)
            val named = nameSingle(lifts)
            assertEquals("Full Body A", named.workouts.single().name)
        }

        @Test
        @DisplayName("Given empty workout lifts then Full Body A")
        fun emptyWorkoutIsFullBody() {
            val named = nameSingle(emptyList())
            assertEquals("Full Body A", named.workouts.single().name)
        }

        @Test
        @DisplayName("Given unknown lift ids only then Full Body A")
        fun unknownLiftsAreFullBody() {
            val lifts = listOf("missing-1", "missing-2")
            val draft = programOf(workout(position = 0, liftIds = lifts))
            val named = applyHeuristicProgramNames(draft, emptyMap(), fixedClock)
            assertEquals("Full Body A", named.workouts.single().name)
        }

        @Test
        @DisplayName("Given core-only lifts dilute upper share to 70 percent then Full Body A")
        fun coreOnlyDilutesUpper() {
            val lifts = upperIds(7) + coreIds(3)
            val named = nameSingle(lifts)
            assertEquals("Full Body A", named.workouts.single().name)
        }

        @Test
        @DisplayName("Given LowerBack-primary HipHinge deadlift counts as Lower")
        fun deadliftPrimaryLowerBackIsLower() {
            val dl = "barbell-deadlift"
            val catalog =
                mapOf(
                    dl to
                        liftContext(
                            id = dl,
                            primary = setOf(MuscleGroup.LowerBack),
                            pattern = MovementPattern.HipHinge,
                        ),
                )
            val draft = programOf(workout(position = 0, liftIds = listOf(dl)))
            val named = applyHeuristicProgramNames(draft, catalog, fixedClock)
            assertEquals("Lower A", named.workouts.single().name)
        }

        @Test
        @DisplayName("Given 7 lower + 1 deadlift + 2 upper then Lower A (hinge not Neither)")
        fun deadliftDoesNotPushNearThresholdToFullBody() {
            // Without pattern fallback: 7/10 lower → Full Body. With hinge=Lower: 8/10 → Lower.
            val lowers = lowerIds(7)
            val dl = "barbell-deadlift"
            val uppers = upperIds(2)
            val lifts = lowers + dl + uppers
            val catalog =
                catalogFor(lowers + uppers) +
                    (dl to
                        liftContext(
                            id = dl,
                            primary = setOf(MuscleGroup.LowerBack),
                            pattern = MovementPattern.HipHinge,
                        ))
            val draft = programOf(workout(position = 0, liftIds = lifts))
            val named = applyHeuristicProgramNames(draft, catalog, fixedClock)
            assertEquals("Lower A", named.workouts.single().name)
        }
    }

    @Nested
    @DisplayName("letter suffixes")
    inner class LetterSuffixes {
        @Test
        @DisplayName("Given Upper Lower Upper then Upper A, Lower A, Upper B")
        fun independentPrefixCounters() {
            val w0 = (0 until 8).map { "u0-$it" }
            val w1 = (0 until 8).map { "l1-$it" }
            val w2 = (0 until 8).map { "u2-$it" }
            val catalog =
                catalogUpper(w0) + catalogLower(w1) + catalogUpper(w2)
            val draft =
                programOf(
                    workout(position = 0, liftIds = w0),
                    workout(position = 1, liftIds = w1),
                    workout(position = 2, liftIds = w2),
                )
            val named = applyHeuristicProgramNames(draft, catalog, fixedClock)
            assertEquals(listOf("Upper A", "Lower A", "Upper B"), named.workouts.map { it.name })
        }

        @Test
        @DisplayName("Given two Full Body sessions then Full Body A then Full Body B")
        fun twoFullBodyLetters() {
            val aUpper = (0 until 5).map { "fa-u-$it" }
            val aLower = (0 until 5).map { "fa-l-$it" }
            val bUpper = (0 until 5).map { "fb-u-$it" }
            val bLower = (0 until 5).map { "fb-l-$it" }
            val catalog =
                catalogUpper(aUpper + bUpper) + catalogLower(aLower + bLower)
            val draft =
                programOf(
                    workout(position = 0, liftIds = aUpper + aLower),
                    workout(position = 1, liftIds = bUpper + bLower),
                )
            val named = applyHeuristicProgramNames(draft, catalog, fixedClock)
            assertEquals(listOf("Full Body A", "Full Body B"), named.workouts.map { it.name })
        }

        @Test
        @DisplayName("Given list order differs from position then counters follow position")
        fun positionOrderNotListOrder() {
            val laterIds = upperIds(8).map { "later-$it" }
            val earlierIds = upperIds(8).map { "earlier-$it" }
            val catalog = catalogUpper(laterIds + earlierIds)
            val draft =
                programOf(
                    workout(position = 1, liftIds = laterIds),
                    workout(position = 0, liftIds = earlierIds),
                )
            val named = applyHeuristicProgramNames(draft, catalog, fixedClock)
            assertEquals("Upper B", named.workouts[0].name)
            assertEquals("Upper A", named.workouts[1].name)
        }

        @Test
        @DisplayName("letterForIndex uses A through Z then AA AB")
        fun letterIndexBeyondZ() {
            assertEquals("A", letterForIndex(0))
            assertEquals("Z", letterForIndex(25))
            assertEquals("AA", letterForIndex(26))
            assertEquals("AB", letterForIndex(27))
        }
    }

    @Nested
    @DisplayName("lift title contribution")
    inner class LiftClassification {
        @Test
        fun lowerPrimaryIsLower() {
            assertEquals(
                LiftTitleContribution.Lower,
                classifyLiftTitleContribution(setOf(MuscleGroup.Quad)),
            )
        }

        @Test
        fun chestPrimaryIsUpper() {
            assertEquals(
                LiftTitleContribution.Upper,
                classifyLiftTitleContribution(setOf(MuscleGroup.Chest)),
            )
        }

        @Test
        fun coreOnlyIsNeither() {
            assertEquals(
                LiftTitleContribution.Neither,
                classifyLiftTitleContribution(setOf(MuscleGroup.Ab)),
            )
        }

        @Test
        fun emptyPrimaryWithHipHingeIsLower() {
            assertEquals(
                LiftTitleContribution.Lower,
                classifyLiftTitleContribution(
                    primaryMuscles = emptySet(),
                    movementPattern = MovementPattern.HipHinge,
                ),
            )
        }

        @Test
        fun lowerBackPrimaryWithHipHingeIsLower() {
            assertEquals(
                LiftTitleContribution.Lower,
                classifyLiftTitleContribution(
                    primaryMuscles = setOf(MuscleGroup.LowerBack),
                    movementPattern = MovementPattern.HipHinge,
                ),
            )
        }

        @Test
        fun mixedLowerAndUpperPrimaryPrefersLower() {
            assertEquals(
                LiftTitleContribution.Lower,
                classifyLiftTitleContribution(
                    setOf(MuscleGroup.Chest, MuscleGroup.Glute),
                ),
            )
        }

        @Test
        fun emptyIsNeither() {
            assertEquals(
                LiftTitleContribution.Neither,
                classifyLiftTitleContribution(emptySet()),
            )
        }
    }

    // --- helpers ---

    private fun nameSingle(liftIds: List<String>): ProgramDraft {
        val draft = programOf(workout(position = 0, liftIds = liftIds))
        return applyHeuristicProgramNames(draft, catalogFor(liftIds), fixedClock)
    }

    private fun programOf(vararg workouts: ProgramWorkoutDraft): ProgramDraft =
        ProgramDraft(
            name = "Generated Program",
            cycleLength = workouts.size.coerceAtLeast(1),
            workouts = workouts.toList(),
        )

    private fun workout(
        position: Int,
        liftIds: List<String>,
    ): ProgramWorkoutDraft =
        ProgramWorkoutDraft(
            name = "Session ${position + 1}",
            position = position,
            workoutLifts =
                liftIds
                    .mapIndexed { index, id ->
                        ProgramLiftDraft(liftId = id, position = index)
                    }
                    .toMutableList(),
        )

    private fun upperIds(n: Int): List<String> = (0 until n).map { "upper-$it" }

    private fun lowerIds(n: Int): List<String> = (0 until n).map { "lower-$it" }

    private fun coreIds(n: Int): List<String> = (0 until n).map { "core-$it" }

    private fun catalogFor(liftIds: List<String>): Map<String, LiftContext> =
        liftIds.associateWith { id ->
            when {
                id.startsWith("upper") ->
                    liftContext(id, setOf(MuscleGroup.Chest), MovementPattern.HorizontalPress)
                id.startsWith("lower") ->
                    liftContext(id, setOf(MuscleGroup.Quad), MovementPattern.SquatPattern)
                id.startsWith("core") ->
                    liftContext(id, setOf(MuscleGroup.Ab), MovementPattern.SpinalFlexion)
                else ->
                    liftContext(id, emptySet(), MovementPattern.HorizontalPress)
            }
        }

    private fun catalogUpper(ids: List<String>): Map<String, LiftContext> =
        ids.associateWith {
            liftContext(it, setOf(MuscleGroup.Chest), MovementPattern.HorizontalPress)
        }

    private fun catalogLower(ids: List<String>): Map<String, LiftContext> =
        ids.associateWith {
            liftContext(it, setOf(MuscleGroup.Quad), MovementPattern.SquatPattern)
        }

    private fun liftContext(
        id: String,
        primary: Set<MuscleGroup>,
        pattern: MovementPattern,
    ): LiftContext =
        LiftContext(
            id = id,
            name = id,
            movementPattern = pattern,
            movementPatterns = setOf(pattern),
            primaryMuscles = primary,
            secondaryMuscles = emptySet(),
            fatigueProfile = FatigueProfile.Low,
        )
}
