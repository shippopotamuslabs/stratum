package com.stratum.application.workout

import com.stratum.core.testing.workout.FakeFatigueModel
import com.stratum.domain.lift.Lift
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
import com.stratum.domain.workout.myorep.MyoRepLifecycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.stratum.domain.workout.dropset.DropSetRepProjection
import com.stratum.domain.workout.dropset.EstimateDropSetWeightUseCase
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.RecommendationEngine
import com.stratum.domain.workout.RenderedSet
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.RenderedWorkoutLift
import com.stratum.domain.workout.SetSlotId

/**
 * Scenario matrix for live intensifier recommendations (myo + drop).
 *
 * Complements [RecomputeIntensifierRecommendationsTest] and lifecycle/placement suites.
 * Each case names a product path that has bitten us or is easy to regress.
 *
 * ## Coverage map (what this file owns)
 * - Multi-cluster myo load isolation + no-rec without activation log
 * - Activation-too-heavy boundaries for mini load reduce
 * - Drop prior assumptions (baseline prescription, skip-ahead blanks)
 * - Mixed post-myo standard+drop chains
 * - Long unlogged drop chains; third myo mini re-anchor
 */
@DisplayName("Intensifier recommendation scenarios")
class IntensifierRecommendationScenariosTest {

    private val liftId = LiftId("L-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val workoutId = WorkoutId("w-1")

    private val useCase = RecomputeRecommendationsUseCase(
        recommendationEngine = RecommendationEngine(PopulationFatigueModel()),
        fatigueModel = FakeFatigueModel(),
        estimateDropSetWeight = EstimateDropSetWeightUseCase(),
    )

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun catalogLift(): Lift = Lift(
        id = liftId,
        name = "Curl",
        movementPatterns = setOf(MovementPattern.ElbowFlexion),
        primaryMuscles = setOf(MuscleGroup.Bicep),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    private fun rendered(sets: List<RenderedSet>) = RenderedWorkout(
        workoutId = workoutId,
        workoutName = "W",
        lifts = listOf(
            RenderedWorkoutLift(
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                liftName = "Curl",
                progressionScheme = ProgressionScheme.Double,
                sets = sets,
            ),
        ),
    )

    private fun set(
        id: String,
        position: Int,
        type: SetType,
        isTopSet: Boolean,
        weight: Double,
        logged: LoggedSetValue? = null,
        repMin: Int = 0,
        repMax: Int = 0,
        rirGoal: Int = 0,
        recommendedReps: Int = repMin.coerceAtLeast(1),
    ) = RenderedSet(
        slotId = SetSlotId.Template(LiftSetId(id)),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = type,
        isTopSet = isTopSet,
        repMin = repMin,
        repMax = repMax,
        rirGoal = rirGoal,
        restSeconds = if (type == SetType.DropSet) 0 else 120,
        recommendedWeight = lb(weight),
        recommendedReps = recommendedReps,
        loggedValue = logged,
    )

    private fun overlayMyo(
        id: String,
        position: Int,
        weight: Double = 65.0,
        logged: LoggedSetValue? = null,
        recommendedReps: Int = 5,
    ) = RenderedSet(
        slotId = SetSlotId.Overlay(OverlaySetInstanceId(if (id.startsWith("mini")) id else "mini-$id")),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.MyoRep,
        isTopSet = false,
        repMin = 0,
        repMax = 0,
        rirGoal = 0,
        restSeconds = 15,
        recommendedWeight = lb(weight),
        recommendedReps = recommendedReps,
        loggedValue = logged,
    )

    private fun log(id: String, weight: Double, reps: Int, rir: Int?) =
        LoggedSetValue(SetSlotId.Template(LiftSetId(id)), lb(weight), reps, rir)

    private fun logOverlay(id: String, weight: Double, reps: Int, rir: Int?) =
        LoggedSetValue(
            SetSlotId.Overlay(OverlaySetInstanceId(if (id.startsWith("mini")) id else "mini-$id")),
            lb(weight),
            reps,
            rir,
        )

    // -------------------------------------------------------------------------
    // Multi-cluster myo
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Multi-cluster myo recommendations")
    inner class MultiClusterMyo {

        @Test
        @DisplayName("second-cluster open mini copies act2 load, not heavier act1")
        fun secondClusterMiniUsesOwningActivationLoad() {
            val workout = rendered(
                listOf(
                    set(
                        "act1", 0, SetType.MyoRep, true, 80.0,
                        log("act1", 80.0, 10, 1),
                        repMin = 8, repMax = 12, rirGoal = 1,
                    ),
                    overlayMyo("mini-act1-1", 1, weight = 80.0, logged = logOverlay("mini-act1-1", 80.0, 4, 0)),
                    set(
                        "act2", 2, SetType.MyoRep, false, 50.0,
                        log("act2", 50.0, 12, 1),
                        repMin = 8, repMax = 12, rirGoal = 1,
                    ),
                    overlayMyo("mini-act2-1", 3, weight = 50.0, logged = null),
                ),
            )
            val rec = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[
                SetSlotId.Overlay(OverlaySetInstanceId("mini-act2-1")),
            ]
            assertNotNull(rec)
            assertEquals(50.0, rec!!.weight.amount, 0.01, "must copy act2, not act1 80 lb")
            assertTrue(rec.reps > 0)
        }

        @Test
        @DisplayName("open mini with unlogged activation gets no live rec")
        fun openMiniWithoutActivationLogHasNoRec() {
            val workout = rendered(
                listOf(
                    set("s0", 0, SetType.Standard, true, 65.0, log("s0", 65.0, 8, 1), repMin = 6, repMax = 10, rirGoal = 1),
                    set("act", 1, SetType.MyoRep, false, 65.0, logged = null, repMin = 8, repMax = 12, rirGoal = 1),
                    overlayMyo("mini-orphan", 2, weight = 65.0, logged = null),
                ),
            )
            val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
            assertNull(
                recs[SetSlotId.Overlay(OverlaySetInstanceId("mini-orphan"))],
                "cannot project mini load/reps until activation is logged",
            )
        }

        @Test
        @DisplayName("third mini re-anchors on second mini, not activation")
        fun thirdMiniReanchorsOnSecondMini() {
            val workout = rendered(
                listOf(
                    set(
                        "act", 0, SetType.MyoRep, true, 65.0,
                        log("act", 65.0, 14, 0),
                        repMin = 8, repMax = 12, rirGoal = 0,
                    ),
                    overlayMyo("m1", 1, logged = logOverlay("m1", 65.0, 9, 0)),
                    overlayMyo("m2", 2, logged = logOverlay("m2", 65.0, 6, 0)),
                    overlayMyo("m3", 3, logged = null),
                ),
            )
            val rec = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[
                SetSlotId.Overlay(OverlaySetInstanceId("mini-m3")),
            ]
            assertNotNull(rec)
            // Subsequent mini: last mini 6 − 3 = 3 (not activation 14)
            assertEquals(3, rec!!.reps)
        }
    }

    // -------------------------------------------------------------------------
    // Activation too heavy
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Activation too-heavy mini load reduce")
    inner class ActivationTooHeavy {

        @Test
        @DisplayName("hits default rep floor → too heavy")
        fun atRepFloorIsTooHeavy() {
            val activation = set("act", 0, SetType.MyoRep, true, 100.0, repMin = 10, repMax = 15, rirGoal = 1)
            val logged = log("act", 100.0, MyoRepLifecycle.DEFAULT_REP_FLOOR, 0)
            assertTrue(useCase.isActivationTooHeavy(logged, activation))
        }

        @Test
        @DisplayName("achieved exactly repMin−2 → too heavy")
        fun twoBelowRepMinIsTooHeavy() {
            // achieved = reps + rir − rirGoal = 8 + 0 − 1 = 7; repMin 10 → 10−2=8 → 7 ≤ 8
            val activation = set("act", 0, SetType.MyoRep, true, 100.0, repMin = 10, repMax = 15, rirGoal = 1)
            val logged = log("act", 100.0, 8, 0)
            assertTrue(useCase.isActivationTooHeavy(logged, activation))
        }

        @Test
        @DisplayName("achieved at repMin−1 → not too heavy")
        fun oneBelowRepMinIsNotTooHeavy() {
            // achieved = 9 + 0 − 1 = 8; repMin−2 = 8 → 8 ≤ 8 is still too heavy by the ≤ rule
            // Use achieved = 9: reps 10 + rir 0 − rirGoal 1 = 9; floor margin 8 → not too heavy
            val activation = set("act", 0, SetType.MyoRep, true, 100.0, repMin = 10, repMax = 15, rirGoal = 1)
            val logged = log("act", 100.0, 10, 0)
            assertFalse(useCase.isActivationTooHeavy(logged, activation))
        }

        @Test
        @DisplayName("on-track activation keeps mini weight (no reduce)")
        fun onTrackKeepsMiniWeight() {
            val workout = rendered(
                listOf(
                    set(
                        "act", 0, SetType.MyoRep, true, 70.0,
                        log("act", 70.0, 12, 1),
                        repMin = 10, repMax = 15, rirGoal = 1,
                    ),
                    overlayMyo("mini-1", 1, weight = 70.0),
                ),
            )
            val rec = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[
                SetSlotId.Overlay(OverlaySetInstanceId("mini-1")),
            ]
            assertNotNull(rec)
            assertEquals(70.0, rec!!.weight.amount, 0.01)
        }
    }

    // -------------------------------------------------------------------------
    // Drop prior assumptions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Drop set prior assumptions")
    inner class DropPriorAssumptions {

        @Test
        @DisplayName("unlogged parent with only baseline prescription uses rirGoal (not lastPerformed)")
        fun dropUsesParentBaselineRepsAndRirGoal() {
            // Parent is open with baseline 7@1; no straight live rec (engine may Hold/null).
            // Drop must not fall through to lastPerformed mini 3@0.
            val workout = rendered(
                listOf(
                    set(
                        "act", 0, SetType.MyoRep, true, 65.0,
                        log("act", 65.0, 11, 1),
                        repMin = 8, repMax = 12, rirGoal = 1,
                    ),
                    overlayMyo("m1", 1, logged = logOverlay("m1", 65.0, 3, 0)),
                    set(
                        "parent", 2, SetType.Standard, false, 65.0,
                        logged = null,
                        repMin = 6, repMax = 10, rirGoal = 1,
                        recommendedReps = 7,
                    ),
                    set("drop", 3, SetType.DropSet, false, 65.0, rirGoal = 1),
                ),
            )
            val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
            val drop = recs[SetSlotId.Template(LiftSetId("drop"))]
            assertNotNull(drop)
            // From parent prescription 65×7@1 (failure 8) → 52 lb drop, not from mini 3@0
            assertEquals(52.0, drop!!.weight.amount, 0.01)
            val fromMiniCrush = com.stratum.domain.workout.dropset.DropSetRepProjection.expectedReps(
                priorWeight = lb(65.0),
                priorReps = 3,
                priorRir = 0,
                dropWeight = lb(52.0),
                rirGoal = 1,
            )
            assertTrue(
                drop.reps > fromMiniCrush,
                "drop must not under-project from last mini; got ${drop.reps} vs mini-path $fromMiniCrush",
            )
        }

        @Test
        @DisplayName("drop rirGoal 1 yields fewer recommended reps than rirGoal 0 at same prior")
        fun dropRirGoalSubtractsFromCapacity() {
            val prior = log("s1", 100.0, 8, 1)
            val at0 = rendered(
                listOf(
                    set("s1", 0, SetType.Standard, true, 100.0, prior, repMin = 6, repMax = 10, rirGoal = 1),
                    set("d0", 1, SetType.DropSet, false, 100.0, rirGoal = 0),
                ),
            )
            val at1 = rendered(
                listOf(
                    set("s1", 0, SetType.Standard, true, 100.0, prior, repMin = 6, repMax = 10, rirGoal = 1),
                    set("d1", 1, SetType.DropSet, false, 100.0, rirGoal = 1),
                ),
            )
            val r0 = useCase(at0, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[SetSlotId.Template(LiftSetId("d0"))]
            val r1 = useCase(at1, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[SetSlotId.Template(LiftSetId("d1"))]
            assertNotNull(r0)
            assertNotNull(r1)
            assertEquals(r0!!.weight.amount, r1!!.weight.amount, 0.01)
            assertEquals(r0.reps - 1, r1.reps, "rirGoal should subtract one working rep from zero-rest capacity")
        }

        @Test
        @DisplayName("skip-ahead: unlogged drop before last performed gets no live rec")
        fun skipAheadUnloggedDropBeforeAnchorGetsNoRec() {
            // User logged the second standard first; open drop sits before last performed.
            val workout = rendered(
                listOf(
                    set("s1", 0, SetType.Standard, true, 100.0, log("s1", 100.0, 8, 1), repMin = 6, repMax = 10, rirGoal = 1),
                    set("d1", 1, SetType.DropSet, false, 100.0, logged = null, rirGoal = 0),
                    set("s2", 2, SetType.Standard, false, 100.0, log("s2", 100.0, 7, 1), repMin = 6, repMax = 10, rirGoal = 1),
                ),
            )
            val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
            assertNull(
                recs[SetSlotId.Template(LiftSetId("d1"))],
                "pre-anchor drop stays blank (post-anchor-only for drops)",
            )
        }

        @Test
        @DisplayName("three-drop unlogged chain: each step 80% of prior rec weight")
        fun tripleUnloggedDropChain() {
            val workout = rendered(
                listOf(
                    set("s1", 0, SetType.Standard, true, 100.0, log("s1", 100.0, 8, 1), repMin = 6, repMax = 10, rirGoal = 1),
                    set("d1", 1, SetType.DropSet, false, 100.0),
                    set("d2", 2, SetType.DropSet, false, 100.0),
                    set("d3", 3, SetType.DropSet, false, 100.0),
                ),
            )
            val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
            val d1 = recs[SetSlotId.Template(LiftSetId("d1"))]
            val d2 = recs[SetSlotId.Template(LiftSetId("d2"))]
            val d3 = recs[SetSlotId.Template(LiftSetId("d3"))]
            assertNotNull(d1)
            assertNotNull(d2)
            assertNotNull(d3)
            assertEquals(80.0, d1!!.weight.amount, 0.01)
            assertEquals(64.0, d2!!.weight.amount, 0.01)
            assertEquals(51.2, d3!!.weight.amount, 0.01)
            assertTrue(d1.reps >= d2.reps, "reps should not recover along unlogged chain")
            assertTrue(d2.reps >= d3.reps, "reps should not recover along unlogged chain")
        }
    }

    // -------------------------------------------------------------------------
    // Mixed post-intensifier
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Post-myo standard + drop mix")
    inner class PostMyoStandardAndDrop {

        @Test
        @DisplayName("after completed myo, open standard and following drop both receive recs")
        fun postMyoStandardAndDropBothRecommended() {
            val workout = rendered(
                listOf(
                    set(
                        "act", 0, SetType.MyoRep, true, 65.0,
                        log("act", 65.0, 11, 1),
                        repMin = 8, repMax = 12, rirGoal = 1,
                    ),
                    overlayMyo("m1", 1, logged = logOverlay("m1", 65.0, 5, 0)),
                    set("s1", 2, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                    set("d1", 3, SetType.DropSet, false, 65.0, rirGoal = 1),
                ),
            )
            val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
            val std = recs[SetSlotId.Template(LiftSetId("s1"))]
            val drop = recs[SetSlotId.Template(LiftSetId("d1"))]
            assertNotNull(std, "post-myo standard needs live rec")
            assertNotNull(drop, "drop after open standard needs live rec")
            assertTrue(std!!.weight.amount >= 60.0, "standard near activation load")
            assertTrue(
                drop!!.weight.amount < std.weight.amount,
                "drop lighter than its parent standard rec",
            )
            assertTrue(std.reps > 0 && drop.reps > 0)
        }

        @Test
        @DisplayName("drop after logged post-myo standard uses that standard, not last mini")
        fun dropAfterLoggedPostMyoStandardUsesStandard() {
            val workout = rendered(
                listOf(
                    set(
                        "act", 0, SetType.MyoRep, true, 100.0,
                        log("act", 100.0, 10, 1),
                        repMin = 8, repMax = 12, rirGoal = 1,
                    ),
                    overlayMyo("m1", 1, weight = 100.0, logged = logOverlay("m1", 100.0, 3, 0)),
                    set(
                        "s1", 2, SetType.Standard, false, 100.0,
                        log("s1", 100.0, 7, 1),
                        repMin = 6, repMax = 10, rirGoal = 1,
                    ),
                    set("d1", 3, SetType.DropSet, false, 100.0, rirGoal = 0),
                ),
            )
            val drop = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[
                SetSlotId.Template(LiftSetId("d1")),
            ]
            assertNotNull(drop)
            assertEquals(80.0, drop!!.weight.amount, 0.01, "20% off standard 100, not mini")
            // From 100×7@1 (fail 8) at 80: healthier than from mini 3@0
            val fromMini = com.stratum.domain.workout.dropset.DropSetRepProjection.expectedReps(
                priorWeight = lb(100.0),
                priorReps = 3,
                priorRir = 0,
                dropWeight = lb(80.0),
                rirGoal = 0,
            )
            assertTrue(drop.reps > fromMini, "must not re-anchor on crushing mini")
        }
    }

    // -------------------------------------------------------------------------
    // Post-anchor straight blanks
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Post-anchor straight-set policy")
    inner class PostAnchorPolicy {

        @Test
        @DisplayName("unlogged standard before last performed has no live rec")
        fun preAnchorStandardHasNoLiveRec() {
            val workout = rendered(
                listOf(
                    set("s1", 0, SetType.Standard, true, 100.0, log("s1", 100.0, 8, 1), repMin = 6, repMax = 10, rirGoal = 1),
                    set("s2", 1, SetType.Standard, false, 100.0, logged = null, repMin = 6, repMax = 10, rirGoal = 1),
                    set("s3", 2, SetType.Standard, false, 100.0, log("s3", 100.0, 6, 1), repMin = 6, repMax = 10, rirGoal = 1),
                    set("s4", 3, SetType.Standard, false, 100.0, logged = null, repMin = 6, repMax = 10, rirGoal = 1),
                ),
            )
            val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
            assertNull(recs[SetSlotId.Template(LiftSetId("s2"))], "skipped set stays blank")
            assertNotNull(recs[SetSlotId.Template(LiftSetId("s4"))], "post-anchor set still recommended")
        }
    }
}
