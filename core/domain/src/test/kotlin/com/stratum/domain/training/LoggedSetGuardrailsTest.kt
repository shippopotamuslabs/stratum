package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("LoggedSet guardrails (STR-168)")
class LoggedSetGuardrailsTest {

    private fun loggedSet(
        setIndex: Int = 0,
        reps: Int = 8,
        weightAmount: Double = 100.0,
        rir: Int? = 2,
        liftId: LiftId? = LiftId("lift-1"),
        partialReps: Int? = null,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId("set-1"),
            sessionId = TrainingSessionId("session-1"),
            exerciseNameRaw = "Barbell Squat",
            liftId = liftId,
            setIndex = setIndex,
            weight = Weight(weightAmount, Weight.WeightUnit.Lb),
            reps = reps,
            rir = rir,
            completedAt = Instant.parse("2025-01-01T10:30:00Z"),
            liftContext = LoggedLiftContext(liftName = "Barbell Squat"),
            partialReps = partialReps,
        )

    @Test
    @DisplayName("rejects a negative setIndex")
    fun rejectsNegativeSetIndex() {
        assertThrows<IllegalArgumentException> { loggedSet(setIndex = -1) }
    }

    @Test
    @DisplayName("rejects a negative RIR but allows null and zero")
    fun rirRange() {
        assertThrows<IllegalArgumentException> { loggedSet(rir = -1) }
        assertEquals(0, loggedSet(rir = 0).rir)
        assertNull(loggedSet(rir = null).rir)
    }

    @Test
    @DisplayName("zero-rep and zero-weight sets stay constructible as legitimate states")
    fun legitimateStatesConstructible() {
        val imported = loggedSet(reps = 0, liftId = null)
        assertEquals(0, imported.reps)
        val bodyweight = loggedSet(weightAmount = 0.0)
        assertEquals(0.0, bodyweight.weight.amount)
    }

    @Test
    @DisplayName("normalizedPartialReps is owned by the type")
    fun normalizedPartialRepsOwnedByType() {
        assertNull(loggedSet(partialReps = null).normalizedPartialReps)
        assertNull(loggedSet(partialReps = 0).normalizedPartialReps)
        assertNull(loggedSet(partialReps = -2).normalizedPartialReps)
        assertEquals(3, loggedSet(partialReps = 3).normalizedPartialReps)
        // copy() carries raw values; the member still defines the meaning.
        assertNull(loggedSet(partialReps = 3).copy(partialReps = 0).normalizedPartialReps)
        assertEquals(5, loggedSet().copy(partialReps = 5).normalizedPartialReps)
    }

    @Test
    @DisplayName("isInsightCreditable encodes the sanitizer rule")
    fun insightCreditable() {
        assertTrue(loggedSet().isInsightCreditable)
        assertTrue(loggedSet(weightAmount = 0.0).isInsightCreditable)
        assertFalse(loggedSet(reps = 0).isInsightCreditable)
        assertFalse(loggedSet(reps = -1).isInsightCreditable)
        assertFalse(loggedSet(weightAmount = -5.0).isInsightCreditable)
    }

    @Test
    @DisplayName("isPersonalRecordEligible needs a resolved lift and a completed rep")
    fun personalRecordEligible() {
        assertTrue(loggedSet().isPersonalRecordEligible)
        assertTrue(loggedSet(weightAmount = 0.0).isPersonalRecordEligible)
        assertFalse(loggedSet(liftId = null).isPersonalRecordEligible)
        assertFalse(loggedSet(reps = 0).isPersonalRecordEligible)
    }
}
