package com.stratum.domain.response

internal object ResponseRegressionSupport {
    data class RegressionResult(
        val slope: Double,
        val intercept: Double,
        val rSquared: Double,
    )

    fun linearRegression(xs: List<Double>, ys: List<Double>): RegressionResult? {
        if (xs.size != ys.size || xs.size < 2) return null
        val n = xs.size
        val sumX = xs.sum()
        val sumY = ys.sum()
        val sumXY = xs.zip(ys).sumOf { (x, y) -> x * y }
        val sumX2 = xs.sumOf { it * it }
        val denom = n * sumX2 - sumX * sumX
        if (kotlin.math.abs(denom) < REGRESSION_DENOM_EPSILON) return null
        val slope = (n * sumXY - sumX * sumY) / denom
        val intercept = (sumY - slope * sumX) / n
        val meanY = sumY / n
        val ssTot = ys.sumOf { (it - meanY) * (it - meanY) }
        val ssRes = ys.indices.sumOf { index ->
            val predicted = intercept + slope * xs[index]
            (ys[index] - predicted) * (ys[index] - predicted)
        }
        val rSquared =
            if (ssTot <= REGRESSION_RSQUARED_EPSILON) {
                0.0
            } else {
                (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0)
            }
        return RegressionResult(slope = slope, intercept = intercept, rSquared = rSquared)
    }
}
