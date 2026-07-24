package com.stratum.domain.workout

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
import com.stratum.domain.workout.dropset.EstimateDropSetWeightUseCase
import com.stratum.domain.workout.fatigue.PopulationFatigueModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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
        val recs = useCase(workout, mapOf(liftId to catalogLift()))
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
        val recs = useCase(workout, mapOf(liftId to catalogLift()))
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
        val recs = useCase(workout, mapOf(liftId to catalogLift()))
        val second = recs[SetSlotId.Template(LiftSetId(d2))]
        assertNotNull(second)
        assertEquals(64.0, second!!.weight.amount, 0.01)
        // From 80×6 → 64: fresh ~12 × 0.5 → 6
        assertEquals(6, second.reps)
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
        val recs = useCase(workout, mapOf(liftId to catalogLift()))
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
        val mini = "mini"
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
        val recs = useCase(workout, mapOf(liftId to catalogLift()))
        val miniRec = recs[SetSlotId.Overlay(OverlaySetInstanceId(mini))]
        assertNotNull(miniRec)
        assertTrue(miniRec!!.weight.amount < 50.0, "mini should be lighter than crushing activation")
    }

    @Test
    @DisplayName("MYO-COPY: normal activation keeps same mini weight")
    fun myoMiniCopiesActivationWhenOnTrack() {
        val act = "act"
        val mini = "mini"
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
        val recs = useCase(workout, mapOf(liftId to catalogLift()))
        val miniRec = recs[SetSlotId.Overlay(OverlaySetInstanceId(mini))]
        assertNotNull(miniRec)
        assertEquals(40.0, miniRec!!.weight.amount, 0.01)
    }

    @Test
    @DisplayName("MYO-FATIGUE-1: first mini after 65×9 is well below 9 (short-rest default)")
    fun firstMiniAppliesAggressiveShortRestFatigue() {
        val act = "act"
        val mini = "mini"
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
        val recs = useCase(workout, mapOf(liftId to catalogLift()))
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
        val mini1 = "mini1"
        val mini2 = "mini2"
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
        val recs = useCase(workout, mapOf(liftId to catalogLift()))
        val miniRec = recs[SetSlotId.Overlay(OverlaySetInstanceId(mini2))]
        assertNotNull(miniRec)
        // From last mini 9, not activation 12: 9 − 3 = 6
        assertEquals(6, miniRec!!.reps)
        assertTrue(miniRec.reps < 9, "must not replay last mini's 9 with zero residual fatigue")
    }

    @Test
    @DisplayName("POST-MYO-STRAIGHT: standards after completed minis anchor on activation, not last mini")
    fun postMyoStraightAnchorsOnActivationNotLastMini() {
        // Screenshot regression: activation 65×11@1, minis 65×6@0 / 65×3@0, then standards.
        // Re-anchoring on the 3-rep mini understates e1RM and crushed load (~50). Must use
        // activation capacity and still count the two minis as fatigue steps.
        val act = "act"
        val mini1 = "mini1"
        val mini2 = "mini2"
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
        val recs = useCase(workout, mapOf(liftId to catalogLift()))
        val firstStd = recs[SetSlotId.Template(LiftSetId(s1))]
        val lastStd = recs[SetSlotId.Template(LiftSetId(s3))]
        assertNotNull(firstStd)
        assertNotNull(lastStd)
        // Shared straight weight must not collapse to the mini-anchored ~50 lb band.
        assertTrue(
            firstStd!!.weight.amount >= 60.0,
            "post-myo straight weight must track activation (~65), not last mini crush; got ${firstStd.weight.amount}",
        )
        assertEquals(firstStd.weight.amount, lastStd!!.weight.amount, 0.01, "shared straight weight")
        // First remaining standard is set-index 4 after activation (2 minis + itself).
        // Last mini alone (set-index 2 from 65×3) would project far fewer reps at the same load.
        assertTrue(firstStd.reps >= 5, "honest capacity at activation e1RM; got ${firstStd.reps}")
        assertTrue(
            firstStd.reps >= lastStd.reps,
            "reps should step down with set index: ${firstStd.reps} then ${lastStd.reps}",
        )
    }

    @Test
    @DisplayName("POST-MYO-STRAIGHT: mini-only anchor would reduce harder than activation anchor")
    fun postMyoActivationAnchorBeatsMiniOnlyBaseline() {
        val act = "act"
        val mini = "mini"
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
        val myoRec = useCase(withMyo, mapOf(liftId to catalogLift()))[SetSlotId.Template(LiftSetId(std))]
        val crushedRec = useCase(
            miniAsStandardOnly,
            mapOf(liftId to catalogLift()),
        )[SetSlotId.Template(LiftSetId(std))]
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
        val postDrop = useCase(workout, mapOf(liftId to catalogLift()))[SetSlotId.Template(LiftSetId(std))]
        val crushed = useCase(
            dropAsTopOnly,
            mapOf(liftId to catalogLift()),
        )[SetSlotId.Template(LiftSetId(std))]
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
