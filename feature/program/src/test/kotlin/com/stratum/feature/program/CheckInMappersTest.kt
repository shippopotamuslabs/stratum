package com.stratum.feature.program

import com.stratum.domain.checkin.CheckInAction
import com.stratum.domain.checkin.CheckInApplyCommand
import com.stratum.domain.checkin.CheckInRationale
import com.stratum.domain.checkin.CheckInRecommendation
import com.stratum.domain.checkin.PerformanceTrend
import com.stratum.domain.checkin.RecoverySignal
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.feature.program.ui.CheckInRecommendationUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CheckInMappersTest {

    @Test
    @DisplayName("Maintain recommendations are dropped from UI")
    fun maintainDropped() {
        val ui =
            sampleRec(CheckInAction.Maintain, CheckInRationale.ClimbingMaintain).toUi()
        assertNull(ui)
    }

    @Test
    @DisplayName("Swap UI maps to Swap apply command")
    fun swapToCommand() {
        val ui =
            sampleRec(
                CheckInAction.SwapLift(LiftId("incline"), "Incline"),
                CheckInRationale.SoftStaleSwap(
                    softTrend = com.stratum.domain.checkin.SoftPerformanceTrend.Flat,
                    tenureWeeks = 5,
                ),
            ).toUi() as CheckInRecommendationUi.Swap
        val cmd = ui.toApplyCommand() as CheckInApplyCommand.Swap
        assertEquals(LiftId("incline"), cmd.newLiftId)
    }

    @Test
    @DisplayName("Increase volume uses absolute target set count")
    fun increaseAbsolute() {
        val ui =
            sampleRec(
                CheckInAction.IncreaseVolume(1),
                CheckInRationale.SoftFreshIncrease(
                    softTrend = com.stratum.domain.checkin.SoftPerformanceTrend.Flat,
                    tenureWeeks = 2,
                ),
                setCount = 3,
            ).toUi() as CheckInRecommendationUi.IncreaseVolume
        val cmd = ui.toApplyCommand() as CheckInApplyCommand.SetWorkingSets
        assertEquals(4, cmd.targetSetCount)
    }

    @Test
    @DisplayName("Cut with removeSets=0 produces no apply command")
    fun floorCutNoCommand() {
        val ui =
            sampleRec(
                CheckInAction.CutVolume(0),
                CheckInRationale.AlreadyAtFloorSets,
                setCount = 1,
            ).toUi() as CheckInRecommendationUi.CutVolume
        assertTrue(ui.isFloorOnly)
        assertNull(ui.toApplyCommand())
    }

    @Test
    @DisplayName("Rejected recommendation produces no command")
    fun rejectedNoCommand() {
        val ui =
            sampleRec(
                CheckInAction.IncreaseVolume(1),
                CheckInRationale.NoClearChange,
                setCount = 2,
            ).toUi(accepted = false) as CheckInRecommendationUi.IncreaseVolume
        assertNull(ui.toApplyCommand())
    }

    private fun sampleRec(
        action: CheckInAction,
        rationale: CheckInRationale,
        setCount: Int = 3,
    ): CheckInRecommendation =
        CheckInRecommendation(
            workoutLiftId = WorkoutLiftId("wl-1"),
            liftId = LiftId("bench"),
            liftName = "Bench",
            primaryMuscles = setOf(MuscleGroup.Chest),
            performanceTrend = PerformanceTrend.Flat,
            recoverySignal = RecoverySignal.Good,
            rotationTenureWeeks = 3,
            currentSetCount = setCount,
            action = action,
            rationale = rationale,
        )
}
