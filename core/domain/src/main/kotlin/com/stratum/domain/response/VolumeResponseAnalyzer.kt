package com.stratum.domain.response

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant
import java.time.temporal.ChronoUnit

object VolumeResponseAnalyzer {
    fun analyze(
        muscle: MuscleGroup,
        anchorLiftId: LiftId,
        epoch: Epoch,
        input: ResponseExtractionInput,
        deloadWeeks: Set<Instant>,
    ): VolumeResponseObservation? {
        val cleanSets = TrainingSignalCleaner.clean(input)
        val epochWeeksUsed = epochWeekCount(epoch)
        if (epochWeeksUsed < MIN_EPOCH_WEEKS_FOR_CORRELATION) return null

        val stimulusByWeek =
            StimulusSeriesBuilder.weeklyHardSetsByMuscle(epoch, cleanSets, input.lifts)[muscle]
                ?.associate { it.weekStart to it.hardSets.toDouble() }
                ?: emptyMap()
        val outcomeByWeek =
            OutcomeSeriesBuilder.weeklyAnchorOutcomes(epoch, anchorLiftId, cleanSets)
                .mapNotNull { signal ->
                    val outcome = signal.smoothedEstimatedOneRm ?: signal.peakEstimatedOneRm ?: return@mapNotNull null
                    signal.weekStart to outcome
                }.toMap()

        val pairedPoints =
            stimulusByWeek.keys
                .union(outcomeByWeek.keys)
                .sorted()
                .mapNotNull { weekStart ->
                    if (weekStart in deloadWeeks) return@mapNotNull null
                    val hardSets = stimulusByWeek[weekStart] ?: return@mapNotNull null
                    val outcome = outcomeByWeek[weekStart] ?: return@mapNotNull null
                    weekStart to (hardSets to outcome)
                }
        val regressionPoints = subsampleWeeklyPoints(pairedPoints)
        if (regressionPoints.size < MIN_WEEKLY_POINTS_FOR_OLS) return null

        val hardSetValues = regressionPoints.map { it.second.first }
        val ys = regressionPoints.map { it.second.second }
        val xs =
            if (hardSetValues.distinct().size <= 1) {
                regressionPoints.indices.map { it + 1.0 }
            } else {
                hardSetValues
            }
        val regression = ResponseRegressionSupport.linearRegression(xs, ys) ?: return null
        val confidenceScore =
            (regression.rSquared * REGRESSION_R2_WEIGHT) +
                (regressionPoints.size.toDouble() / epochWeeksUsed.toDouble() * REGRESSION_COMPLETENESS_WEIGHT)
        val evidence =
            if (confidenceScore < HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN) {
                EvidenceLevel.InsufficientData
            } else {
                EvidenceLevel.Hypothesis
            }

        return VolumeResponseObservation(
            muscle = muscle,
            epochId = epoch.id,
            anchorLiftId = anchorLiftId,
            stimulus =
                StimulusDescriptor(
                    epochId = epoch.id,
                    experimentId = null,
                    muscle = muscle,
                    pattern = null,
                    liftId = anchorLiftId,
                    weeklyHardSets = hardSetValues.average(),
                    sessionsPerWeek = regressionPoints.size.toDouble() / epochWeeksUsed.toDouble(),
                    intensityProxy = null,
                ),
            outcome =
                OutcomeDescriptor(
                    anchorLiftId = anchorLiftId,
                    metric = OutcomeMetric.WeeklyPeakE1rm,
                    value = ys.average(),
                    windowWeeks = epochWeeksUsed,
                    reliability =
                        RegressionReliability(
                            rSquared = regression.rSquared,
                            outlierCount = 0,
                            completenessScore = regressionPoints.size.toDouble() / epochWeeksUsed.toDouble(),
                        ),
                ),
            correlationSlope = regression.slope,
            confidenceScore = confidenceScore,
            evidence = evidence,
            weeklyPointsUsed = regressionPoints.size,
            epochWeeksUsed = epochWeeksUsed,
            observedWeeklySetRange =
                WeeklySetRange(
                    min = hardSetValues.minOrNull() ?: 0.0,
                    max = hardSetValues.maxOrNull() ?: 0.0,
                ),
            bestObservedWeeklySetRange = null,
        )
    }

    private fun epochWeekCount(epoch: Epoch): Int {
        val startWeek = WeeklySignalSupport.weekStartFor(epoch.start)
        val endWeek = WeeklySignalSupport.weekStartFor(epoch.end)
        val days = ChronoUnit.DAYS.between(startWeek, endWeek)
        return (days / 7 + 1).toInt().coerceAtLeast(1)
    }

    private fun subsampleWeeklyPoints(
        points: List<Pair<Instant, Pair<Double, Double>>>,
    ): List<Pair<Instant, Pair<Double, Double>>> {
        if (points.size <= MIN_WEEKLY_POINTS_FOR_OLS) return points
        if (points.size % 2 == 0) {
            return points.chunked(2).mapNotNull { chunk ->
                if (chunk.isEmpty()) return@mapNotNull null
                val weekStart = chunk.first().first
                val avgHardSets = chunk.map { it.second.first }.average()
                val avgOutcome = chunk.map { it.second.second }.average()
                weekStart to (avgHardSets to avgOutcome)
            }
        }
        return points
    }
}
