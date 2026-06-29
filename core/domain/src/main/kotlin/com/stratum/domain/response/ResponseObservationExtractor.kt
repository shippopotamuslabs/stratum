package com.stratum.domain.response

import com.stratum.domain.response.modeling.ResponseModelingPipeline
import javax.inject.Inject

interface ResponseObservationExtractor {
    fun extract(input: ResponseExtractionInput): ResponseExtractionOutput
}

class DefaultResponseObservationExtractor @Inject constructor() : ResponseObservationExtractor {
    override fun extract(input: ResponseExtractionInput): ResponseExtractionOutput {
        val epochs = EpochSplitter.split(input)
        val cleanSets = TrainingSignalCleaner.clean(input)
        val deloadWeeksByEpoch =
            epochs.associateWith { epoch ->
                EpochSplitter.markDeloadWeeks(epoch, input, cleanSets)
            }
        val deloadWeeksExcluded = deloadWeeksByEpoch.values.flatten().toSet()

        val modeling =
            ResponseModelingPipeline.run(
                input = input,
                epochs = epochs,
                cleanSets = cleanSets,
                deloadWeeks = deloadWeeksExcluded,
            )
        val volumeResponses = ResponseModelingPipeline.projectVolumeResponses(modeling, epochs)

        val patterns = input.lifts.flatMap { it.movementPatterns }.distinct()
        val liftEffectiveness =
            patterns.flatMap { pattern ->
                epochs.flatMap { epoch ->
                    LiftEffectivenessAnalyzer.analyze(
                        pattern = pattern,
                        epoch = epoch,
                        input = input,
                        deloadWeeks = deloadWeeksByEpoch.getValue(epoch),
                    )
                }
            }

        return ResponseExtractionOutput(
            epochs = epochs,
            volumeResponses = volumeResponses.distinctBy { it.muscle to it.epochId },
            liftEffectiveness = liftEffectiveness,
            deloadWeeksExcluded = deloadWeeksExcluded,
            modeling = modeling,
        )
    }
}
