package com.stratum.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pins the behavior of [normalizeSparklineSeries]: independent per-series normalization into a
 * centered band so two differently-scaled series (e.g. weekly set count vs. a 100+ progress index
 * or a 200+ e1RM) each show their shape and both stay vertically centered. Conservative: a std
 * floor and gain cap keep near-constant data flat/centered rather than amplifying noise.
 *
 * Band constants (must mirror the production values):
 *   BAND_MIN = 0.15, BAND_MAX = 0.85, BAND_MID = 0.5, BAND_HALF = 0.35, GAIN = 0.6.
 */
class SparklineNormalizationTest {

    private val bandMin = 0.15f
    private val bandMax = 0.85f
    private val mid = 0.5f
    private val bandHalf = 0.35f
    // Float arithmetic on the clamped extremes lands a hair inside the band, so range checks use
    // a small tolerance rather than the strict `in bandMin..bandMax`.
    private val bandEps = 1e-4f

    private fun inBand(v: Float): Boolean = v >= bandMin - bandEps && v <= bandMax + bandEps

    @Test
    @DisplayName("flat series maps every point to the midline (stays centered)")
    fun flatSeriesIsCenteredAtMid() {
        val out = normalizeSparklineSeries(listOf(5f, 5f, 5f, 5f))

        assertEquals(4, out.size)
        out.forEach { v -> assertEquals(mid, v, 1e-5f) }
    }

    @Test
    @DisplayName("near-constant series with engaged std floor stays clustered near mid (conservative)")
    fun nearConstantSeriesStaysCentered() {
        // rawStd ≈ 0.066; std floor = |mean| * 0.04 = 0.242 engages, so the small fluctuation is
        // *not* magnified to the band edges. The three 6s sit well inside the band (~0.38), and the
        // single 6.2 hits the gain cap at BAND_MAX.
        val out = normalizeSparklineSeries(listOf(6f, 6f, 6f, 6.2f))

        assertEquals(4, out.size)
        // Every output stays inside the band.
        out.forEach { v -> assertTrue(inBand(v), "out of band: $v") }
        // The three identical 6s cluster near (but not pinned at) mid — they stay centered, not
        // blasted down to BAND_MIN by the tiny std.
        val sixes = out.take(3)
        sixes.forEach { v -> assertTrue(v in 0.30f..0.45f, "6 should sit near mid: $v") }
        // The lone 6.2 is the most extreme z and hits the clamp at BAND_MAX.
        assertEquals(bandMax, out.last(), bandEps)
    }

    @Test
    @DisplayName("varied series spans the band and preserves input ordering")
    fun variedSeriesFillsBandMonotonically() {
        // Min and max inputs are the most extreme z-scores; intermediate values land between them.
        val input = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)
        val out = normalizeSparklineSeries(input)

        assertEquals(input.size, out.size)
        out.forEach { v -> assertTrue(inBand(v), "out of band: $v") }
        // Linear ramp: outputs are monotonic non-decreasing.
        for (i in 1 until out.size) {
            assertTrue(out[i] >= out[i - 1] - 1e-6f, "not monotonic at $i: ${out[i]} < ${out[i - 1]}")
        }
        // Min input maps to BAND_MIN, max to BAND_MAX (clamped extremes).
        assertEquals(bandMin, out.first(), bandEps)
        assertEquals(bandMax, out.last(), bandEps)
    }

    @Test
    @DisplayName("a single extreme outlier does not push any point outside the band")
    fun outlierIsClamped() {
        val out = normalizeSparklineSeries(listOf(10f, 10f, 10f, 10f, 10_000f))

        out.forEach { v -> assertTrue(inBand(v), "out of band: $v") }
        // The outlier saturates at BAND_MAX (clamped to +GAIN).
        assertEquals(bandMax, out.last(), bandEps)
    }

    @Test
    @DisplayName("two series at very different magnitudes each land in the same band and stay centered")
    fun independentNormalizationAcrossScales() {
        // Volume (weekly hard sets) and e1RM differ by ~30x. After independent normalization both
        // occupy the same band and neither dominates the shared scale.
        val volume = normalizeSparklineSeries(listOf(3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f))
        val e1rm = normalizeSparklineSeries(
            listOf(100f, 120f, 145f, 170f, 195f, 220f, 245f, 270f, 290f, 300f),
        )

        (volume + e1rm).forEach { v -> assertTrue(inBand(v), "out of band: $v") }
        // Both span (close to) the full band — neither is pinned flat.
        val volumeRange = volume.max() - volume.min()
        val e1rmRange = e1rm.max() - e1rm.min()
        assertTrue(volumeRange > 2f * bandHalf * 0.8f, "volume range too small: $volumeRange")
        assertTrue(e1rmRange > 2f * bandHalf * 0.8f, "e1rm range too small: $e1rmRange")
        // Both midpoints hover near the band center → vertically centered.
        assertEquals(mid, (volume.min() + volume.max()) / 2f, bandHalf * 0.25f)
        assertEquals(mid, (e1rm.min() + e1rm.max()) / 2f, bandHalf * 0.25f)
    }

    @Test
    @DisplayName("series shorter than the line threshold is returned unchanged")
    fun shortSeriesPassthrough() {
        val single = listOf(5f)
        val empty = emptyList<Float>()

        assertEquals(single, normalizeSparklineSeries(single))
        assertEquals(empty, normalizeSparklineSeries(empty))
    }
}
