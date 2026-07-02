package com.stratum.domain.response.modeling

import com.stratum.domain.importing.VolumeDosePoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class SqrtDoseResponseFitTest {
    @Test
    @DisplayName("Given a monotonic sqrt series when fitting then recovers slope and intercept")
    fun monotonicSeriesRecoversParameters() {
        val points =
            (4..18).map { volume ->
                val progress = 0.5 + 0.2 * sqrt(volume.toDouble())
                VolumeDosePoint(volume = volume.toDouble(), progress = progress)
            }
        val fit = SqrtDoseResponseFit.fit(points)

        assertNotNull(fit, "monotonic sqrt series with spread should fit")
        fit!!
        assertEquals(0.5, fit.intercept, 1e-4)
        assertEquals(0.2, fit.slope, 1e-4)
        assertTrue(fit.rSquared > 0.99, "synthetic noise-free data should fit near-perfectly")
    }

    @Test
    @DisplayName("Given too few points when fitting then rejects and returns null")
    fun tooFewPointsRejects() {
        val points = (1..3).map { v -> VolumeDosePoint(v.toDouble(), 1.0) }
        assertNull(SqrtDoseResponseFit.fit(points), "fewer than MIN_POINTS should reject")
    }

    @Test
    @DisplayName("Given insufficient volume spread when fitting then rejects and returns null")
    fun insufficientSpreadRejects() {
        val points = (1..8).map { VolumeDosePoint(10.0, it.toDouble()) }
        assertNull(SqrtDoseResponseFit.fit(points), "no volume spread should reject")
    }

    @Test
    @DisplayName("Given a flat progress series when fitting then rejects (no signal)")
    fun flatProgressRejects() {
        val points = (4..18 step 1).map { v -> VolumeDosePoint(v.toDouble(), 0.0) }
        assertNull(SqrtDoseResponseFit.fit(points), "zero variance in progress (ssTot=0) should reject")
    }

    @Test
    @DisplayName("Given a valid fit when evaluating marginal slope then decreases with dose")
    fun marginalSlopeDecreasesWithDose() {
        val points =
            (4..18).map { volume ->
                VolumeDosePoint(volume = volume.toDouble(), progress = 0.5 + 0.2 * sqrt(volume.toDouble()))
            }
        val fit = SqrtDoseResponseFit.fit(points)!!

        assertTrue(fit.marginalSlopeAt(6.0) > fit.marginalSlopeAt(16.0))
    }

    @Test
    @DisplayName("Given epoch-blocked points when bootstrapping then returns 5th to 95th percentile band")
    fun epochBlockedBootstrapProducesBand() {
        val points =
            (4..18).map { volume ->
                VolumeDosePoint(volume = volume.toDouble(), progress = 0.5 + 0.2 * sqrt(volume.toDouble()))
            }
        val epochIds = points.indices.map { if (it < points.size / 2) 0 else 1 }
        val band =
            EpochBlockedBootstrap.predictionBand(
                points = points,
                epochIds = epochIds,
                referenceDose = 13.0,
                iterations = 400,
            )

        assertNotNull(band)
        band!!
        assertTrue(band.low <= band.pointEstimate)
        assertTrue(band.high >= band.pointEstimate)
        assertTrue(band.high > band.low)
    }
}
