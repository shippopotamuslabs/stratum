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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.workout.dropset.EstimateDropSetWeightUseCase
import com.stratum.domain.workout.myorep.MyoRepKind
import com.stratum.domain.lift.residual
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.RecommendationEngine
import com.stratum.domain.workout.RenderedSet
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.RenderedWorkoutLift
import com.stratum.domain.workout.SetSlotId

@DisplayName("RecomputeRecommendationsUseCase: drop + myo intensifiers")
class RecomputeIntensifierRecommendationsTest {

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
        recommendedReps = repMin.coerceAtLeast(1),
        loggedValue = logged,
    )

    @Test
    @DisplayName(
        "DROP-COLD-OPEN: nothing logged — drop still 20% off prior snapshot weight " +
            "(not shared straight-set load)",
    )
    fun coldOpenDropUsesTwentyPercentOffPriorPrescription() {
        // Screenshot: all incomplete S 150×11 + D showing 150 — must be 120 before any log.
        val workout = rendered(
            listOf(
                set("s1", 0, SetType.Standard, true, 150.0, repMin = 8, repMax = 12, rirGoal = 1)
                    .copy(recommendedReps = 11),
                set("s2", 1, SetType.Standard, false, 150.0, repMin = 8, repMax = 12, rirGoal = 1)
                    .copy(recommendedReps = 11),
                set("d1", 2, SetType.DropSet, false, 150.0, rirGoal = 0)
                    .copy(recommendedReps = 11),
                set("s3", 3, SetType.Standard, false, 150.0, repMin = 6, repMax = 10, rirGoal = 2)
                    .copy(recommendedReps = 8),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val drop = recs[SetSlotId.Template(LiftSetId("d1"))]
        assertNotNull(drop, "cold open must still prescribe drops")
        assertEquals(120.0, drop!!.weight.amount, 0.01, "20% off prior 150")
        assertTrue(drop.reps > 0)
        // First straight stays on snapshot (no live row); later straights get cold-open fatigue reps.
        assertNull(recs[SetSlotId.Template(LiftSetId("s1"))])
        val s2 = recs[SetSlotId.Template(LiftSetId("s2"))]
        assertNotNull(s2, "cold open projects fatigue reps for set 2+")
        assertEquals(150.0, s2!!.weight.amount, 0.01, "cold-open weight stays snapshot")
        assertTrue(s2.reps > 0)
    }

    @Test
    @DisplayName("DROP-W20: drop rec is 80% of performed prior")
    fun dropTwentyPercentFromPerformed() {
        val priorId = "s1"
        val dropId = "s2"
        val logged = LoggedSetValue(
            slotId = SetSlotId.Template(LiftSetId(priorId)),
            weight = lb(100.0),
            reps = 8,
            rir = 1,
        )
        val workout = rendered(
            listOf(
                set(priorId, 0, SetType.Standard, true, 100.0, logged, repMin = 6, repMax = 10, rirGoal = 1),
                set(dropId, 1, SetType.DropSet, false, 100.0),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val drop = recs[SetSlotId.Template(LiftSetId(dropId))]
        assertNotNull(drop)
        assertEquals(80.0, drop!!.weight.amount, 0.01)
        assertTrue(drop.reps >= 1)
    }

    @Test
    @DisplayName("DROP-ZERO-REST: 100×8@1RIR → 80 lb uses zero-rest capacity, not near-fresh e1RM")
    fun dropRepsApplyZeroRestResidual() {
        val priorId = "s1"
        val dropId = "s2"
        val workout = rendered(
            listOf(
                set(
                    priorId, 0, SetType.Standard, true, 100.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(priorId)), lb(100.0), 8, 1),
                    repMin = 6, repMax = 10, rirGoal = 1,
                ),
                set(dropId, 1, SetType.DropSet, false, 100.0),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val drop = recs[SetSlotId.Template(LiftSetId(dropId))]
        assertNotNull(drop)
        assertEquals(80.0, drop!!.weight.amount, 0.01)
        // Fresh e1RM at 80 after 100×9-fail is ~15; half residual → ~8. Must not be 11–15.
        assertEquals(8, drop.reps)
        assertTrue(drop.reps < 11, "zero-rest residual must beat mild set-index bump (~11)")
    }

    @Test
    @DisplayName("DROP-CHAIN: second drop uses first drop performed, not original standard")
    fun dropChainUsesImmediatePrior() {
        val s1 = "s1"
        val d1 = "d1"
        val d2 = "d2"
        val workout = rendered(
            listOf(
                set(
                    s1, 0, SetType.Standard, true, 100.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(s1)), lb(100.0), 8, 1),
                    repMin = 6, repMax = 10, rirGoal = 1,
                ),
                set(
                    d1, 1, SetType.DropSet, false, 80.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(d1)), lb(80.0), 6, 0),
                ),
                set(d2, 2, SetType.DropSet, false, 80.0),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val second = recs[SetSlotId.Template(LiftSetId(d2))]
        assertNotNull(second)
        assertEquals(64.0, second!!.weight.amount, 0.01)
        // From 80×6 → 64: fresh ~12 × 0.5 → 6
        assertEquals(6, second.reps)
    }

    @Test
    @DisplayName(
        "DROP-PRIOR-AS-PRESCRIBED: drop reps stable when parent is logged exactly as " +
            "the open-row prescription (must use parent rirGoal, not RIR 0)",
    )
    fun dropRepsUnchangedWhenPriorLoggedAsPrescribed() {
        // Screenshot: myo cluster done → open standard 65×7@1 + drop 50×7; user checks
        // the standard exactly as shown. Drop reps must not jump (was 7 → 8 when RIR was
        // hardcoded 0 on the unlogged-parent path while live rec was 7 at rirGoal 1).
        val act = "act"
        val mini = "mini-act-1"
        val parentId = "s1"
        val dropId = "d1"
        val openParent = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(65.0), 11, 1),
                    repMin = 8, repMax = 12, rirGoal = 1,
                ),
                overlayMyo(
                    mini, 1,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini)),
                        lb(65.0),
                        5,
                        0,
                    ),
                ),
                set(
                    parentId, 2, SetType.Standard, false, 65.0,
                    logged = null,
                    repMin = 6, repMax = 10, rirGoal = 1,
                ),
                set(dropId, 3, SetType.DropSet, false, 65.0, rirGoal = 1),
            ),
        )
        val before = useCase(openParent, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val parentRec = before[SetSlotId.Template(LiftSetId(parentId))]
        assertNotNull(parentRec, "parent needs a live rec to act as the open prescription")
        val dropBefore = before[SetSlotId.Template(LiftSetId(dropId))]
        assertNotNull(dropBefore)

        val loggedAsPrescribed = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(65.0), 11, 1),
                    repMin = 8, repMax = 12, rirGoal = 1,
                ),
                overlayMyo(
                    mini, 1,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini)),
                        lb(65.0),
                        5,
                        0,
                    ),
                ),
                set(
                    parentId, 2, SetType.Standard, false, 65.0,
                    LoggedSetValue(
                        SetSlotId.Template(LiftSetId(parentId)),
                        parentRec!!.weight,
                        parentRec.reps,
                        rir = 1,
                    ),
                    repMin = 6, repMax = 10, rirGoal = 1,
                ),
                set(dropId, 3, SetType.DropSet, false, 65.0, rirGoal = 1),
            ),
        )
        val dropAfter = useCase(loggedAsPrescribed, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[
            SetSlotId.Template(LiftSetId(dropId)),
        ]
        assertNotNull(dropAfter)
        assertEquals(
            dropBefore!!.weight.amount,
            dropAfter!!.weight.amount,
            0.01,
            "drop weight should match when parent logged as prescribed",
        )
        assertEquals(
            dropBefore.reps,
            dropAfter.reps,
            "drop reps must not jump when parent is logged exactly as the open prescription " +
                "(before=${dropBefore.reps}, after=${dropAfter.reps}, parentRec=${parentRec.reps})",
        )
    }

    @Test
    @DisplayName(
        "DROP-CHAIN-PRIOR-AS-PRESCRIBED: second drop stable when first drop is logged " +
            "exactly as open rec at its rirGoal (0) — not a higher logged RIR",
    )
    fun dropChainSecondStableWhenFirstLoggedAsPrescribedAtRirGoal() {
        // Screenshot: S×3 open → D 15×11 / D 10×11; logging first drop 15×11@0 keeps second
        // at 10×11. Logging 15×11@1 (stale badge) was jumping second to 10×12.
        val s1 = "s1"
        val s2 = "s2"
        val s3 = "s3"
        val d1 = "d1"
        val d2 = "d2"
        val open = rendered(
            listOf(
                set(s1, 0, SetType.Standard, true, 20.0, repMin = 10, repMax = 18, rirGoal = 1),
                set(s2, 1, SetType.Standard, false, 20.0, repMin = 10, repMax = 18, rirGoal = 1),
                set(s3, 2, SetType.Standard, false, 20.0, repMin = 10, repMax = 18, rirGoal = 1),
                set(d1, 3, SetType.DropSet, false, 20.0, rirGoal = 0),
                set(d2, 4, SetType.DropSet, false, 20.0, rirGoal = 0),
            ),
        )
        val before = useCase(open, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val firstBefore = before[SetSlotId.Template(LiftSetId(d1))]
        val secondBefore = before[SetSlotId.Template(LiftSetId(d2))]
        assertNotNull(firstBefore)
        assertNotNull(secondBefore)

        val loggedAtGoal = rendered(
            listOf(
                set(s1, 0, SetType.Standard, true, 20.0, repMin = 10, repMax = 18, rirGoal = 1),
                set(s2, 1, SetType.Standard, false, 20.0, repMin = 10, repMax = 18, rirGoal = 1),
                set(s3, 2, SetType.Standard, false, 20.0, repMin = 10, repMax = 18, rirGoal = 1),
                set(
                    d1, 3, SetType.DropSet, false, 20.0,
                    LoggedSetValue(
                        SetSlotId.Template(LiftSetId(d1)),
                        firstBefore!!.weight,
                        firstBefore.reps,
                        rir = 0,
                    ),
                    rirGoal = 0,
                ),
                set(d2, 4, SetType.DropSet, false, 20.0, rirGoal = 0),
            ),
        )
        val secondAfter = useCase(loggedAtGoal, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[
            SetSlotId.Template(LiftSetId(d2)),
        ]
        assertNotNull(secondAfter)
        assertEquals(
            secondBefore!!.weight.amount,
            secondAfter!!.weight.amount,
            0.01,
        )
        assertEquals(
            secondBefore.reps,
            secondAfter.reps,
            "second drop must not jump when first is logged as open rec @ rirGoal " +
                "(before=${secondBefore.reps}, after=${secondAfter.reps}, " +
                "firstRec=${firstBefore.reps})",
        )

        // Contrast: logging the same weight/reps with a higher RIR *should* raise capacity.
        val loggedWithHigherRir = rendered(
            listOf(
                set(s1, 0, SetType.Standard, true, 20.0, repMin = 10, repMax = 18, rirGoal = 1),
                set(s2, 1, SetType.Standard, false, 20.0, repMin = 10, repMax = 18, rirGoal = 1),
                set(s3, 2, SetType.Standard, false, 20.0, repMin = 10, repMax = 18, rirGoal = 1),
                set(
                    d1, 3, SetType.DropSet, false, 20.0,
                    LoggedSetValue(
                        SetSlotId.Template(LiftSetId(d1)),
                        firstBefore.weight,
                        firstBefore.reps,
                        rir = 1,
                    ),
                    rirGoal = 0,
                ),
                set(d2, 4, SetType.DropSet, false, 20.0, rirGoal = 0),
            ),
        )
        val secondHigherRir = useCase(loggedWithHigherRir, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[
            SetSlotId.Template(LiftSetId(d2)),
        ]
        assertNotNull(secondHigherRir)
        assertTrue(
            secondHigherRir!!.reps >= secondAfter.reps,
            "logging prior drop with higher RIR may raise next-drop capacity " +
                "(atGoal=${secondAfter.reps}, higherRir=${secondHigherRir.reps})",
        )
    }

    @Test
    @DisplayName("DROP-CHAIN-UNLOGGED: second planned drop re-anchors on first drop rec")
    fun unloggedDropChainReanchorsOnPriorRec() {
        val s1 = "s1"
        val d1 = "d1"
        val d2 = "d2"
        val workout = rendered(
            listOf(
                set(
                    s1, 0, SetType.Standard, true, 100.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(s1)), lb(100.0), 8, 1),
                    repMin = 6, repMax = 10, rirGoal = 1,
                ),
                set(d1, 1, SetType.DropSet, false, 100.0),
                set(d2, 2, SetType.DropSet, false, 100.0),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val first = recs[SetSlotId.Template(LiftSetId(d1))]
        val second = recs[SetSlotId.Template(LiftSetId(d2))]
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(80.0, first!!.weight.amount, 0.01)
        assertEquals(64.0, second!!.weight.amount, 0.01)
        // Second must not reuse parent 100×9 capacity at 64 (would be ~10–11 even with 0.5×).
        assertTrue(
            second.reps <= first.reps,
            "chained zero-rest drop should not recover reps vs prior drop rec " +
                "(first=${first.reps}, second=${second.reps})",
        )
    }

    @Test
    @DisplayName("MYO-HEAVY: activation way under floor lowers mini weight")
    fun myoMiniReducesWhenActivationTooHeavy() {
        val act = "act"
        val mini = "mini-act-1"
        val workout = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 50.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(50.0), 4, 0),
                    repMin = 10, repMax = 15, rirGoal = 1,
                ),
                RenderedSet(
                    slotId = SetSlotId.Overlay(OverlaySetInstanceId(mini)),
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 1,
                    setType = SetType.MyoRep,
                    isTopSet = false,
                    repMin = 0,
                    repMax = 0,
                    rirGoal = 0,
                    restSeconds = 15,
                    recommendedWeight = lb(50.0),
                    recommendedReps = 5,
                    loggedValue = null,
                ),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val miniRec = recs[SetSlotId.Overlay(OverlaySetInstanceId(mini))]
        assertNotNull(miniRec)
        assertTrue(miniRec!!.weight.amount < 50.0, "mini should be lighter than crushing activation")
    }

    @Test
    @DisplayName("MYO-COPY: normal activation keeps same mini weight")
    fun myoMiniCopiesActivationWhenOnTrack() {
        val act = "act"
        val mini = "mini-act-1"
        val workout = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 40.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(40.0), 12, 1),
                    repMin = 10, repMax = 15, rirGoal = 1,
                ),
                RenderedSet(
                    slotId = SetSlotId.Overlay(OverlaySetInstanceId(mini)),
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 1,
                    setType = SetType.MyoRep,
                    isTopSet = false,
                    repMin = 0,
                    repMax = 0,
                    rirGoal = 0,
                    restSeconds = 15,
                    recommendedWeight = lb(40.0),
                    recommendedReps = 5,
                    loggedValue = null,
                ),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val miniRec = recs[SetSlotId.Overlay(OverlaySetInstanceId(mini))]
        assertNotNull(miniRec)
        assertEquals(40.0, miniRec!!.weight.amount, 0.01)
    }

    @Test
    @DisplayName("MATCH-MINI: weight seeds from activation even when backoff weight differs; no too-heavy reduce")
    fun matchMiniWeightFromActivationNotBackoff() {
        val act = "act"
        val bo = "bo1"
        val mini = "mini-bo1-1-aaaaaaaa"
        val actId = SetSlotId.Template(LiftSetId(act))
        val boId = SetSlotId.Template(LiftSetId(bo))
        val miniId = SetSlotId.Overlay(OverlaySetInstanceId(mini))
        val workout = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 100.0,
                    LoggedSetValue(actId, lb(100.0), 12, 1),
                    repMin = 10, repMax = 15, rirGoal = 1,
                ).copy(myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepMatching),
                set(
                    bo, 1, SetType.MyoRep, false, 90.0,
                    LoggedSetValue(boId, lb(90.0), 6, 1),
                    repMin = 0, repMax = 0, rirGoal = 1,
                ).copy(myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepMatching),
                RenderedSet(
                    slotId = miniId,
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 2,
                    setType = SetType.MyoRep,
                    isTopSet = false,
                    repMin = 0,
                    repMax = 0,
                    rirGoal = 0,
                    restSeconds = 15,
                    recommendedWeight = lb(90.0),
                    recommendedReps = 0,
                    loggedValue = null,
                    myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepMatching,
                ),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val miniRec = recs[miniId]
        assertNotNull(miniRec)
        assertEquals(
            100.0,
            miniRec!!.weight.amount,
            0.01,
            "match mini weight must come from activation load, not backoff",
        )
        // Activation under floor would reduce for floor myo; match must not.
        assertTrue(miniRec.weight.amount >= 100.0 - 0.01)
    }

    @Test
    @DisplayName("MYO-FATIGUE-1: first mini after 65×9 is well below 9 (short-rest default)")
    fun firstMiniAppliesAggressiveShortRestFatigue() {
        val act = "act"
        val mini = "mini-act-1"
        val workout = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(65.0), 9, 0),
                    repMin = 8, repMax = 12, rirGoal = 0,
                ),
                RenderedSet(
                    slotId = SetSlotId.Overlay(OverlaySetInstanceId(mini)),
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 1,
                    setType = SetType.MyoRep,
                    isTopSet = false,
                    repMin = 0,
                    repMax = 0,
                    rirGoal = 0,
                    restSeconds = 15,
                    recommendedWeight = lb(65.0),
                    recommendedReps = 0,
                    loggedValue = null,
                ),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val miniRec = recs[SetSlotId.Overlay(OverlaySetInstanceId(mini))]
        assertNotNull(miniRec)
        assertEquals(65.0, miniRec!!.weight.amount, 0.01)
        assertEquals(4, miniRec.reps, "9 × 0.45 short-rest first-mini default")
        assertTrue(miniRec.reps < 9)
    }

    @Test
    @DisplayName("MYO-FATIGUE-2: second mini re-anchors on last mini, not activation")
    fun subsequentMiniDropsFromLastMiniNotActivation() {
        val act = "act"
        val mini1 = "mini-act-1"
        val mini2 = "mini-act-2"
        val workout = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(65.0), 12, 0),
                    repMin = 8, repMax = 12, rirGoal = 0,
                ),
                RenderedSet(
                    slotId = SetSlotId.Overlay(OverlaySetInstanceId(mini1)),
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 1,
                    setType = SetType.MyoRep,
                    isTopSet = false,
                    repMin = 0,
                    repMax = 0,
                    rirGoal = 0,
                    restSeconds = 15,
                    recommendedWeight = lb(65.0),
                    recommendedReps = 5,
                    loggedValue = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini1)),
                        lb(65.0),
                        9,
                        0,
                    ),
                ),
                RenderedSet(
                    slotId = SetSlotId.Overlay(OverlaySetInstanceId(mini2)),
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 2,
                    setType = SetType.MyoRep,
                    isTopSet = false,
                    repMin = 0,
                    repMax = 0,
                    rirGoal = 0,
                    restSeconds = 15,
                    recommendedWeight = lb(65.0),
                    recommendedReps = 0,
                    loggedValue = null,
                ),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val miniRec = recs[SetSlotId.Overlay(OverlaySetInstanceId(mini2))]
        assertNotNull(miniRec)
        // From last mini 9, not activation 12: 9 − 3 = 6
        assertEquals(6, miniRec!!.reps)
        assertTrue(miniRec.reps < 9, "must not replay last mini's 9 with zero residual fatigue")
    }

    @Test
    @DisplayName(
        "MYO-MULTI-CLUSTER-OOO: open mini under earlier activation still gets recs " +
            "after a later cluster is fully logged",
    )
    fun earlierClusterOpenMiniGetsRecsWhenLaterClusterDone() {
        // Screenshot regression: user finishes 2nd myo cluster first, then logs 1st activation.
        // Global last-performed is the last mini of cluster 2; the open mini under cluster 1
        // sits before that anchor and must still receive weight + short-rest reps from act1.
        val act1 = "act1"
        val mini1 = "mini-act1-1"
        val act2 = "act2"
        val mini2a = "mini-act2-1"
        val mini2b = "mini-act2-2"
        val workout = rendered(
            listOf(
                set(
                    act1, 0, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act1)), lb(65.0), 11, 1),
                    repMin = 8, repMax = 12, rirGoal = 1,
                ),
                overlayMyo(mini1, 1, weight = 65.0, logged = null),
                set(
                    act2, 2, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act2)), lb(65.0), 11, 1),
                    repMin = 8, repMax = 12, rirGoal = 1,
                ),
                overlayMyo(
                    mini2a, 3,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini2a)),
                        lb(65.0),
                        8,
                        0,
                    ),
                ),
                overlayMyo(
                    mini2b, 4,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini2b)),
                        lb(65.0),
                        5,
                        0,
                    ),
                ),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val openMini = recs[SetSlotId.Overlay(OverlaySetInstanceId(mini1))]
        assertNotNull(openMini, "open mini under earlier activation must get a live rec")
        assertEquals(65.0, openMini!!.weight.amount, 0.01, "copies activation load")
        // First mini from 65×11@1 → failure capacity 12 × 0.45 ≈ 5
        assertEquals(5, openMini.reps, "short-rest first-mini projection from act1, not blank")
        assertTrue(openMini.reps > 0)
    }

    @Test
    @DisplayName(
        "POST-MYO-STRAIGHT-FLOOR: long mini chain must drop shared weight rather than " +
            "sub-floor reps on following standards",
    )
    fun postMyoStraightDropsWeightInsteadOfSubFloorReps() {
        // Screenshot: act 65×11@1 + three minis (6/6/3) → standards showed 65×5/4/2.
        // Last-set floor (5) must cut load; never prescribe 4 or 2 at 65.
        val act = "act"
        val mini1 = "mini-act-1"
        val mini2 = "mini-act-2"
        val mini3 = "mini-act-3"
        val s1 = "s1"
        val s2 = "s2"
        val s3 = "s3"
        val workout = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(65.0), 11, 1),
                    repMin = 8, repMax = 12, rirGoal = 1,
                ),
                overlayMyo(
                    mini1, 1,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini1)),
                        lb(65.0),
                        6,
                        0,
                    ),
                ),
                overlayMyo(
                    mini2, 2,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini2)),
                        lb(65.0),
                        6,
                        0,
                    ),
                ),
                overlayMyo(
                    mini3, 3,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini3)),
                        lb(65.0),
                        3,
                        0,
                    ),
                ),
                set(s1, 4, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                set(s2, 5, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                set(s3, 6, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val first = recs[SetSlotId.Template(LiftSetId(s1))]
        val mid = recs[SetSlotId.Template(LiftSetId(s2))]
        val last = recs[SetSlotId.Template(LiftSetId(s3))]
        assertNotNull(first)
        assertNotNull(mid)
        assertNotNull(last)
        assertTrue(
            first!!.weight.amount < 65.0,
            "shared weight must drop when last-set capacity at 65 is sub-floor; got ${first.weight.amount}",
        )
        assertEquals(first.weight.amount, last!!.weight.amount, 0.01, "shared straight weight")
        assertTrue(first.reps >= BACKOFF_REP_FLOOR, "got ${first.reps}")
        assertTrue(mid!!.reps >= BACKOFF_REP_FLOOR, "got ${mid.reps}")
        assertTrue(last.reps >= BACKOFF_REP_FLOOR, "got ${last.reps}")
        assertTrue(first.reps >= last.reps, "set-index fatigue: ${first.reps} then ${last.reps}")
    }

    @Test
    @DisplayName(
        "POST-MYO-STRAIGHT-OPEN-MINI: after activation log, standards re-project while mini " +
            "is still open (not only after mini is completed)",
    )
    fun postMyoStraightUpdatesWhileMiniStillOpen() {
        // Screenshot: act 65×11@1 → mini spawns 65×5@0; standards still showed snapshot
        // 11/9/6 until mini was logged, then jumped to 8/7/5. Must live-project with the
        // open mini occupying a set-index step as soon as the activation is logged.
        val act = "act"
        val mini = "mini-act-1"
        val s1 = "s1"
        val s2 = "s2"
        val s3 = "s3"
        val withOpenMini = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(65.0), 11, 1),
                    repMin = 8, repMax = 12, rirGoal = 1,
                ),
                overlayMyo(mini, 1, weight = 65.0, logged = null),
                set(s1, 2, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                set(s2, 3, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                set(s3, 4, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
            ),
        )
        val openRecs = useCase(withOpenMini, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val openS1 = openRecs[SetSlotId.Template(LiftSetId(s1))]
        val openS2 = openRecs[SetSlotId.Template(LiftSetId(s2))]
        val openS3 = openRecs[SetSlotId.Template(LiftSetId(s3))]
        assertNotNull(openS1, "standards must get live recs while mini is still open")
        assertNotNull(openS2)
        assertNotNull(openS3)

        // Control: no mini slot — first standard is only one step after activation.
        val noMini = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(65.0), 11, 1),
                    repMin = 8, repMax = 12, rirGoal = 1,
                ),
                set(s1, 1, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                set(s2, 2, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                set(s3, 3, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
            ),
        )
        val noMiniS1 = useCase(noMini, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[
            SetSlotId.Template(LiftSetId(s1)),
        ]
        // Without a mini, engine-null may leave snapshot (no live rec). If both live,
        // open-mini first standard is further set-index → fewer or equal reps.
        if (noMiniS1 != null) {
            assertTrue(
                openS1!!.reps <= noMiniS1.reps,
                "open mini must count as fatigue step for following standards " +
                    "(openMini=${openS1.reps}, noMini=${noMiniS1.reps})",
            )
        }

        // Logging mini exactly as its open rec must not jump standards further.
        val miniRec = openRecs[SetSlotId.Overlay(OverlaySetInstanceId(mini))]
        assertNotNull(miniRec)
        val miniLogged = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(65.0), 11, 1),
                    repMin = 8, repMax = 12, rirGoal = 1,
                ),
                overlayMyo(
                    mini, 1, weight = 65.0,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini)),
                        miniRec!!.weight,
                        miniRec.reps,
                        rir = 0,
                    ),
                ),
                set(s1, 2, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                set(s2, 3, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                set(s3, 4, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
            ),
        )
        val afterMini = useCase(miniLogged, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val afterS1 = afterMini[SetSlotId.Template(LiftSetId(s1))]
        assertNotNull(afterS1)
        assertEquals(
            openS1!!.reps,
            afterS1!!.reps,
            "logging mini as prescribed must not change first-standard reps " +
                "(open=${openS1.reps}, afterMini=${afterS1.reps})",
        )
        assertEquals(openS2!!.reps, afterMini[SetSlotId.Template(LiftSetId(s2))]!!.reps)
        assertEquals(openS3!!.reps, afterMini[SetSlotId.Template(LiftSetId(s3))]!!.reps)
    }

    @Test
    @DisplayName("POST-MYO-STRAIGHT: standards after completed minis anchor on activation, not last mini")
    fun postMyoStraightAnchorsOnActivationNotLastMini() {
        // Screenshot regression: activation 65×11@1, minis 65×6@0 / 65×3@0, then standards.
        // Re-anchoring on the 3-rep mini understates e1RM and crushed load (~50). Must use
        // activation capacity and still count the two minis as fatigue steps.
        val act = "act"
        val mini1 = "mini-act-1"
        val mini2 = "mini-act-2"
        val s1 = "s1"
        val s2 = "s2"
        val s3 = "s3"
        val workout = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 65.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(65.0), 11, 1),
                    repMin = 8, repMax = 12, rirGoal = 1,
                ),
                overlayMyo(
                    mini1, 1,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini1)),
                        lb(65.0),
                        6,
                        0,
                    ),
                ),
                overlayMyo(
                    mini2, 2,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini2)),
                        lb(65.0),
                        3,
                        0,
                    ),
                ),
                set(s1, 3, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                set(s2, 4, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
                set(s3, 5, SetType.Standard, false, 65.0, repMin = 6, repMax = 10, rirGoal = 1),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val firstStd = recs[SetSlotId.Template(LiftSetId(s1))]
        val lastStd = recs[SetSlotId.Template(LiftSetId(s3))]
        assertNotNull(firstStd)
        assertNotNull(lastStd)
        // Shared load is activation-anchored then last-set floor reduced — may sit below 65
        // but must not collapse to a last-mini e1RM crush (~50).
        assertTrue(
            firstStd!!.weight.amount >= 55.0,
            "post-myo straight weight must stay near activation/floor path; got ${firstStd.weight.amount}",
        )
        assertEquals(firstStd.weight.amount, lastStd!!.weight.amount, 0.01, "shared straight weight")
        // First remaining standard is set-index 4 after activation (2 minis + itself).
        // All standards clear BACKOFF_REP_FLOOR after shared reduce.
        assertTrue(firstStd.reps >= BACKOFF_REP_FLOOR, "honest capacity; got ${firstStd.reps}")
        assertTrue(lastStd.reps >= BACKOFF_REP_FLOOR, "last set must clear floor; got ${lastStd.reps}")
        assertTrue(
            firstStd.reps >= lastStd.reps,
            "reps should step down with set index: ${firstStd.reps} then ${lastStd.reps}",
        )
    }

    @Test
    @DisplayName("POST-MYO-STRAIGHT: mini-only anchor would reduce harder than activation anchor")
    fun postMyoActivationAnchorBeatsMiniOnlyBaseline() {
        val act = "act"
        val mini = "mini-act-1"
        val std = "std"
        val withMyo = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 100.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(act)), lb(100.0), 10, 1),
                    repMin = 8, repMax = 12, rirGoal = 1,
                ),
                overlayMyo(
                    mini, 1,
                    weight = 100.0,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini)),
                        lb(100.0),
                        3,
                        0,
                    ),
                ),
                set(std, 2, SetType.Standard, false, 100.0, repMin = 6, repMax = 10, rirGoal = 1),
            ),
        )
        // Same last log as if it were a lone standard — no activation capacity to fall back on.
        val miniAsStandardOnly = rendered(
            listOf(
                set(
                    mini, 0, SetType.Standard, true, 100.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(mini)), lb(100.0), 3, 0),
                    repMin = 6, repMax = 10, rirGoal = 1,
                ),
                set(std, 1, SetType.Standard, false, 100.0, repMin = 6, repMax = 10, rirGoal = 1),
            ),
        )
        val myoRec = useCase(withMyo, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[SetSlotId.Template(LiftSetId(std))]
        val crushedRec = useCase(
            miniAsStandardOnly,
            mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[SetSlotId.Template(LiftSetId(std))]
        assertNotNull(myoRec)
        assertNotNull(crushedRec)
        assertTrue(
            myoRec!!.weight.amount > crushedRec!!.weight.amount,
            "activation anchor (${myoRec.weight.amount}) must beat mini-only reduce (${crushedRec.weight.amount})",
        )
    }

    @Test
    @DisplayName("POST-DROP-STRAIGHT: standards after completed drops anchor on parent, not last drop")
    fun postDropStraightAnchorsOnParentNotLastDrop() {
        val parent = "parent"
        val d1 = "d1"
        val d2 = "d2"
        val std = "std"
        val workout = rendered(
            listOf(
                set(
                    parent, 0, SetType.Standard, true, 100.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(parent)), lb(100.0), 8, 1),
                    repMin = 6, repMax = 10, rirGoal = 1,
                ),
                set(
                    d1, 1, SetType.DropSet, false, 80.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(d1)), lb(80.0), 6, 0),
                ),
                set(
                    d2, 2, SetType.DropSet, false, 64.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(d2)), lb(64.0), 4, 0),
                ),
                set(std, 3, SetType.Standard, false, 100.0, repMin = 6, repMax = 10, rirGoal = 1),
            ),
        )
        // Control: only the weak last drop as a standard top set.
        val dropAsTopOnly = rendered(
            listOf(
                set(
                    d2, 0, SetType.Standard, true, 64.0,
                    LoggedSetValue(SetSlotId.Template(LiftSetId(d2)), lb(64.0), 4, 0),
                    repMin = 6, repMax = 10, rirGoal = 1,
                ),
                set(std, 1, SetType.Standard, false, 100.0, repMin = 6, repMax = 10, rirGoal = 1),
            ),
        )
        val postDrop = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[SetSlotId.Template(LiftSetId(std))]
        val crushed = useCase(
            dropAsTopOnly,
            mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())[SetSlotId.Template(LiftSetId(std))]
        assertNotNull(postDrop)
        assertNotNull(crushed)
        assertTrue(
            postDrop!!.weight.amount > crushed!!.weight.amount,
            "parent-standard anchor (${postDrop.weight.amount}) must beat last-drop reduce (${crushed.weight.amount})",
        )
        assertTrue(
            postDrop.weight.amount >= 85.0,
            "post-drop straight should stay near parent capacity, got ${postDrop.weight.amount}",
        )
    }

    @Test
    @DisplayName(
        "MATCH-CLUSTER-NEXT: first working set after a completed match mini cluster " +
            "projects from the activation (top set), not the final mini (STR-232)",
    )
    fun matchClusterNextSetAnchorsOnActivationNotMini() {
        // Sean's live scenario (Machine Crunch, rep-match): activation 140x15@2,
        // backoff 140x10@0, its mini 140x5@0 (cluster total 15 = parity, cluster
        // complete). The next backoff showed 135x5 — the gradient ran from the
        // mini/backoff chain as if the 5-rep mini were the capacity anchor. It must
        // project from the activation 140x15@2 with normal fatigue: the backoff and
        // its completed cluster count as one normal set step, minis never count.
        val act = "act"
        val bo1 = "bo1"
        val mini = "mini-bo1-1-aaaaaaaa"
        val bo2 = "bo2"
        val actId = SetSlotId.Template(LiftSetId(act))
        val bo1Id = SetSlotId.Template(LiftSetId(bo1))
        val bo2Id = SetSlotId.Template(LiftSetId(bo2))
        val workout = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 140.0,
                    LoggedSetValue(actId, lb(140.0), 15, 2),
                    repMin = 10, repMax = 15, rirGoal = 2,
                ).copy(myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepMatching),
                set(
                    bo1, 1, SetType.MyoRep, false, 140.0,
                    LoggedSetValue(bo1Id, lb(140.0), 10, 0),
                    repMin = 0, repMax = 0, rirGoal = 1,
                ).copy(myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepMatching),
                overlayMyo(
                    mini, 2, weight = 140.0,
                    logged = LoggedSetValue(
                        SetSlotId.Overlay(OverlaySetInstanceId(mini)),
                        lb(140.0),
                        5,
                        0,
                    ),
                ).copy(myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepMatching),
                set(
                    bo2, 3, SetType.MyoRep, false, 140.0,
                    repMin = 0, repMax = 0, rirGoal = 2,
                ).copy(myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepMatching),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val next = recs[bo2Id]
        assertNotNull(next)
        assertTrue(
            next!!.weight.amount == 140.0 && next.reps == 11,
            "next backoff must project from activation 140x15@2 with normal fatigue " +
                "(expected 140x11), got ${next.weight.amount}x${next.reps}",
        )
    }

    @Test
    @DisplayName(
        "MATCH-NO-CLUSTER: match backoff after a clean backoff (no minis) still " +
            "anchors on that backoff — behavior unchanged (STR-232)",
    )
    fun matchBackoffWithoutClusterAnchorsOnBackoff() {
        val act = "act"
        val bo1 = "bo1"
        val bo2 = "bo2"
        val actId = SetSlotId.Template(LiftSetId(act))
        val bo1Id = SetSlotId.Template(LiftSetId(bo1))
        val bo2Id = SetSlotId.Template(LiftSetId(bo2))
        val workout = rendered(
            listOf(
                set(
                    act, 0, SetType.MyoRep, true, 140.0,
                    LoggedSetValue(actId, lb(140.0), 15, 2),
                    repMin = 10, repMax = 15, rirGoal = 2,
                ).copy(myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepMatching),
                set(
                    bo1, 1, SetType.MyoRep, false, 140.0,
                    LoggedSetValue(bo1Id, lb(140.0), 10, 0),
                    repMin = 0, repMax = 0, rirGoal = 1,
                ).copy(myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepMatching),
                set(
                    bo2, 2, SetType.MyoRep, false, 140.0,
                    repMin = 0, repMax = 0, rirGoal = 2,
                ).copy(myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepMatching),
            ),
        )
        val recs = useCase(workout, mapOf(liftId to catalogLift()), priorRecommendations = emptyMap())
        val next = recs[bo2Id]
        assertNotNull(next)
        assertTrue(
            next!!.weight.amount == 140.0 && next.reps == 6,
            "clean backoff anchor (140x10@0, one normal step) must stay 140x6, " +
                "got ${next.weight.amount}x${next.reps}",
        )
    }

    private fun overlayMyo(
        id: String,
        position: Int,
        weight: Double = 65.0,
        logged: LoggedSetValue? = null,
    ) = RenderedSet(
        slotId = SetSlotId.Overlay(OverlaySetInstanceId(id)),
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
        recommendedReps = 5,
        loggedValue = logged,
    )
}
