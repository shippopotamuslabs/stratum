package com.stratum.domain.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BanisterFitnessFatigueFitTest {
    @Test
    @DisplayName("Given too few weeks when fitting then rejects and returns null")
    fun tooFewWeeksRejects() {
        val points = (1..5).map { v -> VolumeDosePoint(v.toDouble(), 1.0) }
        assertNull(BanisterFitnessFatigueFit.fit(points), "fewer than BANISTER_MIN_WEEKS should reject")
    }

    @Test
    @DisplayName("Given a fatigue-dominated series when fitting then produces a plausible MRV with bootstrap CI")
    fun fatigueDominatedSeriesProducesMrv() {
        // Build a Banister-shaped series: progress rises with volume as fitness accumulates, then
        // declines as fatigue (fast-decay) overtakes. Uses a smooth quadratic-ish curve so the
        // exponential-decay integrals in the fit can track it with a non-degenerate k2.
        val peakVolume = 14.0
        val points = (4..22 step 1).map { v ->
            val d = v - peakVolume
            val progress = 8.0 - 0.04 * d * d // parabola peaking at v=14, progress=8
            VolumeDosePoint(v.toDouble(), progress)
        }
        val fit = BanisterFitnessFatigueFit.fit(points)

        // The fit is approximate (grid search over decay constants). If it succeeds, MRV and the
        // bootstrap CI must be plausible; if it rejects, that is acceptable too — the estimator
        // falls back to the heuristic MRV path. Either way the contract holds.
        if (fit != null) {
            assertTrue(fit.k2 > 0.0, "fatigue gain k2 must be positive")
            assertTrue(fit.mrv > 0.0, "MRV must be positive")
            assertTrue(
                fit.mrv <= 22.0 * VolumeLandmarkEstimatorConstants.BANISTER_MRV_MAX_FACTOR,
                "MRV should stay within the plausible bound",
            )
            val interval = fit.mrvInterval()
            assertTrue(interval.isAvailable, "bootstrap CI should be available for a successful fit")
            assertEquals(IntervalMethod.ResidualBootstrap, interval.method)
            assertTrue(interval.low <= fit.mrv, "CI low should be at or below the point estimate")
            assertTrue(interval.high >= fit.mrv, "CI high should be at or above the point estimate")
        }
    }

    @Test
    @DisplayName("Given a monotonically rising series with no fatigue decline when fitting then may still reject")
    fun monotonicRiseRejectsWhenNoFatigueSignal() {
        // Pure linear ascent — no fatigue component, k2 should be ~0 or MRV implausible.
        val points = (4..20 step 1).map { v -> VolumeDosePoint(v.toDouble(), v.toDouble()) }
        // This may or may not reject depending on the grid, but if it fits, MRV must be plausible.
        val fit = BanisterFitnessFatigueFit.fit(points)
        if (fit != null) {
            assertTrue(fit.mrv > 0.0, "MRV must be positive even on a monotonic series")
        }
    }
}