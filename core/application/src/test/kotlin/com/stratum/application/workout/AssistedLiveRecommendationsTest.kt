package com.stratum.application.workout

import com.stratum.core.testing.workout.FakeFatigueModel
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
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.RecommendationEngine
import com.stratum.domain.workout.RenderedSet
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.RenderedWorkoutLift
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.fatigue.PopulationFatigueModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * STR-326: assisted lifts receive live recommendations after a set is logged.
 * The performed assistance amount carries across the remaining straight sets
 * (stored negative; the UI displays the magnitude) and reps project load-free,
 * like bodyweight: effective load is bodyweight minus assistance and bodyweight
 * is not measured. Cold open stays unseeded — the snapshot caps assistance at
 * 0 and programs carry no planned assistance.
 */
@DisplayName("Assisted lifts: live recommendations (STR-326)")
class AssistedLiveRecommendationsTest {

    private val liftId = LiftId("L-as")
    private val workoutLiftId = WorkoutLiftId("wl-as")
    private val workoutId = WorkoutId("w-as")

    private val useCase = RecomputeRecommendationsUseCase(
        recommendationEngine = RecommendationEngine(PopulationFatigueModel()),
        fatigueModel = FakeFatigueModel(),
    )

    private fun assistedCatalogLift(): Lift = Lift(
        id = liftId,
        name = "Assisted Dip",
        movementPatterns = setOf(MovementPattern.ElbowExtension),
        primaryMuscles = setOf(MuscleGroup.Tricep),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    /**
     * Three straight assisted sets mirroring Sean's Assisted Dip: the first set
     * carries the 8-12 range, the rest carry no range; RIR goal 2 throughout.
     * Logged values store the assistance negative, as the ViewModel persists it.
     */
    private fun assistedThreeSets(
        logged: Map<Int, LoggedSetValue> = emptyMap(),
    ): RenderedWorkout {
        val sets = (0..2).map { position ->
            RenderedSet(
                slotId = SetSlotId.Template(LiftSetId("ls-as-$position")),
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                position = position,
                setType = SetType.Standard,
                isTopSet = position == 0,
                repMin = if (position == 0) 8 else 0,
                repMax = if (position == 0) 12 else 0,
                rirGoal = 2,
                restSeconds = 120,
                recommendedWeight = Weight(0.0, Weight.WeightUnit.Lb),
                recommendedReps = 0,
                loggedValue = logged[position],
            )
        }
        return RenderedWorkout(
            workoutId = workoutId,
            workoutName = "Upper B",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Assisted Dip",
                    progressionScheme = ProgressionScheme.Double,
                    sets = sets,
                    loadType = LoadType.Assisted,
                ),
            ),
        )
    }

    private fun loggedSet(position: Int, assistance: Double, reps: Int, rir: Int) =
        LoggedSetValue(
            slotId = SetSlotId.Template(LiftSetId("ls-as-$position")),
            weight = Weight(-assistance, Weight.WeightUnit.Lb),
            reps = reps,
            rir = rir,
        )

    @Test
    @DisplayName("Cold open produces no recommendations: no assistance exists to carry yet")
    fun `cold open has no assisted recs`() {
        val recommendations = useCase(
            assistedThreeSets(),
            mapOf(liftId to assistedCatalogLift()),
            priorRecommendations = emptyMap(),
        )

        assertTrue(recommendations.isEmpty())
    }

    @Test
    @DisplayName("Logging set 1 carries its assistance to sets 2 and 3 with fatigue-projected reps")
    fun `logged first set carries assistance forward`() {
        // Sean's session: set 1 logged at 50 lb assistance x 8 @ RIR 2.
        val rendered = assistedThreeSets(logged = mapOf(0 to loggedSet(0, 50.0, 8, 2)))

        val recommendations = useCase(
            rendered,
            mapOf(liftId to assistedCatalogLift()),
            priorRecommendations = emptyMap(),
        )

        val set2 = recommendations[SetSlotId.Template(LiftSetId("ls-as-1"))]
        val set3 = recommendations[SetSlotId.Template(LiftSetId("ls-as-2"))]
        assertNotNull(set2)
        assertNotNull(set3)
        assertEquals(-50.0, set2!!.weight.amount)
        assertEquals(-50.0, set3!!.weight.amount)
        // Failure capacity 8 + 2 = 10, population slope -2.0: set 2 -> 8 @ RIR 0
        // -> 6 at the RIR 2 goal; set 3 -> 6 @ RIR 0 -> 4.
        assertEquals(6, set2.reps)
        assertEquals(4, set3.reps)
    }

    @Test
    @DisplayName("Logging set 2 as recommended keeps set 3 on the same assistance")
    fun `second set log keeps assistance for set 3`() {
        val firstPass = useCase(
            assistedThreeSets(logged = mapOf(0 to loggedSet(0, 50.0, 8, 2))),
            mapOf(liftId to assistedCatalogLift()),
            priorRecommendations = emptyMap(),
        )
        val rendered = assistedThreeSets(
            logged = mapOf(
                0 to loggedSet(0, 50.0, 8, 2),
                1 to loggedSet(1, 50.0, firstPass.getValue(SetSlotId.Template(LiftSetId("ls-as-1"))).reps, 2),
            ),
        )

        val recommendations = useCase(
            rendered,
            mapOf(liftId to assistedCatalogLift()),
            priorRecommendations = firstPass,
        )

        val set3 = recommendations[SetSlotId.Template(LiftSetId("ls-as-2"))]
        assertNotNull(set3)
        assertEquals(-50.0, set3!!.weight.amount)
        assertEquals(4, set3.reps)
    }

    @Test
    @DisplayName("A changed assistance on a later set re-anchors the carry on the new amount")
    fun `changed assistance re-anchors`() {
        val rendered = assistedThreeSets(
            logged = mapOf(
                0 to loggedSet(0, 50.0, 8, 2),
                1 to loggedSet(1, 40.0, 6, 2),
            ),
        )

        val recommendations = useCase(
            rendered,
            mapOf(liftId to assistedCatalogLift()),
            priorRecommendations = emptyMap(),
        )

        assertEquals(-40.0, recommendations[SetSlotId.Template(LiftSetId("ls-as-2"))]?.weight?.amount)
    }

    @Test
    @DisplayName("Shown reps and RIR at a changed assistance re-anchor the next set on the new performance")
    fun `changed assistance at shown reps re-anchors reps`() {
        // A fractional slope separates the two geometries: stable first-set
        // geometry and re-anchored geometry round to different reps.
        val fatigue = mapOf(liftId to FatigueDecay(slope = -2.5, intercept = null))
        val firstPass = useCase(
            assistedThreeSets(logged = mapOf(0 to loggedSet(0, 50.0, 8, 2))),
            mapOf(liftId to assistedCatalogLift()),
            fatigueByLiftId = fatigue,
            priorRecommendations = emptyMap(),
        )
        val shownSet2 = firstPass.getValue(SetSlotId.Template(LiftSetId("ls-as-1")))
        // Capacity 10, slope -2.5: set 2 at RIR 0 is 7.5, rounding to 8, so 6 at goal.
        assertEquals(-50.0, shownSet2.weight.amount)
        assertEquals(6, shownSet2.reps)

        // Set 2 logged at exactly the shown reps and RIR, but 40 lb assistance
        // instead of 50: the load changed, so the log is not exact.
        val rendered = assistedThreeSets(
            logged = mapOf(
                0 to loggedSet(0, 50.0, 8, 2),
                1 to loggedSet(1, 40.0, shownSet2.reps, 2),
            ),
        )
        val recommendations = useCase(
            rendered,
            mapOf(liftId to assistedCatalogLift()),
            fatigueByLiftId = fatigue,
            priorRecommendations = firstPass,
        )

        val set3 = recommendations[SetSlotId.Template(LiftSetId("ls-as-2"))]
        assertNotNull(set3)
        assertEquals(-40.0, set3!!.weight.amount)
        // Re-anchored on set 2 (capacity 6 + 2 = 8): 8 - 2.5 = 5.5, rounding to
        // 6 at RIR 0, so 4 at goal. Stable first-set geometry would project
        // 10 - 5 = 5 at RIR 0, so 3.
        assertEquals(4, set3.reps)
    }

    @Test
    @DisplayName("Single assisted set produces no live recommendations")
    fun `single assisted set has no live recs`() {
        val rendered = assistedThreeSets(logged = mapOf(0 to loggedSet(0, 50.0, 8, 2))).let { workout ->
            val lift = workout.lifts.single().copy(sets = workout.lifts.single().sets.take(1))
            workout.copy(lifts = listOf(lift))
        }

        val recommendations = useCase(
            rendered,
            mapOf(liftId to assistedCatalogLift()),
            priorRecommendations = emptyMap(),
        )

        assertTrue(recommendations.isEmpty())
    }
}
