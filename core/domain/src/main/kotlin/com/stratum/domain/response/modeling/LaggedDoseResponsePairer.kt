@file:Suppress("MagicNumber")

package com.stratum.domain.response.modeling

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.ResponseRegressionSupport
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln

object LaggedDoseResponsePairer {
    data class PairingResult(
        val points: List<MuscleDoseResponsePoint>,
        val selectedLagWeeks: Int,
        val responseWindowWeeks: Int,
        val predictiveScore: Double?,
    )

    fun pair(
        muscle: MuscleGroup,
        stimulus: List<MuscleWeeklyStimulus>,
        indices: List<MuscleWeeklyOutcomeIndex>,
    ): PairingResult {
        val stimulusByWeek = stimulus.associateBy { it.weekStart }
        val indexByWeek = indices.associateBy { it.weekStart }
        val weekStarts = stimulusByWeek.keys.union(indexByWeek.keys).sorted()
        if (weekStarts.size < ResponseModelingConstants.MIN_PAIRED_POINTS_FOR_MODEL) {
            return PairingResult(emptyList(), 0, ResponseModelingConstants.DEFAULT_RESPONSE_WINDOW_WEEKS, null)
        }

        val ewmaDose = computeEwmaDose(weekStarts, stimulusByWeek)
        var bestLag = 0
        var bestScore = Double.NEGATIVE_INFINITY
        var bestPoints = emptyList<MuscleDoseResponsePoint>()
        for (lag in ResponseModelingConstants.CANDIDATE_LAG_WEEKS) {
            val points = buildPoints(muscle, weekStarts, ewmaDose, indexByWeek, stimulusByWeek, lag)
            val score = predictiveScore(points)
            if (score != null && score > bestScore) {
                bestScore = score
                bestLag = lag
                bestPoints = points
            } else if (score == null && bestPoints.isEmpty() && points.size > bestPoints.size) {
                bestLag = lag
                bestPoints = points
            }
        }
        return PairingResult(
            points = bestPoints,
            selectedLagWeeks = bestLag,
            responseWindowWeeks = ResponseModelingConstants.DEFAULT_RESPONSE_WINDOW_WEEKS,
            predictiveScore = bestScore.takeIf { it != Double.NEGATIVE_INFINITY },
        )
    }

    private fun computeEwmaDose(
        weekStarts: List<Instant>,
        stimulusByWeek: Map<Instant, MuscleWeeklyStimulus>,
    ): Map<Instant, Double> {
        val alpha = 2.0 / (ResponseModelingConstants.EWMA_SPAN_WEEKS + 1.0)
        val out = linkedMapOf<Instant, Double>()
        var previous: Double? = null
        weekStarts.forEach { weekStart ->
            val current = stimulusByWeek[weekStart]?.hardSets ?: 0.0
            previous =
                if (previous == null) {
                    current
                } else {
                    alpha * current + (1.0 - alpha) * previous!!
                }
            out[weekStart] = previous!!
        }
        return out
    }

    private fun buildPoints(
        muscle: MuscleGroup,
        weekStarts: List<Instant>,
        ewmaDose: Map<Instant, Double>,
        indexByWeek: Map<Instant, MuscleWeeklyOutcomeIndex>,
        stimulusByWeek: Map<Instant, MuscleWeeklyStimulus>,
        lagWeeks: Int,
    ): List<MuscleDoseResponsePoint> {
        val window = ResponseModelingConstants.DEFAULT_RESPONSE_WINDOW_WEEKS
        return weekStarts.mapNotNull { weekStart ->
            val stimulusWeek = stimulusByWeek[weekStart]
            if (stimulusWeek?.deloadFlag == true) return@mapNotNull null
            val dose = ewmaDose[weekStart] ?: return@mapNotNull null
            if (dose <= 0.0) return@mapNotNull null
            val startIndex = weekStarts.indexOf(weekStart) + lagWeeks
            if (startIndex < 0 || startIndex >= weekStarts.size) return@mapNotNull null
            val responseWeeks = weekStarts.drop(startIndex).take(window + 1)
            val responseValues =
                responseWeeks.mapNotNull { responseWeek ->
                    indexByWeek[responseWeek]?.indexValue
                }
            if (responseValues.size < 2) return@mapNotNull null
            val response = forwardLogSlope(responseValues)
            MuscleDoseResponsePoint(
                muscle = muscle,
                weekStart = weekStart,
                dose = dose,
                response = response,
                lagWeeks = lagWeeks,
                responseWindowWeeks = window,
            )
        }
    }

    private fun forwardLogSlope(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val xs = values.indices.map { it.toDouble() }
        return ResponseRegressionSupport.linearRegression(xs, values)?.slope
            ?: ((values.last() - values.first()) / (values.size - 1).coerceAtLeast(1))
    }

    private fun predictiveScore(points: List<MuscleDoseResponsePoint>): Double? {
        if (points.size < ResponseModelingConstants.MIN_PAIRED_POINTS_FOR_MODEL) return null
        val holdoutFrom = (points.size * 0.75).toInt().coerceAtLeast(4)
        if (holdoutFrom >= points.size) return null
        val train = points.take(holdoutFrom)
        val test = points.drop(holdoutFrom)
        val regression = ResponseRegressionSupport.linearRegression(train.map { it.dose }, train.map { it.response })
            ?: return null
        val errors =
            test.map { point ->
                val predicted = regression.intercept + regression.slope * point.dose
                kotlin.math.abs(point.response - predicted)
            }
        return (-errors.average()).takeIf { it.isFinite() }
    }
}
