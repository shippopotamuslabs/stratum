package com.stratum.domain.workout

import com.stratum.core.testing.workout.FakeFatigueModel
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.fatigue.PopulationFatigueModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * STR-29: bodyweight lifts receive live rep recommendations after a set is logged.
 * Weight recommendations stay suppressed (always zero); only reps are projected, via
 * the load-free [com.stratum.domain.workout.bodyweight.BodyweightRepProjection].
 */
@DisplayName("Bodyweight lifts: live rep recommendations (STR-29)")
class BodyweightLiveRecommendationsTest {

    private val liftId = LiftId("L-bw")
    private val workoutLiftId = WorkoutLiftId("wl-bw")
    private val workoutId = WorkoutId("w-bw")

    private val useCase = RecomputeRecommendationsUseCase(
        recommendationEngine = RecommendationEngine(PopulationFatigueModel()),
        fatigueModel = FakeFatigueModel(),
    )

    private fun bw(weight: Double = 0.0) = Weight(weight, Weight.WeightUnit.Lb)

    private fun catalogLift(): Lift = Lift(
        id = liftId,
        name = "Pull-ups",
        movementPatterns = setOf(MovementPattern.VerticalPull),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    /**
     * Three straight bodyweight sets; the first [loggedReps]/[loggedRir] optionally
     * logged. Snapshot prescription is 3x[prescribedReps] @ RIR [rirGoal] with rep
     * range [repMin]..[repMax].
     */
    private fun bodyweightThreeSets(
        prescribedReps: Int = 10,
        repMin: Int = 8,
        repMax: Int = 12,
        rirGoal: Int = 1,
        loggedReps: Int? = null,
        loggedRir: Int? = null,
    ): RenderedWorkout {
        val sets = (0..2).map { position ->
            val logged = if (position == 0 && loggedReps != null) {
                LoggedSetValue(
                    slotId = SetSlotId.Template(LiftSetId("ls-bw-$position")),
                    weight = bw(),
                    reps = loggedReps,
                    rir = loggedRir,
                )
            } else {
                null
            }
            RenderedSet(
                slotId = SetSlotId.Template(LiftSetId("ls-bw-$position")),
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                position = position,
                setType = SetType.Standard,
                isTopSet = position == 0,
                repMin = repMin,
                repMax = repMax,
                rirGoal = rirGoal,
                restSeconds = 120,
                recommendedWeight = bw(),
                recommendedReps = prescribedReps,
                loggedValue = logged,
            )
        }
        return RenderedWorkout(
            workoutId = workoutId,
            workoutName = "Workout 1",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Pull-ups",
                    progressionScheme = ProgressionScheme.Double,
                    sets = sets,
                    loadType = LoadType.Bodyweight,
                ),
            ),
        )
    }

    @Test
    @DisplayName("Cold open projects fatigue reps for sets after the first, weight stays zero")
    fun `cold open projects bodyweight fatigue reps`() {
        val rendered = bodyweightThreeSets()

        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())

        // First set has no live rec; sets 2-3 decay from the 10 @ RIR 1 prescription
        // with the population slope (-2.0): baseline 11 → 9 @ RIR 0 → 8; then 7 → 6.
        assertNull(recommendations[SetSlotId.Template(LiftSetId("ls-bw-0"))])
        val set2 = recommendations[SetSlotId.Template(LiftSetId("ls-bw-1"))]
        val set3 = recommendations[SetSlotId.Template(LiftSetId("ls-bw-2"))]
        assertNotNull(set2)
        assertNotNull(set3)
        assertEquals(8, set2!!.reps)
        assertEquals(6, set3!!.reps)
        assertEquals(0.0, set2.weight.amount)
        assertEquals(0.0, set3.weight.amount)
    }

    @Test
    @DisplayName("Logging the first set exactly as prescribed keeps stable block geometry")
    fun `exact log keeps stable bodyweight rep geometry`() {
        val rendered = bodyweightThreeSets(loggedReps = 10, loggedRir = 1)

        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())

        // Stable geometry from the first set's logged 10 @ RIR 1 — identical to cold open.
        assertEquals(8, recommendations[SetSlotId.Template(LiftSetId("ls-bw-1"))]?.reps)
        assertEquals(6, recommendations[SetSlotId.Template(LiftSetId("ls-bw-2"))]?.reps)
        assertTrue(recommendations.values.all { it.weight.amount == 0.0 })
    }

    @Test
    @DisplayName("Off-track log re-anchors the projection on the performed set")
    fun `off-track log re-anchors bodyweight projection`() {
        val rendered = bodyweightThreeSets(loggedReps = 8, loggedRir = 2)

        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())

        // Re-anchored on 8 @ RIR 2 (baseline 10): set 2 → 10 - 2 = 8 @ RIR 0 → 7;
        // set 3 → 10 - 4 = 6 @ RIR 0 → 5.
        assertEquals(7, recommendations[SetSlotId.Template(LiftSetId("ls-bw-1"))]?.reps)
        assertEquals(5, recommendations[SetSlotId.Template(LiftSetId("ls-bw-2"))]?.reps)
    }

    @Test
    @DisplayName("Per-lift fatigue slope drives the bodyweight projection when present")
    fun `per-lift slope drives bodyweight projection`() {
        val rendered = bodyweightThreeSets(loggedReps = 10, loggedRir = 1)
        val fatigue = mapOf(liftId to FatigueDecay(slope = -3.0, intercept = 0.0))

        val recommendations = useCase(
            rendered,
            mapOf(liftId to catalogLift()),
            priorRecommendations = emptyMap(),
            fatigueByLiftId = fatigue,
        )

        // Baseline 11 with slope -3.0: set 2 → 8 @ RIR 0 → 7; set 3 → 5 @ RIR 0 → 4.
        assertEquals(7, recommendations[SetSlotId.Template(LiftSetId("ls-bw-1"))]?.reps)
        assertEquals(4, recommendations[SetSlotId.Template(LiftSetId("ls-bw-2"))]?.reps)
    }

    @Test
    @DisplayName("Projected reps respect the set repMax cap")
    fun `bodyweight projection respects repMax cap`() {
        val rendered = bodyweightThreeSets(repMax = 7, loggedReps = 10, loggedRir = 1)

        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())

        // Uncapped set 2 would be 8; repMax 7 caps it.
        assertEquals(7, recommendations[SetSlotId.Template(LiftSetId("ls-bw-1"))]?.reps)
        assertEquals(6, recommendations[SetSlotId.Template(LiftSetId("ls-bw-2"))]?.reps)
    }

    @Test
    @DisplayName("Prior live rows are honored when the second set is logged exactly")
    fun `exact second log keeps stable geometry with priors`() {
        val coldOpen = useCase(
            bodyweightThreeSets(),
            mapOf(liftId to catalogLift()),
            priorRecommendations = emptyMap(),
        )
        // Log set 2 exactly as the cold-open overlay showed (8 @ RIR 1).
        val rendered = bodyweightThreeSets(loggedReps = 10, loggedRir = 1).let { workout ->
            val sets = workout.lifts.single().sets.mapIndexed { index, set ->
                if (index == 1) {
                    set.copy(
                        loggedValue = LoggedSetValue(
                            slotId = set.slotId,
                            weight = bw(),
                            reps = coldOpen[set.slotId]!!.reps,
                            rir = 1,
                        ),
                    )
                } else {
                    set
                }
            }
            workout.copy(lifts = listOf(workout.lifts.single().copy(sets = sets)))
        }

        val recommendations = useCase(
            rendered,
            mapOf(liftId to catalogLift()),
            priorRecommendations = coldOpen,
        )

        // Set 3 keeps the stable block projection (6), weight stays zero.
        assertEquals(6, recommendations[SetSlotId.Template(LiftSetId("ls-bw-2"))]?.reps)
        assertEquals(0.0, recommendations[SetSlotId.Template(LiftSetId("ls-bw-2"))]?.weight?.amount)
    }

    @Test
    @DisplayName("Single bodyweight set produces no live recommendations")
    fun `single bodyweight set has no live recs`() {
        val rendered = bodyweightThreeSets().let { workout ->
            val lift = workout.lifts.single().copy(sets = workout.lifts.single().sets.take(1))
            workout.copy(lifts = listOf(lift))
        }

        val recommendations = useCase(rendered, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())

        assertTrue(recommendations.isEmpty())
    }
}
