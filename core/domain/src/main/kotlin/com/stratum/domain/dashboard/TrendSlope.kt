package com.stratum.domain.dashboard

/**
 * Ordinary least-squares slope over a [TrendPoint] series (x = week index 0,1,2,…, y = value).
 * Returns `null` when there are fewer than two points. Used to derive [SeriesDirection] for badge
 * coloring and sorting.
 */
fun olsSlope(points: List<TrendPoint>): Double? {
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
    return num / den
}

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
