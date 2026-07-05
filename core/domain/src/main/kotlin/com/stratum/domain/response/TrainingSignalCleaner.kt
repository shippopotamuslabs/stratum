package com.stratum.domain.response

import com.stratum.domain.importing.EstimatedOneRm
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId

object TrainingSignalCleaner {
    private const val LB_TO_KG = 0.45359237

    fun clean(input: ResponseExtractionInput): List<CleanTrainingSet> {
        val resolvedSets = input.sets.filter { it.liftId != null && it.reps > 0 }
        val mixedUnitLifts = detectMixedUnitLifts(resolvedSets)
        val sessionTopWeightByLift = sessionTopNormalizedWeight(resolvedSets)

        return resolvedSets.map { set ->
            val liftId = set.liftId!!
            val normalizedWeight = normalizeToKg(set.weight)
            val flags = mutableSetOf<TrainingSignalQualityFlag>()
            if (liftId in mixedUnitLifts) {
                flags += TrainingSignalQualityFlag.MixedUnits
            }
            val estimatedOneRm = EstimatedOneRm.calculate(normalizedWeight, set.reps)
            val rirAdjustedEstimatedOneRm =
                set.rir?.let { rir ->
                    EstimatedOneRm.calculate(normalizedWeight, set.reps + rir)
                } ?: run {
                    flags += TrainingSignalQualityFlag.MissingRir
                    null
                }

            val isWorkingSet =
                classifyWorkingSet(
                    set = set,
                    normalizedWeight = normalizedWeight,
                    sessionTopWeight = sessionTopWeightByLift[set.sessionId to liftId] ?: normalizedWeight,
                    flags = flags,
                )

            CleanTrainingSet(
                sourceSet = set,
                normalizedWeight = normalizedWeight,
                estimatedOneRm = estimatedOneRm,
                rirAdjustedEstimatedOneRm = rirAdjustedEstimatedOneRm,
                isWorkingSet = isWorkingSet,
                qualityFlags = flags,
            )
        }
    }

    private fun classifyWorkingSet(
        set: LoggedSet,
        normalizedWeight: Double,
        sessionTopWeight: Double,
        flags: MutableSet<TrainingSignalQualityFlag>,
    ): Boolean {
        val rir = set.rir
        if (rir != null) {
            if (rir >= HIGH_RIR_WARMUP_THRESHOLD) {
                flags += TrainingSignalQualityFlag.HighRirWarmupExcluded
                return false
            }
            return true
        }
        val relativeLoad = if (sessionTopWeight > 0.0) normalizedWeight / sessionTopWeight else 1.0
        if (relativeLoad < LOW_RELATIVE_LOAD_FRACTION) {
            flags += TrainingSignalQualityFlag.LowRelativeLoadWarmupExcluded
            return false
        }
        return true
    }

    private fun detectMixedUnitLifts(sets: List<LoggedSet>): Set<LiftId> {
        val unitsByLift =
            sets.filter { it.liftId != null }
                .groupBy { it.liftId!! }
                .mapValues { (_, liftSets) -> liftSets.map { it.weight.unit }.toSet() }
        return unitsByLift.filter { (_, units) -> units.size > 1 }.keys
    }

    private fun sessionTopNormalizedWeight(
        sets: List<LoggedSet>,
    ): Map<Pair<TrainingSessionId, LiftId>, Double> =
        sets.filter { it.liftId != null }
            .groupBy { it.sessionId to it.liftId!! }
            .mapValues { (_, sessionSets) ->
                sessionSets.maxOf { normalizeToKg(it.weight) }
            }

    private fun normalizeToKg(weight: Weight): Double =
        when (weight.unit) {
            Weight.WeightUnit.Kg -> weight.amount
            Weight.WeightUnit.Lb -> weight.amount * LB_TO_KG
        }
}
