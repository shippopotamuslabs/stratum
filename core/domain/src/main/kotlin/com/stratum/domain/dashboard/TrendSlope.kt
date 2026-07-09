package com.stratum.domain.dashboard

/**
 * Ordinary least-squares slope over a [TrendPoint] series (x = week index 0,1,2,…, y = value).
 * Returns `null` when there are fewer than two points. Used to derive [SeriesDirection] for badge
 * coloring and sorting.
 */
fun olsSlope(points: List<TrendPoint>): Double? {
    val fit = olsFit(points) ?: return null
    return fit.slope
}

/**
 * Coefficient of determination (R²) for the OLS line through [points]. Returns `null` when the
 * series is too short or has zero variance (perfectly flat). Used as a confidence gate so noisy
 * zig-zags with a slight OLS tilt do not surface as callouts.
 */
fun olsRSquared(points: List<TrendPoint>): Double? {
    val fit = olsFit(points) ?: return null
    return fit.rSquared
}

/**
 * Shared OLS fit: slope + R². `null` when fewer than two points or x-variance is zero.
 */
internal fun olsFit(points: List<TrendPoint>): OlsFit? {
    if (points.size < 2) return null
    val xs = points.indices.map { it.toDouble() }
    val ys = points.map { it.value }
    val meanX = xs.average()
    val meanY = ys.average()
    var num = 0.0
    var den = 0.0
    for (i in points.indices) {
        num += (xs[i] - meanX) * (ys[i] - meanY)
        den += (xs[i] - meanX).square()
    }
    if (den == 0.0) return null
    val slope = num / den
    val intercept = meanY - slope * meanX
    var ssTot = 0.0
    var ssRes = 0.0
    for (i in points.indices) {
        val y = ys[i]
        val predicted = intercept + slope * xs[i]
        ssRes += (y - predicted).square()
        ssTot += (y - meanY).square()
    }
    if (ssTot == 0.0) return null
    return OlsFit(slope = slope, rSquared = (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0))
}

internal data class OlsFit(
    val slope: Double,
    val rSquared: Double,
)

private fun Double.square(): Double = this * this

/**
 * Maps a slope to a [SeriesDirection] with a symmetric dead-band so trivial jitter reads as Flat.
 * [threshold] is in the series's native units per week.
 */
fun slopeToDirection(slope: Double?, threshold: Double): SeriesDirection = when {
    slope == null -> SeriesDirection.Insufficient
    slope > threshold -> SeriesDirection.Up
    slope < -threshold -> SeriesDirection.Down
    else -> SeriesDirection.Flat
}
