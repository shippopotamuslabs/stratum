package com.stratum.domain.dashboard

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MuscleProgressIndexTest {
    @Test
    fun `returns null when fewer than 2 contributing lifts`() {
        val liftA = LiftId("a")
        val w0 = NOW.minus(28, ChronoUnit.DAYS)
        val w1 = NOW.minus(21, ChronoUnit.DAYS)
        val w2 = NOW.minus(14, ChronoUnit.DAYS)
        val perf = listOf(
            perf(liftA, w0, 100.0), perf(liftA, w1, 105.0), perf(liftA, w2, 110.0),
        )
        val shares = mapOf(liftA to 1.0)

        val result = MuscleProgressIndex.compute(MuscleGroup.Chest, perf, shares)

        assertNull(result)
    }

    @Test
    fun `returns null when fewer than 3 shared weeks`() {
        val liftA = LiftId("a")
        val liftB = LiftId("b")
        val w0 = NOW.minus(14, ChronoUnit.DAYS)
        val perf = listOf(perf(liftA, w0, 100.0), perf(liftB, w0, 100.0))
        val shares = mapOf(liftA to 0.5, liftB to 0.5)

        val result = MuscleProgressIndex.compute(MuscleGroup.Chest, perf, shares)

        assertNull(result)
    }

    @Test
    fun `normalizes each lift to its own first-in-window week as 100`() {
        val squat = LiftId("squat")
        val curl = LiftId("curl")
        val w0 = NOW.minus(28, ChronoUnit.DAYS)
        val w1 = NOW.minus(21, ChronoUnit.DAYS)
        val w2 = NOW.minus(14, ChronoUnit.DAYS)
        // Squat: 200 → 220 (10% up). Curl: 50 → 55 (10% up). Equal weight.
        val perf = listOf(
            perf(squat, w0, 200.0), perf(squat, w1, 210.0), perf(squat, w2, 220.0),
            perf(curl, w0, 50.0), perf(curl, w1, 52.5), perf(curl, w2, 55.0),
        )
        val shares = mapOf(squat to 0.5, curl to 0.5)

        val result = MuscleProgressIndex.compute(MuscleGroup.Quad, perf, shares)

        assertNotNull(result)
        val series = result!!
        assertEquals(100.0, series.first().value, 0.01, "first week baseline = 100")
        assertEquals(110.0, series.last().value, 0.01, "both lifts up 10% → index up 10%")
    }

    @Test
    fun `volume-weighting favors the higher-volume lift`() {
        val heavy = LiftId("heavy")
        val light = LiftId("light")
        val w0 = NOW.minus(28, ChronoUnit.DAYS)
        val w1 = NOW.minus(21, ChronoUnit.DAYS)
        val w2 = NOW.minus(14, ChronoUnit.DAYS)
        // Heavy: flat (100→100). Light: up 20% (100→120).
        val perf = listOf(
            perf(heavy, w0, 100.0), perf(heavy, w1, 100.0), perf(heavy, w2, 100.0),
            perf(light, w0, 100.0), perf(light, w1, 110.0), perf(light, w2, 120.0),
        )
        // Heavy gets 80% of volume, light 20%.
        val shares = mapOf(heavy to 0.8, light to 0.2)

        val result = MuscleProgressIndex.compute(MuscleGroup.Quad, perf, shares)!!

        // Week 2: (100 * 0.8 + 120 * 0.2) / (0.8 + 0.2) = 104.
        assertEquals(104.0, result.last().value, 0.01, "weighted toward flat heavy lift → modest gain")
    }

    @Test
    fun `slope sign reflects direction`() {
        val a = LiftId("a")
        val b = LiftId("b")
        val weeks = (0..4).map { NOW.minus((28 - it * 7).toLong(), ChronoUnit.DAYS) }
        // Both lifts declining 2% per week.
        val perf = listOf(
            perf(a, weeks[0], 100.0), perf(a, weeks[1], 98.0), perf(a, weeks[2], 96.0),
            perf(a, weeks[3], 94.0), perf(a, weeks[4], 92.0),
            perf(b, weeks[0], 100.0), perf(b, weeks[1], 98.0), perf(b, weeks[2], 96.0),
            perf(b, weeks[3], 94.0), perf(b, weeks[4], 92.0),
        )
        val shares = mapOf(a to 0.5, b to 0.5)

        val result = MuscleProgressIndex.compute(MuscleGroup.Chest, perf, shares)!!
        val slope = olsSlope(result)

        assertNotNull(slope)
        assertTrue(slope!! < 0, "declining series → negative slope, got $slope")
    }

    private fun perf(liftId: LiftId, weekStart: Instant, peakE1rm: Double): LiftWeeklyPerformance =
        LiftWeeklyPerformance(
            liftId = liftId,
            weekStart = weekStart,
            peakE1rm = peakE1rm,
            smoothedE1rm = peakE1rm,
            logE1rm = kotlin.math.ln(peakE1rm.coerceAtLeast(1.0)),
            hardSets = 4.0,
            sessions = 1,
            meanRir = 2.0,
            reliability = 0.9,
            qualityFlags = emptySet<TrainingSignalQualityFlag>(),
        )

    private companion object {
        val NOW = Instant.parse("2025-06-01T00:00:00Z")
    }
}
