package com.stratum.domain.program

import com.stratum.domain.importing.MuscleVolumeHighlight
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.ResponseEvidenceMerger
import com.stratum.domain.response.VolumeResponseObservation
import java.time.Instant
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup

data class VolumeResponseInfluence(
    val muscle: com.stratum.domain.onboarding.MuscleGroup,
    val anchorLiftId: com.stratum.domain.lift.identity.LiftId,
    val correlationSlope: Double,
    val confidenceScore: Double,
    val evidence: EvidenceLevel,
)

data class LiftEffectivenessInfluence(
    val pattern: com.stratum.domain.lift.MovementPattern,
    val liftId: com.stratum.domain.lift.identity.LiftId,
    val effectivenessScore: Double,
    val rank: Int,
    val evidence: EvidenceLevel,
)

data class ResponseProfileInformational(
    val volumeResponses: List<VolumeResponseObservation>,
    val liftEffectiveness: List<LiftEffectivenessObservation>,
)

data class ResponseProfilePayload(
    val volumeResponses: List<VolumeResponseInfluence>,
    val liftEffectiveness: List<LiftEffectivenessInfluence>,
    val volumeSnapshots: List<MuscleVolumeHighlight>,
    val informational: ResponseProfileInformational,
    val epochsAnalyzed: Int,
    val analysisWindowStart: Instant,
    val analysisWindowEnd: Instant,
) {
    companion object {
        fun fromObservations(
            volumeObservations: List<VolumeResponseObservation>,
            effectivenessObservations: List<LiftEffectivenessObservation>,
            volumeSnapshots: List<MuscleVolumeHighlight> = emptyList(),
            epochsAnalyzed: Int,
            analysisWindowStart: Instant,
            analysisWindowEnd: Instant,
        ): ResponseProfilePayload =
            ResponseProfilePayload(
                volumeResponses = filterVolumeInfluenceWithMerge(volumeObservations),
                liftEffectiveness = filterLiftEffectivenessInfluenceWithMerge(effectivenessObservations),
                volumeSnapshots = volumeSnapshots,
                informational =
                    ResponseProfileInformational(
                        volumeResponses = filterInformationalVolume(volumeObservations),
                        liftEffectiveness = filterInformationalEffectiveness(effectivenessObservations),
                    ),
                epochsAnalyzed = epochsAnalyzed,
                analysisWindowStart = analysisWindowStart,
                analysisWindowEnd = analysisWindowEnd,
            )
    }
}

fun VolumeResponseObservation.toInfluenceOrNull(): VolumeResponseInfluence? =
    when (evidence) {
        EvidenceLevel.Proven ->
            VolumeResponseInfluence(
                muscle = muscle,
                anchorLiftId = anchorLiftId,
                correlationSlope = correlationSlope,
                confidenceScore = confidenceScore,
                evidence = evidence,
            )
        EvidenceLevel.Hypothesis ->
            if (confidenceScore >= HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN) {
                VolumeResponseInfluence(
                    muscle = muscle,
                    anchorLiftId = anchorLiftId,
                    correlationSlope = correlationSlope,
                    confidenceScore = confidenceScore,
                    evidence = evidence,
                )
            } else {
                null
            }
        EvidenceLevel.InsufficientData -> null
    }

fun LiftEffectivenessObservation.toInfluenceOrNull(): LiftEffectivenessInfluence? =
    when (evidence) {
        EvidenceLevel.Proven ->
            LiftEffectivenessInfluence(
                pattern = pattern,
                liftId = liftId,
                effectivenessScore = effectivenessScore,
                rank = rank,
                evidence = evidence,
            )
        EvidenceLevel.Hypothesis ->
            if (confidenceScore >= HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN) {
                LiftEffectivenessInfluence(
                    pattern = pattern,
                    liftId = liftId,
                    effectivenessScore = effectivenessScore,
                    rank = rank,
                    evidence = evidence,
                )
            } else {
                null
            }
        EvidenceLevel.InsufficientData -> null
    }

fun filterVolumeInfluence(observations: List<VolumeResponseObservation>): List<VolumeResponseInfluence> =
    observations.mapNotNull { it.toInfluenceOrNull() }

fun filterVolumeInfluenceWithMerge(observations: List<VolumeResponseObservation>): List<VolumeResponseInfluence> =
    observations.groupBy { it.muscle }.flatMap { (_, muscleObservations) ->
        when (ResponseEvidenceMerger.mergeVolumeEvidence(muscleObservations)) {
            EvidenceLevel.Proven -> {
                val representative = muscleObservations.maxBy { it.confidenceScore }
                listOfNotNull(representative.copy(evidence = EvidenceLevel.Proven).toInfluenceOrNull())
            }
            else -> muscleObservations.mapNotNull { it.toInfluenceOrNull() }
        }
    }

fun filterLiftEffectivenessInfluence(
    observations: List<LiftEffectivenessObservation>,
): List<LiftEffectivenessInfluence> = observations.mapNotNull { it.toInfluenceOrNull() }

fun filterLiftEffectivenessInfluenceWithMerge(
    observations: List<LiftEffectivenessObservation>,
): List<LiftEffectivenessInfluence> =
    observations.groupBy { it.pattern }.flatMap { (_, patternObservations) ->
        val rankOnes = patternObservations.filter { it.rank == 1 }
        when (ResponseEvidenceMerger.mergeLiftEffectivenessEvidence(rankOnes)) {
            EvidenceLevel.Proven -> {
                val representative = rankOnes.maxBy { it.confidenceScore }
                listOfNotNull(representative.copy(evidence = EvidenceLevel.Proven).toInfluenceOrNull())
            }
            else -> patternObservations.mapNotNull { it.toInfluenceOrNull() }
        }
    }

fun filterInformationalVolume(
    observations: List<VolumeResponseObservation>,
): List<VolumeResponseObservation> =
    observations.filter { observation ->
        observation.evidence == EvidenceLevel.Hypothesis &&
            observation.confidenceScore < HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN
    }

fun filterInformationalEffectiveness(
    observations: List<LiftEffectivenessObservation>,
): List<LiftEffectivenessObservation> =
    observations.filter { observation ->
        observation.evidence == EvidenceLevel.Hypothesis &&
            observation.confidenceScore < HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN
    }

fun shouldIncludeResponseProfile(payload: ResponseProfilePayload): Boolean =
    payload.epochsAnalyzed > 0 ||
        payload.volumeResponses.isNotEmpty() ||
        payload.liftEffectiveness.isNotEmpty() ||
        payload.volumeSnapshots.isNotEmpty() ||
        payload.informational.volumeResponses.isNotEmpty() ||
        payload.informational.liftEffectiveness.isNotEmpty()
