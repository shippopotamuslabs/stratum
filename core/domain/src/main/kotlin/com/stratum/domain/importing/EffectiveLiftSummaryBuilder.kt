package com.stratum.domain.importing

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.ResponseEvidenceMerger

object EffectiveLiftSummaryBuilder {
    fun build(
        observations: List<LiftEffectivenessObservation>,
        liftsById: Map<LiftId, Lift>,
    ): List<EffectiveLiftHighlight> {
        val eligible =
            observations.filter { observation ->
                observation.rank == 1 &&
                    observation.evidence != EvidenceLevel.InsufficientData &&
                    (
                        observation.evidence == EvidenceLevel.Proven ||
                            observation.confidenceScore >= HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN
                        )
            }

        val byMuscle = mutableMapOf<MuscleGroup, MutableList<LiftEffectivenessObservation>>()
        for (observation in eligible) {
            val lift = liftsById[observation.liftId] ?: continue
            for (muscle in lift.primaryMuscles) {
                byMuscle.getOrPut(muscle) { mutableListOf() }.add(observation)
            }
        }

        val highlights =
            byMuscle.mapNotNull { (muscle, muscleObservations) ->
                val representative = muscleObservations.maxBy { it.effectivenessScore }
                val rankOnes = muscleObservations.filter { it.rank == 1 }
                val mergedEvidence = ResponseEvidenceMerger.mergeLiftEffectivenessEvidence(rankOnes)
                val evidence =
                    if (mergedEvidence == EvidenceLevel.Proven) {
                        EvidenceLevel.Proven
                    } else {
                        representative.evidence
                    }
                liftsById[representative.liftId]?.let { lift ->
                    EffectiveLiftHighlight(
                        liftId = representative.liftId,
                        liftName = lift.name,
                        muscle = muscle,
                        evidence = evidence,
                        effectivenessScore = representative.effectivenessScore,
                        effectivenessTier = UserEffectivenessTier.ModeratelyEffective,
                        confidenceTier =
                            efficacyConfidenceTier(
                                evidence = evidence,
                                epochCount = muscleObservations.map { it.epochId }.distinct().size,
                                peerLiftCount = rankOnes.map { it.liftId }.distinct().size,
                            ),
                    )
                }
            }
                .sortedByDescending { it.effectivenessScore }

        return assignEffectivenessTiers(highlights)
    }

    private fun efficacyConfidenceTier(
        evidence: EvidenceLevel,
        epochCount: Int,
        peerLiftCount: Int,
    ): LandmarkConfidenceTier =
        when {
            evidence == EvidenceLevel.Proven && epochCount >= 2 && peerLiftCount >= 2 ->
                LandmarkConfidenceTier.High
            evidence == EvidenceLevel.Proven ||
                (epochCount >= 1 && peerLiftCount >= 2) ->
                LandmarkConfidenceTier.Medium
            else -> LandmarkConfidenceTier.Low
        }

    private fun assignEffectivenessTiers(
        lifts: List<EffectiveLiftHighlight>,
    ): List<EffectiveLiftHighlight> {
        if (lifts.isEmpty()) return lifts
        val count = lifts.size
        return lifts.mapIndexed { index, lift ->
            val tier =
                when {
                    count == 1 ->
                        if (lift.effectivenessScore > 0.0) {
                            UserEffectivenessTier.ModeratelyEffective
                        } else {
                            UserEffectivenessTier.SomewhatEffective
                        }
                    count == 2 ->
                        if (index == 0) {
                            UserEffectivenessTier.VeryEffective
                        } else {
                            UserEffectivenessTier.SomewhatEffective
                        }
                    index < count / 3 -> UserEffectivenessTier.VeryEffective
                    index < (2 * count) / 3 -> UserEffectivenessTier.ModeratelyEffective
                    else -> UserEffectivenessTier.SomewhatEffective
                }
            lift.copy(effectivenessTier = tier)
        }
    }
}
