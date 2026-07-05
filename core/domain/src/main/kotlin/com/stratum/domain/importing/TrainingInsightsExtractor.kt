package com.stratum.domain.importing

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Pure analytics over normalized history. Computes metrics only — persistence is the use case's job.
 */
object TrainingInsightsExtractor {
    private const val MIN_SESSIONS_FOR_FATIGUE = 3
    private const val MIN_WEEKLY_POINTS_FOR_PROGRESS = 4
    private const val SUPERSET_MAX_MEDIAN_SECONDS = 90.0
    private const val SUPERSET_MIN_CO_OCCURRENCE = 5
    private const val STAGNANT_SLOPE_THRESHOLD = 0.25
    private const val DAYS_PER_WEEK = 7L
    private const val SECONDS_PER_WEEK = 7L * 24L * 3600L
    private const val P90_QUANTILE = 0.9
    private const val REGRESSION_DENOM_EPSILON = 1e-9

    data class AnalysisInput(
        val sessions: List<TrainingSession>,
        val sets: List<LoggedSet>,
        val lifts: List<Lift>,
        /**
         * Tier A proven lift ids from `ResolveImportLiftsUseCase` — persisted into metadata so
         * program generation (OS-6) can prioritize proven lifts. Empty for insight-only runs.
         */
        val provenLiftIds: Set<LiftId> = emptySet(),
    )

    data class AnalysisOutput(
        val metadata: ImportAnalysisMetadata,
        val liftInsights: List<LiftTrainingInsights>,
        val supersetAffinities: List<SupersetAffinity>,
        val volumeBaselines: List<MuscleVolumeBaseline>,
    )

    fun analyze(input: AnalysisInput): AnalysisOutput {
        val liftById = input.lifts.associateBy { it.id }
        val window = selectFullHistoryWindow(input.sessions, input.sets)
        val windowSets = input.sets.filter { it.completedAt in window.start..window.endInclusive }
        val windowSessions = input.sessions.filter { it.startedAt in window.start..window.endInclusive }

        val liftInsights = computeLiftInsights(windowSets)
        val supersets = computeSupersetAffinities(windowSets)
        val volumeBaselines = computeVolumeBaselines(windowSets, liftById, window.start, window.endInclusive)
        val unmatched = windowSets
            .filter { it.liftId == null }
            .map { it.exerciseNameRaw }
            .distinct()

        val metadata = ImportAnalysisMetadata(
            windowStart = window.start,
            windowEnd = window.endInclusive,
            provenLiftIds = input.provenLiftIds.toList(),
            unmatchedExerciseNames = unmatched,
            totalSessions = windowSessions.size,
            totalSets = windowSets.size,
        )
        return AnalysisOutput(metadata, liftInsights, supersets, volumeBaselines)
    }

    private data class Window(val start: Instant, val endInclusive: Instant)

    private fun selectFullHistoryWindow(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
    ): Window {
        if (sessions.isEmpty() && sets.isEmpty()) {
            val now = Instant.now()
            return Window(now, now)
        }
        val sessionStarts = sessions.map { it.startedAt }
        val setTimes = sets.map { it.completedAt }
        val earliest =
            (sessionStarts + setTimes).minOrNull()
                ?: Instant.now()
        val latest =
            (sessionStarts + setTimes).maxOrNull()
                ?: Instant.now()
        return Window(earliest, latest)
    }

