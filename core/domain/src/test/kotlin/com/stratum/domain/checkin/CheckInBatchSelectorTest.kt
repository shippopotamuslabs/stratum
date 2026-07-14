package com.stratum.domain.checkin

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CheckInBatchSelectorTest {

    @Test
    @DisplayName("C1: two chest Increases → only G4 winner stays Increase")
    fun twoChestIncreases() {
        val light =
            provisional(
                name = "Cable Fly",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                setCount = 2,
            )
        val heavy =
            provisional(
                name = "Bench",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                setCount = 4,
            )
        val result = CheckInBatchSelector.select(listOf(heavy, light))
        val byName = result.associateBy { it.input.liftName }
        assertTrue(byName.getValue("Cable Fly").action is CheckInAction.IncreaseVolume)
        assertTrue(byName.getValue("Bench").action is CheckInAction.Maintain)
        assertEquals(
            CheckInRationale.MuscleLeverAlreadyTaken,
            byName.getValue("Bench").rationale,
        )
    }

    @Test
    @DisplayName("C2: chest Increase + chest Swap → Swap wins")
    fun swapBeatsIncreaseSameMuscle() {
        val increase =
            provisional(
                name = "Fly",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
            )
        val swap =
            provisional(
                name = "Bench",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.SwapLift(LiftId("incline"), "Incline"),
            )
        val result = CheckInBatchSelector.select(listOf(increase, swap))
        val byName = result.associateBy { it.input.liftName }
        assertTrue(byName.getValue("Bench").action is CheckInAction.SwapLift)
        assertTrue(byName.getValue("Fly").action is CheckInAction.Maintain)
    }

    @Test
    @DisplayName("C3: chest Cut + chest Increase → both kept (cut does not claim)")
    fun cutDoesNotClaim() {
        val cut =
            provisional(
                name = "Bench",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.CutVolume(1),
            )
        val increase =
            provisional(
                name = "Fly",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
            )
        val result = CheckInBatchSelector.select(listOf(cut, increase))
        assertTrue(result.any { it.action is CheckInAction.CutVolume })
        assertTrue(result.any { it.action is CheckInAction.IncreaseVolume })
    }

    @Test
    @DisplayName("C4: two Cuts same muscle → both kept")
    fun twoCutsKept() {
        val a =
            provisional(
                name = "A",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.CutVolume(1),
            )
        val b =
            provisional(
                name = "B",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.CutVolume(1),
            )
        val result = CheckInBatchSelector.select(listOf(a, b))
        assertEquals(2, result.count { it.action is CheckInAction.CutVolume })
    }

    @Test
    @DisplayName("C5: multi-primary Swap claims first primary only — isolation peer stays open")
    fun multiPrimaryClaimsFirstPrimaryOnly() {
        val bench =
            provisional(
                name = "Bench",
                muscles = setOf(MuscleGroup.Chest, MuscleGroup.Tricep),
                action = CheckInAction.SwapLift(LiftId("incline"), "Incline"),
            )
        val pushdown =
            provisional(
                name = "Pushdown",
                muscles = setOf(MuscleGroup.Tricep),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                kinematic = KinematicProfile.Isolation,
            )
        val result = CheckInBatchSelector.select(listOf(bench, pushdown))
        val byName = result.associateBy { it.input.liftName }
        assertTrue(byName.getValue("Bench").action is CheckInAction.SwapLift)
        // First primary of Bench is Chest (name-sorted); Tricep stays free for pushdown +1.
        assertTrue(byName.getValue("Pushdown").action is CheckInAction.IncreaseVolume)
    }

    @Test
    @DisplayName("C6: lower set count wins among flat increases")
    fun lowerSetCountWins() {
        val four =
            provisional(
                name = "Heavy",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                setCount = 4,
            )
        val two =
            provisional(
                name = "Light",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                setCount = 2,
            )
        val winner =
            CheckInBatchSelector.select(listOf(four, two))
                .single { it.action is CheckInAction.IncreaseVolume }
        assertEquals("Light", winner.input.liftName)
    }

    @Test
    @DisplayName("C7: Dropping beats Flat for +1")
    fun droppingBeatsFlat() {
        val flat =
            provisional(
                name = "FlatLift",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                trend = PerformanceTrend.Flat,
                setCount = 2,
            )
        val dropping =
            provisional(
                name = "DropLift",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                trend = PerformanceTrend.Dropping,
                setCount = 3,
            )
        val winner =
            CheckInBatchSelector.select(listOf(flat, dropping))
                .single { it.action is CheckInAction.IncreaseVolume }
        assertEquals("DropLift", winner.input.liftName)
    }

    @Test
    @DisplayName("C8: Isolation preferred over compound when equal sets/trend")
    fun isolationPreferred() {
        val compound =
            provisional(
                name = "Compound",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                kinematic = KinematicProfile.NonAxialCompound,
            )
        val isolation =
            provisional(
                name = "Isolation",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                kinematic = KinematicProfile.Isolation,
            )
        val winner =
            CheckInBatchSelector.select(listOf(compound, isolation))
                .single { it.action is CheckInAction.IncreaseVolume }
        assertEquals("Isolation", winner.input.liftName)
    }

    @Test
    @DisplayName("C9: name ASC tie-break")
    fun nameTieBreak() {
        val z =
            provisional(
                name = "Zebra",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
            )
        val a =
            provisional(
                name = "Alpha",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
            )
        val winner =
            CheckInBatchSelector.select(listOf(z, a))
                .single { it.action is CheckInAction.IncreaseVolume }
        assertEquals("Alpha", winner.input.liftName)
    }

    @Test
    @DisplayName("C10: different muscles both keep Increase")
    fun differentMusclesBoth() {
        val chest =
            provisional(
                name = "Bench",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
            )
        val ham =
            provisional(
                name = "Curl",
                muscles = setOf(MuscleGroup.Hamstring),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
            )
        val result = CheckInBatchSelector.select(listOf(chest, ham))
        assertEquals(2, result.count { it.action is CheckInAction.IncreaseVolume })
    }

    @Test
    @DisplayName("C11: three soft lifts / two muscles → max one per muscle")
    fun threeLiftsTwoMuscles() {
        val c1 =
            provisional(
                "C1",
                setOf(MuscleGroup.Chest),
                CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                setCount = 2,
            )
        val c2 =
            provisional(
                "C2",
                setOf(MuscleGroup.Chest),
                CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                setCount = 4,
            )
        val h1 =
            provisional(
                "H1",
                setOf(MuscleGroup.Hamstring),
                CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
            )
        val result = CheckInBatchSelector.select(listOf(c1, c2, h1))
        assertEquals(2, result.count { it.action is CheckInAction.IncreaseVolume })
        assertEquals(1, result.count { it.action is CheckInAction.Maintain })
    }

    @Test
    @DisplayName("C12: all Maintains unchanged")
    fun allMaintains() {
        val a =
            provisional("A", setOf(MuscleGroup.Chest), CheckInAction.Maintain)
        val b =
            provisional("B", setOf(MuscleGroup.Hamstring), CheckInAction.Maintain)
        val result = CheckInBatchSelector.select(listOf(a, b))
        assertTrue(result.all { it.action is CheckInAction.Maintain })
    }

    @Test
    @DisplayName("C13: Swap + Increase different muscles both kept")
    fun swapAndIncreaseDifferent() {
        val swap =
            provisional(
                "Bench",
                setOf(MuscleGroup.Chest),
                CheckInAction.SwapLift(LiftId("x"), "X"),
            )
        val inc =
            provisional(
                "Curl",
                setOf(MuscleGroup.Hamstring),
                CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
            )
        val result = CheckInBatchSelector.select(listOf(swap, inc))
        assertTrue(result.any { it.action is CheckInAction.SwapLift })
        assertTrue(result.any { it.action is CheckInAction.IncreaseVolume })
    }

    @Test
    @DisplayName("C14: higher tenure preferred among fresh +1 same muscle")
    fun higherTenurePreferred() {
        val low =
            provisional(
                name = "New",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                tenure = 1,
            )
        val high =
            provisional(
                name = "Older",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                tenure = 3,
            )
        val winner =
            CheckInBatchSelector.select(listOf(low, high))
                .single { it.action is CheckInAction.IncreaseVolume }
        assertEquals("Older", winner.input.liftName)
    }

    @Test
    @DisplayName("C15: demoted rows keep trend/recovery; only action/rationale change")
    fun demotedPreservesInput() {
        val a =
            provisional(
                name = "A",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                trend = PerformanceTrend.Dropping,
                recovery = RecoverySignal.Good,
            )
        val b =
            provisional(
                name = "B",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                trend = PerformanceTrend.Flat,
            )
        val demoted =
            CheckInBatchSelector.select(listOf(a, b))
                .single { it.action is CheckInAction.Maintain }
        assertEquals(PerformanceTrend.Flat, demoted.input.performanceTrend)
        assertEquals(RecoverySignal.Good, demoted.input.recoverySignal)
        assertEquals(CheckInRationale.MuscleLeverAlreadyTaken, demoted.rationale)
    }

    @Test
    @DisplayName("P0: failed-swap-as-+1 re-competes with peer Increase under G4")
    fun failedSwapAsIncreaseRecompetesWithPeer() {
        // Stale compound became +1 after failed swap; light isolation also +1 — isolation wins G4.
        val staleCompound =
            provisional(
                name = "Bench",
                muscles = setOf(MuscleGroup.Chest, MuscleGroup.Tricep),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                rationale =
                    CheckInRationale.SoftStaleIncreaseNoCandidate(
                        SoftPerformanceTrend.Flat,
                        6,
                    ),
                setCount = 4,
                kinematic = KinematicProfile.NonAxialCompound,
                tenure = 6,
            )
        val freshIsolation =
            provisional(
                name = "Fly",
                muscles = setOf(MuscleGroup.Chest),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
                setCount = 2,
                kinematic = KinematicProfile.Isolation,
                tenure = 2,
            )
        val result = CheckInBatchSelector.select(listOf(staleCompound, freshIsolation))
        val byName = result.associateBy { it.input.liftName }
        assertTrue(byName.getValue("Fly").action is CheckInAction.IncreaseVolume)
        assertTrue(byName.getValue("Bench").action is CheckInAction.Maintain)
        assertEquals(CheckInRationale.MuscleLeverAlreadyTaken, byName.getValue("Bench").rationale)
    }

    @Test
    @DisplayName("empty primary muscles never accept non-cut lever")
    fun emptyPrimaryDemoted() {
        val untagged =
            provisional(
                name = "Mystery",
                muscles = emptySet(),
                action = CheckInAction.IncreaseVolume(CHECKIN_VOLUME_STEP),
            )
        val result = CheckInBatchSelector.select(listOf(untagged)).single()
        assertTrue(result.action is CheckInAction.Maintain)
        assertEquals(CheckInRationale.NoClearChange, result.rationale)
    }

    private fun provisional(
        name: String,
        muscles: Set<MuscleGroup>,
        action: CheckInAction,
        setCount: Int = 3,
        tenure: Int = 2,
        trend: PerformanceTrend = PerformanceTrend.Flat,
        recovery: RecoverySignal = RecoverySignal.Good,
        kinematic: KinematicProfile? = KinematicProfile.Isolation,
        rationale: CheckInRationale = CheckInRationale.NoClearChange,
    ): CheckInProvisionalRecommendation =
        CheckInProvisionalRecommendation(
            input =
                LiftCheckInInput(
                    workoutLiftId = WorkoutLiftId("wl-$name"),
                    liftId = LiftId(name.lowercase()),
                    liftName = name,
                    primaryMuscles = muscles,
                    performanceTrend = trend,
                    recoverySignal = recovery,
                    rotationTenureWeeks = tenure,
                    currentSetCount = setCount,
                    kinematicProfile = kinematic,
                ),
            action = action,
            rationale = rationale,
        )
}
