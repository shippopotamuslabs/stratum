package com.stratum.core.inference

import com.stratum.domain.importing.NearMissExerciseSample
import com.stratum.domain.importing.ResolveImportLiftsSummary
import com.stratum.domain.importing.UnmatchedExerciseSample
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportLiftResolutionLogger @Inject constructor(
    private val loggingPort: LoggingPort,
) {
    fun log(summary: ResolveImportLiftsSummary) {
        loggingPort.info(
            TAG,
            buildString {
                append("liftResolution ")
                append("inputSets=${summary.inputSetCount} ")
                append("distinctNames=${summary.distinctInputExerciseNames} ")
                append("distinctNamesNormalized=${summary.distinctInputExerciseNamesNormalized} ")
                append("seedCatalog=${summary.seedCatalogLiftCount} ")
                append("preexistingCustom=${summary.preexistingCustomLiftCount} ")
                append("firstPassCatalogSetMatches=${summary.firstPassCatalogMatches} ")
                append("distinctCatalogEnabledFirstPass=${summary.distinctCatalogLiftsEnabledInFirstPass} ")
                append("distinctNamesMatchedCatalog=${summary.distinctExerciseNamesMatchedCatalogInFirstPass} ")
                append("unmatchedNames=${summary.distinctUnmatchedExerciseNames} ")
                append("customCreated=${summary.customLiftsCreated} ")
                append("secondPassMatches=${summary.secondPassAdditionalMatches} ")
                append("enabledSeed=${summary.finalEnabledSeedLiftCount} ")
                append("enabledCustom=${summary.finalEnabledCustomLiftCount} ")
                append("enabledTotal=${summary.finalEnabledTotal} ")
                append("unresolvedSets=${summary.unresolvedSetCount}")
                if (summary.customLiftInferenceBatchesAttempted > 0) {
                    append(" inferenceBatches=${summary.customLiftInferenceBatchesAttempted}")
                    append(" inferenceFailedBatches=${summary.customLiftInferenceBatchesFailed}")
                    append(" llmInferred=${summary.customLiftLlmInferredCount}")
                    append(" stubFallback=${summary.customLiftsCreated - summary.customLiftLlmInferredCount}")
                    summary.customLiftInferenceLastError?.let { append(" inferenceError=\"$it\"") }
                }
            },
        )

        if (summary.customLiftInferenceBatchesFailed > 0) {
            loggingPort.warning(
                TAG,
                "Custom lift inference failed for ${summary.customLiftInferenceBatchesFailed}/" +
                    "${summary.customLiftInferenceBatchesAttempted} batches " +
                    "(${summary.customLiftLlmInferredCount} LLM, " +
                    "${summary.customLiftsCreated - summary.customLiftLlmInferredCount} stub). " +
                    "Last error: ${summary.customLiftInferenceLastError ?: "unknown"}",
            )
        }

        if (summary.seedCatalogLiftCount < MIN_EXPECTED_SEED_CATALOG_LIFTS) {
            loggingPort.warning(
                TAG,
                "Seed catalog looks incomplete (${summary.seedCatalogLiftCount} lifts). " +
                    "BM25 matching may fail until bundled catalog is seeded.",
            )
        }

        if (summary.finalEnabledTotal <= summary.finalEnabledCustomLiftCount &&
            summary.distinctCatalogLiftsEnabledInFirstPass == 0
        ) {
            loggingPort.warning(
                TAG,
                "No catalog lifts matched — only custom lifts are enabled. " +
                    "Check unmatched/nearMiss samples below.",
            )
        }

        summary.unmatchedExerciseSamples.forEach(::logUnmatched)
        summary.nearMissSamples.forEach(::logNearMiss)
    }

    private fun logUnmatched(sample: UnmatchedExerciseSample) {
        loggingPort.debug(
            TAG,
            "unmatched name=\"${sample.exerciseNameRaw}\" sets=${sample.loggedSetCount} " +
                "bestCatalog=\"${sample.bestCatalogName ?: "none"}\" " +
                "bestScore=${formatScore(sample.bestScore)}",
        )
    }

    private fun logNearMiss(sample: NearMissExerciseSample) {
        loggingPort.debug(
            TAG,
            "nearMiss name=\"${sample.exerciseNameRaw}\" sets=${sample.loggedSetCount} " +
                "bestCatalog=\"${sample.bestCatalogName}\" " +
                "bestScore=${formatScore(sample.bestScore)} " +
                "threshold=$BM25_MATCH_THRESHOLD_LOG_VALUE",
        )
    }

    private fun formatScore(score: Double): String = "%.3f".format(score)

    private companion object {
        const val TAG = "StratumImportLifts"
        const val BM25_MATCH_THRESHOLD_LOG_VALUE = "0.600"
        const val MIN_EXPECTED_SEED_CATALOG_LIFTS = 50
    }
}
