package com.stratum.domain.response

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

        val muscles = input.lifts.flatMap { it.primaryMuscles }.toSet()
        val volumeResponses =
            muscles.flatMap { muscle ->
                val anchor = AnchorLiftSelector.select(muscle, epochs, input) ?: return@flatMap emptyList()
                if (input.provenLiftIds.isNotEmpty() && anchor in input.provenLiftIds) {
                    return@flatMap emptyList()
                }
                epochs.mapNotNull { epoch ->
                    VolumeResponseAnalyzer.analyze(
                        muscle = muscle,
                        anchorLiftId = anchor,
                        epoch = epoch,
                        input = input,
                        deloadWeeks = deloadWeeksByEpoch.getValue(epoch),
                    )
                }
            }

        val patterns = input.lifts.map { it.movementPattern }.distinct()
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
            volumeResponses = volumeResponses,
            liftEffectiveness = liftEffectiveness,
            deloadWeeksExcluded = deloadWeeksExcluded,
        )
    }
}
