package com.stratum.domain.dashboard

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardCalloutsTest {

    @Test
    fun `strong clean muscle uptrend becomes Progressing callout`() {
        val weeks = weeks(8)
        val muscle = muscle(
            name = "Chest",
            // Strong linear rise well above callout slope threshold, high R².
            indexValues = weeks.indices.map { it * 0.012 },
            lifts = 3,
        )

        val callouts = selectDashboardCallouts(muscles = listOf(muscle), lifts = emptyList())

        assertEquals(1, callouts.size)
        assertEquals("Chest", callouts.single().subjectName)
        assertEquals(CalloutDirection.Progressing, callouts.single().direction)
        assertEquals(CalloutKind.Muscle, callouts.single().kind)
    }

    @Test
    fun `strong clean muscle downtrend becomes Regressing callout`() {
        val weeks = weeks(8)
        val muscle = muscle(
            name = "Tricep",
            indexValues = weeks.indices.map { 0.1 - it * 0.012 },
            lifts = 3,
        )

        val callouts = selectDashboardCallouts(muscles = listOf(muscle), lifts = emptyList())

        assertEquals(CalloutDirection.Regressing, callouts.single().direction)
        assertEquals("Tricep", callouts.single().subjectName)
    }

    @Test
    fun `noisy series with mild slope is not a callout`() {
        // Alternating noise around a tiny drift — low R² should fail the gate.
        val values = listOf(0.01, -0.02, 0.015, -0.018, 0.012, -0.01, 0.008, -0.005)
        val muscle = muscle(name = "Back", indexValues = values, lifts = 4)

        val callouts = selectDashboardCallouts(muscles = listOf(muscle), lifts = emptyList())

        assertTrue(callouts.isEmpty(), "noisy mild drift must not surface")
    }

    @Test
    fun `short series is not a callout even with steep slope`() {
        val muscle = muscle(
            name = "Quad",
            indexValues = listOf(0.0, 0.02, 0.04), // only 3 points
            lifts = 3,
        )

        assertTrue(selectDashboardCallouts(listOf(muscle), emptyList()).isEmpty())
    }

    @Test
    fun `strong lift trend with enough sessions becomes callout`() {
        val weeks = weeks(8)
        val lift = lift(
            name = "Bench Press",
            e1rms = weeks.indices.map { 100.0 + it * 2.0 }, // +2 kg/week
            sessions = 12,
        )

        val callouts = selectDashboardCallouts(muscles = emptyList(), lifts = listOf(lift))

        assertEquals(1, callouts.size)
        assertEquals("Bench Press", callouts.single().subjectName)
        assertEquals(CalloutDirection.Progressing, callouts.single().direction)
        assertEquals(CalloutKind.Lift, callouts.single().kind)
    }

    @Test
    fun `prefers regressing then progressing and caps count`() {
        val muscles = listOf(
            muscle("A", weeks(8).indices.map { 0.1 - it * 0.015 }, lifts = 3),
            muscle("B", weeks(8).indices.map { 0.08 - it * 0.012 }, lifts = 3),
            muscle("C", weeks(8).indices.map { it * 0.014 }, lifts = 3),
            muscle("D", weeks(8).indices.map { it * 0.011 }, lifts = 3),
        )

        val callouts = selectDashboardCallouts(muscles, emptyList(), maxCount = 3)

        assertEquals(3, callouts.size)
        assertEquals(CalloutDirection.Regressing, callouts.first().direction)
        assertTrue(callouts.any { it.direction == CalloutDirection.Progressing })
    }

    @Test
    fun `olsRSquared is near 1 for perfect line and lower for noise`() {
        val clean = weeks(6).mapIndexed { i, w -> TrendPoint(w, i * 1.0) }
        val noisy = listOf(0.0, 5.0, 1.0, 6.0, 2.0, 7.0).mapIndexed { i, v ->
            TrendPoint(weeks(6)[i], v)
        }

        val cleanR2 = olsRSquared(clean)!!
        val noisyR2 = olsRSquared(noisy)!!

        assertTrue(cleanR2 > 0.99, "perfect line R²=$cleanR2")
        assertTrue(noisyR2 < cleanR2, "noise should reduce R²")
    }

    private fun weeks(n: Int): List<Instant> {
        val start = Instant.parse("2025-01-01T00:00:00Z")
        return (0 until n).map { start.plus(it.toLong() * 7, ChronoUnit.DAYS) }
    }

    private fun muscle(
        name: String,
        indexValues: List<Double>,
        lifts: Int,
    ): MuscleSeries {
        val weekStarts = weeks(indexValues.size)
        val progress = weekStarts.zip(indexValues) { w, v -> TrendPoint(w, v) }
        return MuscleSeries(
            muscle = MuscleGroup.Chest,
            displayName = name,
            volumeSeries = progress.map { TrendPoint(it.weekStart, 10.0) },
            progressSeries = progress,
            progressSlope = olsSlope(progress),
            meanWeeklyHardSets = 10.0,
            contributingLiftCount = lifts,
        )
    }

    private fun lift(
        name: String,
        e1rms: List<Double>,
        sessions: Int,
    ): LiftSeries {
        val weekStarts = weeks(e1rms.size)
        val perf = weekStarts.zip(e1rms) { w, v -> TrendPoint(w, v) }
        return LiftSeries(
            liftId = LiftId(name.lowercase().replace(' ', '-')),
            name = name,
            performanceSeries = perf,
            volumeSeries = perf.map { TrendPoint(it.weekStart, 4.0) },
            latestE1rm = Weight(e1rms.last(), Weight.WeightUnit.Kg),
            tenureWeeks = 20,
            sessionCount = sessions,
            isLower = false,
            fatigueProfile = FatigueProfile.High,
        )
    }
}
