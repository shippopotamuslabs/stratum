@file:Suppress("MagicNumber")

package com.stratum.domain.response.modeling

import com.stratum.domain.importing.ImportStubLiftGuesser
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.MuscleVolumeCredit
import com.stratum.domain.response.TrainingSignalQualityFlag
import kotlin.math.sqrt

/**
 * Builds a weekly muscle strength index from log-e1RM of lifts where [muscle] is a **primary**
 * mover only. Secondary co-movers (e.g. triceps on bench) are excluded so the index tracks direct
 * target strength rather than being diluted by compounds limited by other muscles. Volume/stimulus
 * accounting still credits secondaries via [MuscleVolumeCredit].
 */
object MuscleOutcomeIndexBuilder {
    data class LiftMuscleCredit(
        val liftId: LiftId,
        val credit: Double,
        val isPrimary: Boolean,
    )

    fun creditsForMuscle(muscle: MuscleGroup, lifts: List<Lift>): List<LiftMuscleCredit> =
        lifts.mapNotNull { lift ->
            val movementPattern = ImportStubLiftGuesser.effectiveMovementPattern(lift)
            val primaryMuscles = ImportStubLiftGuesser.effectivePrimaryMuscles(lift, movementPattern)
            if (muscle in primaryMuscles) {
                LiftMuscleCredit(
                    liftId = lift.id,
                    credit = MuscleVolumeCredit.PRIMARY_SET_CREDIT,
                    isPrimary = true,
                )
            } else {
                null
            }
        }

    fun build(
        muscle: MuscleGroup,
        liftPerformance: List<LiftWeeklyPerformance>,
        lifts: List<Lift>,
    ): List<MuscleWeeklyOutcomeIndex> {
        val credits = creditsForMuscle(muscle, lifts)
        if (credits.isEmpty()) return emptyList()
        val creditByLift = credits.associateBy { it.liftId }
        val performanceByLift = liftPerformance.groupBy { it.liftId }
        val baselines = estimateLiftBaselines(performanceByLift)
        val weekStarts =
            performanceByLift.filterKeys { it in creditByLift }
                .values
                .flatten()
                .map { it.weekStart }
                .distinct()
                .sorted()
        if (weekStarts.isEmpty()) return emptyList()

        val rawByWeek =
            weekStarts.mapNotNull { weekStart ->
                val contributions =
                    credits.mapNotNull { credit ->
                        val row = performanceByLift[credit.liftId]?.firstOrNull { it.weekStart == weekStart }
                            ?: return@mapNotNull null
                        val baseline = baselines[credit.liftId] ?: return@mapNotNull null
                        val normalized = row.logE1rm - baseline
                        val weight = credit.credit * row.reliability
                        Triple(normalized, weight, credit)
                    }
                if (contributions.isEmpty()) return@mapNotNull null
                val totalWeight = contributions.sumOf { it.second }
                if (totalWeight <= 0.0) return@mapNotNull null
                val indexValue = contributions.sumOf { it.first * it.second } / totalWeight
                val compositionHash =
                    contributions.sortedBy { it.third.liftId.value }
                        .joinToString("|") { (_, weight, credit) ->
                            "${credit.liftId.value}:${(weight * 10).toInt()}"
                        }
                weekStart to IndexWeekDraft(
                    indexValue = indexValue,
                    contributingLiftCount = contributions.size,
                    effectiveLiftCount = contributions.count { it.second >= 0.25 },
                    // All contributors are primary by construction.
                    primaryCoverage = 1.0,
                    compositionHash = compositionHash,
                    qualityFlags = contributions.flatMap { performanceByLift[it.third.liftId].orEmpty() }
                        .filter { it.weekStart == weekStart }
                        .flatMap { it.qualityFlags }
                        .toSet(),
                    variance = contributions.map { it.first - indexValue }.let { residuals ->
                        if (residuals.size < 2) null else residuals.sumOf { it * it } / (residuals.size - 1)
                    },
                )
            }.toMap()

        val sortedWeeks = rawByWeek.keys.sorted()
        return sortedWeeks.mapIndexed { index, weekStart ->
            val draft = rawByWeek.getValue(weekStart)
            val priorHash = rawByWeek[sortedWeeks.getOrNull(index - 1)]?.compositionHash.orEmpty()
            val compositionDrift = jaccardDrift(priorHash, draft.compositionHash)
            MuscleWeeklyOutcomeIndex(
                muscle = muscle,
                weekStart = weekStart,
                indexValue = draft.indexValue,
                indexStdError = draft.variance?.let { sqrt(it) },
                contributingLiftCount = draft.contributingLiftCount,
                effectiveLiftCount = draft.effectiveLiftCount,
                primaryCoverage = draft.primaryCoverage,
                compositionHash = draft.compositionHash,
                compositionDrift = compositionDrift,
                qualityFlags = draft.qualityFlags,
            )
        }
    }

    private data class IndexWeekDraft(
        val indexValue: Double,
        val contributingLiftCount: Int,
        val effectiveLiftCount: Int,
        val primaryCoverage: Double,
        val compositionHash: String,
        val qualityFlags: Set<TrainingSignalQualityFlag>,
        val variance: Double?,
    )

    private fun estimateLiftBaselines(
        performanceByLift: Map<LiftId, List<LiftWeeklyPerformance>>,
    ): Map<LiftId, Double> =
        performanceByLift.mapValues { (_, rows) ->
            val sorted = rows.sortedBy { it.weekStart }
            val baselineRows =
                sorted.take(ResponseModelingConstants.LIFT_BASELINE_WEEKS)
                    .ifEmpty { sorted }
            if (baselineRows.size < ResponseModelingConstants.MIN_LIFT_WEEKS_FOR_BASELINE) {
                baselineRows.map { it.logE1rm }.average()
            } else {
                val values = baselineRows.map { it.logE1rm }.sorted()
                values[values.size / 2]
            }
        }

    private fun jaccardDrift(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        val leftTokens = left.split("|").toSet()
        val rightTokens = right.split("|").toSet()
        if (leftTokens.isEmpty() && rightTokens.isEmpty()) return 0.0
        val intersection = leftTokens.intersect(rightTokens).size.toDouble()
        val union = leftTokens.union(rightTokens).size.toDouble()
        return 1.0 - intersection / union
    }
}