    private fun computeLiftInsights(
        sets: List<LoggedSet>,
    ): List<LiftTrainingInsights> {
        val byLift = sets.filter { it.liftId != null }.groupBy { it.liftId!! }
        return byLift.mapNotNull { (liftId, liftSets) ->
            val bySession = liftSets.groupBy { it.sessionId }
            if (bySession.size < MIN_SESSIONS_FOR_FATIGUE) return@mapNotNull null

            val slopes = mutableListOf<Double>()
            val intercepts = mutableListOf<Double>()
            val sessionPeaks = mutableListOf<Pair<Instant, Double>>()

            bySession.forEach { (_, sessionSets) ->
                val ordered = sessionSets.filter { it.reps > 0 }.sortedBy { it.setIndex }
                if (ordered.isEmpty()) return@forEach
                val e1rms = ordered.map { EstimatedOneRm.calculate(it.weight.amount, it.reps) }
                if (e1rms.size >= 2) {
                    val regression = linearRegression(e1rms.indices.map { it + 1.0 }, e1rms)
                    slopes += regression.slope
                    intercepts += regression.intercept
                }
                val peak = e1rms.maxOrNull() ?: return@forEach
                sessionPeaks += ordered.last().completedAt to peak
            }

            val avgSlope = slopes.average().takeIf { slopes.isNotEmpty() }
            val avgIntercept = intercepts.average().takeIf { intercepts.isNotEmpty() }
            val estimatedOneRm = sessionPeaks.maxByOrNull { it.second }?.second?.let { value ->
                val unit = liftSets.first().weight.unit
                Weight(value, unit)
            }
            val progressTrend = computeProgressTrend(sessionPeaks)
            val lastPerformed = sessionPeaks.maxByOrNull { it.first }?.first

            LiftTrainingInsights(
                liftId = liftId,
                estimatedOneRm = estimatedOneRm,
                fatigueDecaySlope = avgSlope,
                fatigueDecayIntercept = avgIntercept,
                sessionCount = bySession.size,
                progressTrend = progressTrend,
                lastPerformedAt = lastPerformed,
            )
        }
    }

    private fun computeProgressTrend(sessionPeaks: List<Pair<Instant, Double>>): ProgressTrend? {
        if (sessionPeaks.size < MIN_WEEKLY_POINTS_FOR_PROGRESS) return null
        val weekly = sessionPeaks
            .groupBy { it.first.truncatedTo(ChronoUnit.DAYS).epochSecond / SECONDS_PER_WEEK }
            .map { (_, peaks) -> peaks.maxOf { it.second } }
        if (weekly.size < MIN_WEEKLY_POINTS_FOR_PROGRESS) return null
        val regression = linearRegression(weekly.indices.map { it.toDouble() }, weekly)
        return when {
            regression.slope > STAGNANT_SLOPE_THRESHOLD -> ProgressTrend.Improving
            regression.slope < -STAGNANT_SLOPE_THRESHOLD -> ProgressTrend.Regressing
            else -> ProgressTrend.Stagnant
        }
    }

    private fun computeSupersetAffinities(sets: List<LoggedSet>): List<SupersetAffinity> {
        val resolved = sets.filter { it.liftId != null }
        val pairDeltas = mutableMapOf<Pair<LiftId, LiftId>, MutableList<Double>>()

        resolved.groupBy { it.sessionId }.forEach { (_, sessionSets) ->
            val ordered = sessionSets.sortedBy { it.completedAt }
            for (i in 0 until ordered.lastIndex) {
                val current = ordered[i]
                val next = ordered[i + 1]
                val a = current.liftId!!
                val b = next.liftId!!
                if (a == b) continue
                val key = if (a.value < b.value) a to b else b to a
                val seconds = Duration.between(current.completedAt, next.completedAt).seconds.toDouble()
                pairDeltas.getOrPut(key) { mutableListOf() } += seconds
            }
        }

        return pairDeltas.mapNotNull { (pair, deltas) ->
            val sorted = deltas.sorted()
            val median = sorted[sorted.size / 2]
            if (median > SUPERSET_MAX_MEDIAN_SECONDS || deltas.size < SUPERSET_MIN_CO_OCCURRENCE) {
                return@mapNotNull null
            }
            SupersetAffinity(
                liftA = pair.first,
                liftB = pair.second,
                medianRestSeconds = median,
                coOccurrenceCount = deltas.size,
                confidence = (deltas.size / 20.0).coerceAtMost(1.0),
            )
        }
    }

