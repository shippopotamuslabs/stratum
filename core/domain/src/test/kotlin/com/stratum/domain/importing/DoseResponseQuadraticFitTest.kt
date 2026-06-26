package com.stratum.domain.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DoseResponseQuadraticFitTest {
    @Test
    @DisplayName("Given an inverted-U series when fitting then recovers vertex MAV and lower root MEV")
    fun invertedURecoversVertexAndRoot() {
        // progress = -0.1·v² + 2.0·v - 4.0 → vertex at v=10 (MAV), roots at v≈2.76 and v≈17.24.
        // MEV (lower root) ≈ 2.76; PV (80% of peak) on the ascending limb.
        val points = (2..18 step 1).map { v ->
            val progress = -0.1 * v * v + 2.0 * v - 4.0
            VolumeDosePoint(volume = v.toDouble(), progress = progress)
        }
        val fit = DoseResponseQuadraticFit.fit(points)

        assertNotNull(fit, "inverted-U with spread should fit")
        fit!!
        assertEquals(-0.1, fit.a, 1e-6)
        assertEquals(2.0, fit.b, 1e-6)
        assertEquals(-4.0, fit.c, 1e-6)
        assertEquals(10.0, fit.mavVertex, 1e-4, "MAV vertex should be -b/(2a) = 10")
        assertTrue(fit.mevRoot < fit.mavVertex, "MEV (lower root) should be below MAV")
        assertTrue(fit.mevRoot > 0.0, "MEV should be positive")
        assertTrue(fit.pvPareto < fit.mavVertex, "PV should be on the ascending limb below MAV")
        assertTrue(fit.pvPareto > fit.mevRoot, "PV should be above MEV")
        assertTrue(fit.rSquared > 0.99, "synthetic noise-free data should fit near-perfectly")
    }

    @Test
    @DisplayName("Given an inverted-U fit when computing intervals then Delta Method CIs are available")
    fun invertedUProducesDeltaMethodIntervals() {
        val points = (2..18 step 1).map { v ->
            VolumeDosePoint(volume = v.toDouble(), progress = -0.1 * v * v + 2.0 * v - 4.0)
        }
        val fit = DoseResponseQuadraticFit.fit(points)!!

        val mavInterval = fit.mavInterval()
        assertTrue(mavInterval.isAvailable, "MAV CI should be available for a clean fit")
        assertEquals(IntervalMethod.DeltaMethod, mavInterval.method)
        assertTrue(mavInterval.low < fit.mavVertex, "MAV CI low should be below the point estimate")
        assertTrue(mavInterval.high > fit.mavVertex, "MAV CI high should be above the point estimate")

        val mevInterval = fit.mevInterval()
        assertTrue(mevInterval.isAvailable, "MEV CI should be available for a clean fit")
        assertTrue(mevInterval.low <= fit.mevRoot)

        val pvInterval = fit.pvInterval()
        assertTrue(pvInterval.isAvailable, "PV CI should be available for a clean fit")
    }

    @Test
    @DisplayName("Given too few points when fitting then rejects and returns null")
    fun tooFewPointsRejects() {
        val points = (1..3).map { v -> VolumeDosePoint(v.toDouble(), 1.0) }
        assertNull(DoseResponseQuadraticFit.fit(points), "fewer than MIN_WEEKS_FOR_OLS points should reject")
    }

    @Test
    @DisplayName("Given insufficient volume spread when fitting then rejects and returns null")
    fun insufficientSpreadRejects() {
        // All the same volume — no spread on x.
        val points = (1..8).map { VolumeDosePoint(10.0, it.toDouble()) }
        assertNull(DoseResponseQuadraticFit.fit(points), "no volume spread should reject")
    }

    @Test
    @DisplayName("Given a concave-up (positive a) series when fitting then rejects")
    fun concaveUpRejects() {
        // progress = +0.1·v² + ... → positive leading coefficient, no inverted-U.
        val points = (2..18 step 1).map { v ->
            VolumeDosePoint(volume = v.toDouble(), progress = 0.1 * v * v + 1.0)
        }
        assertNull(DoseResponseQuadraticFit.fit(points), "concave-up (a >= 0) should reject")
    }

    @Test
    @DisplayName("Given a flat progress series when fitting then rejects (no signal)")
    fun flatProgressRejects() {
        val points = (4..18 step 1).map { v -> VolumeDosePoint(v.toDouble(), 0.0) }
        assertNull(DoseResponseQuadraticFit.fit(points), "zero variance in progress (ssTot=0) should reject")
    }

    @Test
    @DisplayName("Given a vertex outside the observed range when fitting then rejects")
    fun vertexOutOfRangeRejects() {
        // Vertex at v=100, way above the observed 4..18 range.
        val points = (4..18 step 1).map { v ->
            // -0.01·v² + 2.0·v + c → vertex at v=100
            VolumeDosePoint(v.toDouble(), -0.01 * v * v + 2.0 * v)
        }
        val fit = DoseResponseQuadraticFit.fit(points)
        assertNull(fit, "vertex outside observed range should reject")
    }
}