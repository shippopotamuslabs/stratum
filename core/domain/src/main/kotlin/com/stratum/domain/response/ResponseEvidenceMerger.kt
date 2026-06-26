package com.stratum.domain.response

/**
 * Aggregates per-epoch observations into cross-epoch evidence labels for program generation.
 */
object ResponseEvidenceMerger {
    fun mergeVolumeEvidence(observations: List<VolumeResponseObservation>): EvidenceLevel {
        if (observations.isEmpty()) return EvidenceLevel.InsufficientData
        if (observations.size < PROVEN_EPOCH_AGREEMENT_COUNT) {
            return observations.maxBy { it.confidenceScore }.evidence
        }
        val qualifying = observations.filter { it.confidenceScore >= HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN }
        if (qualifying.size < PROVEN_EPOCH_AGREEMENT_COUNT) {
            return EvidenceLevel.Hypothesis
        }
        val positive = qualifying.count { it.correlationSlope > 0 }
        val negative = qualifying.count { it.correlationSlope < 0 }
        return when {
            positive >= PROVEN_EPOCH_AGREEMENT_COUNT -> EvidenceLevel.Proven
            negative >= PROVEN_EPOCH_AGREEMENT_COUNT -> EvidenceLevel.Proven
            else -> EvidenceLevel.Hypothesis
        }
    }

    fun mergeLiftEffectivenessEvidence(
        observations: List<LiftEffectivenessObservation>,
    ): EvidenceLevel {
        if (observations.isEmpty()) return EvidenceLevel.InsufficientData
        if (observations.size < PROVEN_EPOCH_AGREEMENT_COUNT) {
            return observations.first().evidence
        }
        val rankOnes = observations.filter { it.rank == 1 }
        if (rankOnes.size < PROVEN_EPOCH_AGREEMENT_COUNT) return EvidenceLevel.Hypothesis
        val stableLift = rankOnes.all { it.liftId == rankOnes.first().liftId }
        return if (stableLift) EvidenceLevel.Proven else EvidenceLevel.Hypothesis
    }
}