    private fun computeVolumeBaselines(
        sets: List<LoggedSet>,
        liftById: Map<LiftId, Lift>,
        windowStart: Instant,
        windowEnd: Instant,
    ): List<MuscleVolumeBaseline> {
        val weeks = (ChronoUnit.DAYS.between(windowStart, windowEnd) / DAYS_PER_WEEK).coerceAtLeast(1).toInt()
        val weeklyMuscleSets = mutableMapOf<MuscleGroup, MutableList<Int>>()

        sets.filter { it.liftId != null }.forEach { set ->
            val lift = liftById[set.liftId] ?: return@forEach
            val weekIndex = (ChronoUnit.DAYS.between(windowStart, set.completedAt) / DAYS_PER_WEEK)
                .toInt()
                .coerceIn(0, weeks - 1)
            val muscles = lift.primaryMuscles + lift.secondaryMuscles
            muscles.forEach { muscle ->
                val weeksList = weeklyMuscleSets.getOrPut(muscle) {
                    MutableList(weeks) { 0 }
                }
                if (weekIndex in weeksList.indices) {
                    weeksList[weekIndex] = weeksList[weekIndex] + 1
                }
            }
        }

        return weeklyMuscleSets.map { (muscle, counts) ->
            val mean = counts.average()
            val sorted = counts.sorted()
            val p90Index = ((sorted.size - 1) * P90_QUANTILE).toInt().coerceIn(0, sorted.lastIndex)
            val trend = if (counts.size >= 2) {
                linearRegression(counts.indices.map { it.toDouble() }, counts.map { it.toDouble() }).slope
            } else {
                null
            }
            MuscleVolumeBaseline(
                muscle = muscle,
                meanWeeklyHardSets = mean,
                p90WeeklyHardSets = sorted[p90Index].toDouble(),
                trendSlope = trend,
            )
        }
    }

    private data class Regression(val slope: Double, val intercept: Double)

    private fun linearRegression(xs: List<Double>, ys: List<Double>): Regression {
        require(xs.size == ys.size && xs.isNotEmpty())
        val n = xs.size
        val sumX = xs.sum()
        val sumY = ys.sum()
        val sumXY = xs.zip(ys).sumOf { (x, y) -> x * y }
        val sumX2 = xs.sumOf { it * it }
        val denom = n * sumX2 - sumX * sumX
        val slope = if (abs(denom) < REGRESSION_DENOM_EPSILON) 0.0 else (n * sumXY - sumX * sumY) / denom
        val intercept = (sumY - slope * sumX) / n
        return Regression(slope, intercept)
    }

    fun computeVolumeBaselinesFromSnapshots(
        sets: List<com.stratum.domain.training.LoggedSet>,
    ): List<MuscleVolumeBaseline> {
        if (sets.isEmpty()) return emptyList()
        val windowStart = sets.minOf { it.completedAt }
        val windowEnd = sets.maxOf { it.completedAt }
        val weeks = (ChronoUnit.DAYS.between(windowStart, windowEnd) / DAYS_PER_WEEK).coerceAtLeast(1).toInt()
        val weeklyMuscleSets = mutableMapOf<MuscleGroup, MutableList<Int>>()
        val weekStart = windowStart.truncatedTo(ChronoUnit.DAYS)

        sets.forEach { set ->
            val weekIndex =
                (ChronoUnit.DAYS.between(windowStart, set.completedAt) / DAYS_PER_WEEK)
                    .toInt()
                    .coerceIn(0, weeks - 1)
            val credits = mutableMapOf<MuscleGroup, MutableMap<Instant, Double>>()
            com.stratum.domain.response.MuscleVolumeCredit.accumulateSetCredits(
                liftContext = set.liftContext,
                weekStart = weekStart,
                muscleWeekCredits = credits,
            )
            credits.forEach { (muscle, weekCredits) ->
                val credit = weekCredits.values.sum()
                if (credit <= 0.0) return@forEach
                val weeksList = weeklyMuscleSets.getOrPut(muscle) { MutableList(weeks) { 0 } }
                if (weekIndex in weeksList.indices) {
                    weeksList[weekIndex] = weeksList[weekIndex] + credit.toInt().coerceAtLeast(1)
                }
            }
        }

        return weeklyMuscleSets.map { (muscle, counts) ->
            val mean = counts.average()
            val sorted = counts.sorted()
            val p90Index = ((sorted.size - 1) * P90_QUANTILE).toInt().coerceIn(0, sorted.lastIndex)
            val trend =
                if (counts.size >= 2) {
                    linearRegression(counts.indices.map { it.toDouble() }, counts.map { it.toDouble() }).slope
                } else {
                    null
                }
            MuscleVolumeBaseline(
                muscle = muscle,
                meanWeeklyHardSets = mean,
                p90WeeklyHardSets = sorted[p90Index].toDouble(),
                trendSlope = trend,
            )
        }
    }
}
