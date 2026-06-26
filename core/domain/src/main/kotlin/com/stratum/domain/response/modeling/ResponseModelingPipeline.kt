@file:Suppress("MagicNumber")

package com.stratum.domain.response.modeling

import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.CleanTrainingSet
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.ResponseExtractionInput
import com.stratum.domain.response.ResponseRegressionSupport
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.identity.EpochId
import java.time.Instant
import java.time.temporal.ChronoUnit

object ResponseModelingPipeline {
    fun run(
        input: ResponseExtractionInput,
        epochs: List<Epoch>,
        cleanSets: List<CleanTrainingSet>,
        deloadWeeks: Set<Instant>,
    ): ResponseModelingOutput {
        val analysisEpoch = fullWindowEpoch(input)
        val weeklyStimulus =
            MuscleWeeklyStimulusBuilder.build(
                epoch = analysisEpoch,
                cleanSets = cleanSets,
                lifts = input.lifts,
                deloadWeeks = deloadWeeks,
            )
        val liftPerformance =
            LiftPerformancePanelBuilder.build(
                epoch = analysisEpoch,
                cleanSets = cleanSets,
            )
        val muscles = input.lifts.flatMap { it.primaryMuscles + it.secondaryMuscles }.toSet()
        val landmarkEstimates = mutableListOf<MuscleVolumeLandmarkEstimate>()
        val diagnostics = mutableListOf<MuscleResponseModelDiagnostic>()
        val dosePoints = mutableListOf<MuscleDoseResponsePoint>()
        val outcomeIndices = mutableListOf<MuscleWeeklyOutcomeIndex>()

        muscles.forEach { muscle ->
            val muscleStimulus = weeklyStimulus.filter { it.muscle == muscle }
            val indices = MuscleOutcomeIndexBuilder.build(muscle, liftPerformance, input.lifts)
            outcomeIndices += indices
            val pairing = LaggedDoseResponsePairer.pair(muscle, muscleStimulus, indices)
            val anchor = MuscleDoseResponseModel.diagnosticAnchor(muscle, epochs, input)
            val result =
                MuscleDoseResponseModel.estimate(
                    muscle = muscle,
                    pairing = pairing,
                    indices = indices,
                    stimulus = muscleStimulus,
                    diagnosticAnchorLiftId = anchor,
                )
            landmarkEstimates += result.landmarks
            diagnostics += result.diagnostics
            dosePoints += result.dosePoints
        }

        return ResponseModelingOutput(
            weeklyStimulus = weeklyStimulus,
            liftPerformance = liftPerformance,
            outcomeIndices = outcomeIndices,
            landmarkEstimates = landmarkEstimates,
            diagnostics = diagnostics,
            dosePoints = dosePoints,
        )
    }

    fun projectVolumeResponses(
        modeling: ResponseModelingOutput,
        epochs: List<Epoch>,
    ): List<VolumeResponseObservation> {
        val epoch = epochs.maxByOrNull { ChronoUnit.DAYS.between(it.start, it.end) } ?: return emptyList()
        return modeling.landmarkEstimates.mapNotNull { landmark ->
            val muscle = landmark.muscle
            val points = modeling.dosePoints.filter { it.muscle == muscle }
            if (points.size < 4) return@mapNotNull null
            val anchor = landmark.diagnosticAnchorLiftId ?: return@mapNotNull null
            val regression = ResponseRegressionSupport.linearRegression(points.map { it.dose }, points.map { it.response })
                ?: return@mapNotNull null
            val confidenceScore =
                (regression.rSquared * 0.7) +
                    (points.size.toDouble() / epochWeekCount(epoch).toDouble() * 0.3)
            val evidence =
                if (confidenceScore < HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN) {
                    EvidenceLevel.InsufficientData
                } else {
                    EvidenceLevel.Hypothesis
                }
            VolumeResponseObservation(
                muscle = muscle,
                epochId = epoch.id,
                anchorLiftId = anchor,
                stimulus =
                    StimulusDescriptor(
                        epochId = epoch.id,
                        experimentId = null,
                        muscle = muscle,
                        pattern = null,
                        liftId = anchor,
                        weeklyHardSets = points.map { it.dose }.average(),
                        sessionsPerWeek = points.size.toDouble() / epochWeekCount(epoch).toDouble(),
                        intensityProxy = null,
                    ),
                outcome =
                    OutcomeDescriptor(
                        anchorLiftId = anchor,
                        metric = OutcomeMetric.ProgressSlope,
                        value = points.map { it.response }.average(),
                        windowWeeks = epochWeekCount(epoch),
                        reliability =
                            RegressionReliability(
                                rSquared = regression.rSquared,
                                outlierCount = 0,
                                completenessScore = points.size.toDouble() / epochWeekCount(epoch).toDouble(),
                            ),
                    ),
                correlationSlope = regression.slope,
                confidenceScore = confidenceScore,
                evidence = evidence,
                weeklyPointsUsed = points.size,
                epochWeeksUsed = epochWeekCount(epoch),
                observedWeeklySetRange =
                    WeeklySetRange(
                        min = points.minOf { it.dose },
                        max = points.maxOf { it.dose },
                    ),
                bestObservedWeeklySetRange = null,
            )
        }
    }

    private fun fullWindowEpoch(input: ResponseExtractionInput): Epoch =
        Epoch(
            id = EpochId("response-modeling"),
            start = input.analysisWindowStart,
            end = input.analysisWindowEnd,
            anchorLiftIds = emptySet(),
            splitReason = com.stratum.domain.response.EpochSplitReason.WindowEnd,
        )

    private fun epochWeekCount(epoch: Epoch): Int {
        val startWeek = com.stratum.domain.response.WeeklySignalSupport.weekStartFor(epoch.start)
        val endWeek = com.stratum.domain.response.WeeklySignalSupport.weekStartFor(epoch.end)
        val days = ChronoUnit.DAYS.between(startWeek, endWeek)
        return (days / 7 + 1).toInt().coerceAtLeast(1)
    }
}
