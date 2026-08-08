package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.SetType
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WorkingSetEligibility")
class WorkingSetEligibilityTest {

    @Test
    fun `standard and match backoff count - mini and drop do not`() {
        assertTrue(context(SetType.Standard, repMin = 8, repMax = 12, rirGoal = 2).countsTowardMainBlockFatigue())
        assertTrue(
            context(SetType.MyoRep, repMin = 0, repMax = 0, rirGoal = 1).countsTowardMainBlockFatigue(),
            "match backoff keeps rirGoal",
        )
        assertTrue(
            context(SetType.MyoRep, repMin = 10, repMax = 15, rirGoal = 1).countsTowardMainBlockFatigue(),
            "floor activation has band",
        )
        assertFalse(
            context(SetType.MyoRep, repMin = 0, repMax = 0, rirGoal = 0).countsTowardMainBlockFatigue(),
            "auto mini is RIR-only zeros",
        )
        assertFalse(context(SetType.DropSet, repMin = 0, repMax = 0, rirGoal = 0).countsTowardMainBlockFatigue())
        assertTrue(context(null, repMin = null, repMax = null, rirGoal = null).countsTowardMainBlockFatigue())
    }

    @Test
    fun `LoggedSet delegates to liftContext`() {
        val mini =
            LoggedSet(
                id = LoggedSetId("s1"),
                sessionId = TrainingSessionId("ts"),
                exerciseNameRaw = "Curl",
                liftId = null,
                setIndex = 3,
                weight = Weight(40.0, Weight.WeightUnit.Lb),
                reps = 3,
                rir = 0,
                completedAt = Instant.EPOCH,
                liftContext = context(SetType.MyoRep, 0, 0, 0),
            )
        assertFalse(mini.countsTowardMainBlockFatigue())
    }

    private fun context(
        setType: SetType?,
        repMin: Int?,
        repMax: Int?,
        rirGoal: Int?,
    ) = LoggedLiftContext(
        liftName = "Curl",
        setType = setType,
        repMin = repMin,
        repMax = repMax,
        rirGoal = rirGoal,
    )
}
