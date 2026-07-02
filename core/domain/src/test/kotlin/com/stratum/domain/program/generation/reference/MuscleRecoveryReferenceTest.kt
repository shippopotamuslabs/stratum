package com.stratum.domain.program.generation.reference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MuscleRecoveryReferenceTest {

    @Test
    @DisplayName("isolation moderate dose requires two calendar days")
    fun isolationModerateDoseRequiresTwoDays() {
        assertEquals(2, minimumRecoveryCalendarGap(MuscleSessionStress(5.0, LiftStressClass.Isolation)))
        assertEquals(1, minimumRecoveryCalendarGap(MuscleSessionStress(2.0, LiftStressClass.Isolation)))
    }

    @Test
    @DisplayName("heavy axial high dose requires four calendar days")
    fun heavyAxialHighDoseRequiresFourDays() {
        assertEquals(4, minimumRecoveryCalendarGap(MuscleSessionStress(10.0, LiftStressClass.HeavyAxial)))
    }

    @Test
    @DisplayName("isolation high dose requires three calendar days")
    fun isolationHighDoseRequiresThreeDays() {
        assertEquals(3, minimumRecoveryCalendarGap(MuscleSessionStress(14.0, LiftStressClass.Isolation)))
    }

    @Test
    @DisplayName("upper compound low dose requires two calendar days")
    fun upperCompoundLowDoseRequiresTwoDays() {
        assertEquals(2, minimumRecoveryCalendarGap(MuscleSessionStress(3.0, LiftStressClass.UpperCompound)))
    }

    @Test
    @DisplayName("upper compound moderate dose requires three calendar days")
    fun upperCompoundModerateDoseRequiresThreeDays() {
        assertEquals(3, minimumRecoveryCalendarGap(MuscleSessionStress(6.0, LiftStressClass.UpperCompound)))
        assertEquals(3, minimumRecoveryCalendarGap(MuscleSessionStress(8.0, LiftStressClass.UpperCompound)))
    }

    @Test
    @DisplayName("unmapped muscles inherit isolation-class spacing when classified as isolation")
    fun unmappedMuscleUsesIsolationClass() {
        assertEquals(2, minimumRecoveryCalendarGap(MuscleSessionStress(6.0, LiftStressClass.Isolation)))
        assertEquals(2, minimumRecoveryCalendarGap(MuscleSessionStress(4.0, LiftStressClass.Isolation)))
    }

    @Test
    @DisplayName("muscleRecoveryGapViolated gates on earlier-session debt and later meaningfulness")
    fun muscleRecoveryGapViolatedUsesEarlierSessionSemantics() {
        val earlier = MuscleSessionStress(5.0, LiftStressClass.Isolation)
        assertEquals(false, muscleRecoveryGapViolated(earlier, laterDose = 5.0, calendarGap = 2))
        assertEquals(true, muscleRecoveryGapViolated(earlier, laterDose = 5.0, calendarGap = 1))
        assertEquals(false, muscleRecoveryGapViolated(earlier, laterDose = 1.5, calendarGap = 1))
    }

    @Test
    @DisplayName("minimumRecoveryGapBetweenSessions gates meaningful exposure on both sessions")
    fun minimumRecoveryGapBetweenSessionsGatesBothSessions() {
        assertEquals(
            0,
            minimumRecoveryGapBetweenSessions(5.0, 1.5, LiftStressClass.Isolation),
        )
        assertEquals(
            2,
            minimumRecoveryGapBetweenSessions(5.0, 5.0, LiftStressClass.Isolation),
        )
    }

    @Test
    @DisplayName("calendarDayPairsWithMaxGap includes gap-1 and gap-2 ordered pairs")
    fun calendarDayPairsWithMaxGapCoversAdjacentAndGapTwo() {
        val pairs = calendarDayPairsWithMaxGap(listOf(0, 2, 4), maxGap = 2)
        assertTrue(pairs.contains(0 to 2))
        assertTrue(pairs.contains(2 to 4))
        assertTrue(pairs.none { calendarGapBetweenDays(it.first, it.second) > 2 })
    }
}
