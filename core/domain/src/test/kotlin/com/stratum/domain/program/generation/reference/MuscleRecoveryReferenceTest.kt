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

    // Bug #1: muscle-mass penalty — isolation lifts on large compound muscles get +12h at Low tier.

    @Test
    @DisplayName("leg extension Low on Quad requires two calendar days (36h → gap 2)")
    fun legExtensionLowOnQuadRequiresTwoDays() {
        // 2 sets = 2.0 credits = Low tier. Isolation Low baseline = 24h; +12h muscle-mass = 36h → ceil = 2.
        assertEquals(2, minimumRecoveryCalendarGap(MuscleSessionStress(2.0, LiftStressClass.Isolation, muscle = "Quad")))
    }

    @Test
    @DisplayName("leg extension Moderate on Quad requires two calendar days (48h → gap 2, unchanged)")
    fun legExtensionModerateOnQuadRequiresTwoDays() {
        // 5 sets = 5.0 credits = Moderate tier. Isolation Moderate = 48h (no mass penalty at Moderate).
        assertEquals(2, minimumRecoveryCalendarGap(MuscleSessionStress(5.0, LiftStressClass.Isolation, muscle = "Quad")))
    }

    @Test
    @DisplayName("leg extension High on Quad requires three calendar days (72h → gap 3, unchanged)")
    fun legExtensionHighOnQuadRequiresThreeDays() {
        assertEquals(3, minimumRecoveryCalendarGap(MuscleSessionStress(14.0, LiftStressClass.Isolation, muscle = "Quad")))
    }

    @Test
    @DisplayName("bicep curl Low on Bicep stays at one calendar day (24h — small muscle, no penalty)")
    fun bicepCurlLowOnBicepStaysOneDay() {
        assertEquals(1, minimumRecoveryCalendarGap(MuscleSessionStress(2.0, LiftStressClass.Isolation, muscle = "Bicep")))
    }

    @Test
    @DisplayName("lying hamstring curl Low on Hamstring stays at one calendar day (Shortened bias, no lengthened penalty)")
    fun lyingHamstringCurlLowOnHamstringStaysOneDay() {
        assertEquals(
            1,
            minimumRecoveryCalendarGap(
                MuscleSessionStress(2.0, LiftStressClass.Isolation, muscle = "Hamstring", muscleLengthBias = "Shortened"),
            ),
        )
    }

    @Test
    @DisplayName("seated hamstring curl Low on Hamstring requires two calendar days (Lengthened bias → +12h → 36h)")
    fun seatedHamstringCurlLowOnHamstringRequiresTwoDays() {
        // The catalog's only KneeFlexion lift is lying (Shortened), but the machinery must handle
        // a future seated (Lengthened) entry. Isolation Low + Hamstring + Lengthened = 24 + 12 = 36h.
        assertEquals(
            2,
            minimumRecoveryCalendarGap(
                MuscleSessionStress(2.0, LiftStressClass.Isolation, muscle = "Hamstring", muscleLengthBias = "Lengthened"),
            ),
        )
    }

    @Test
    @DisplayName("seated hamstring curl Moderate on Hamstring requires three calendar days (Lengthened → 48 + 12 = 60h → gap 3)")
    fun seatedHamstringCurlModerateOnHamstringRequiresThreeDays() {
        assertEquals(
            3,
            minimumRecoveryCalendarGap(
                MuscleSessionStress(5.0, LiftStressClass.Isolation, muscle = "Hamstring", muscleLengthBias = "Lengthened"),
            ),
        )
    }

    @Test
    @DisplayName("isLargeCompoundMuscle identifies large compound muscle groups")
    fun largeCompoundMuscleSet() {
        assertTrue(isLargeCompoundMuscle("Quad"))
        assertTrue(isLargeCompoundMuscle("Hamstring"))
        assertTrue(isLargeCompoundMuscle("Glute"))
        assertTrue(isLargeCompoundMuscle("Chest"))
        assertTrue(isLargeCompoundMuscle("Back"))
        assertEquals(false, isLargeCompoundMuscle("Bicep"))
        assertEquals(false, isLargeCompoundMuscle("Calf"))
        assertEquals(false, isLargeCompoundMuscle("LateralDeltoid"))
    }

    // Secondary-only hitchhiking on large compounds stays on the 24h Isolation Low lane.

    @Test
    @DisplayName("secondary-only Glute Low (squat hitchhiking) stays at one calendar day")
    fun secondaryOnlyGluteLowStaysOneDay() {
        // 4 sets squat × 0.5 secondary = 2.0 credits, zero primary Glute.
        assertEquals(
            1,
            minimumRecoveryCalendarGap(
                MuscleSessionStress(
                    fractionalSets = 2.0,
                    stressClass = LiftStressClass.Isolation,
                    muscle = "Glute",
                    primaryFractionalSets = 0.0,
                ),
            ),
        )
    }

    @Test
    @DisplayName("secondary-only Glute Moderate still prices 48h Isolation (gap 2) for soft/advisory paths")
    fun secondaryOnlyGluteModerateStillGapTwoOnHours() {
        // Hours table for Isolation Moderate is 48h regardless of secondary-only; hard forbids
        // no longer force gap-2 for secondary-only, but soft/advisory debt still uses the table.
        assertEquals(
            2,
            minimumRecoveryCalendarGap(
                MuscleSessionStress(
                    fractionalSets = 4.5,
                    stressClass = LiftStressClass.Isolation,
                    muscle = "Glute",
                    primaryFractionalSets = 0.0,
                ),
            ),
        )
    }

    @Test
    @DisplayName("primary isolation Low on Glute still requires two calendar days (mass surcharge)")
    fun primaryIsolationLowOnGluteRequiresTwoDays() {
        assertEquals(
            2,
            minimumRecoveryCalendarGap(
                MuscleSessionStress(
                    fractionalSets = 2.0,
                    stressClass = LiftStressClass.Isolation,
                    muscle = "Glute",
                    primaryFractionalSets = 2.0,
                ),
            ),
        )
    }

    @Test
    @DisplayName("muscleRecoveryGapViolated allows adjacent secondary-only Glute Low")
    fun secondaryOnlyGluteLowNotViolatedOnAdjacentDay() {
        val earlier =
            MuscleSessionStress(
                fractionalSets = 2.0,
                stressClass = LiftStressClass.Isolation,
                muscle = "Glute",
                primaryFractionalSets = 0.0,
            )
        assertEquals(false, muscleRecoveryGapViolated(earlier, laterDose = 4.5, calendarGap = 1))
    }
}
