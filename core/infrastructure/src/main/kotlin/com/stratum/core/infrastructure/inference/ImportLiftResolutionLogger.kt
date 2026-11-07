package com.stratum.core.infrastructure.inference

import com.stratum.domain.importing.NearMissExerciseSample
import com.stratum.domain.importing.ResolveImportLiftsDiagnostic
import com.stratum.domain.importing.ResolveImportLiftsSummary
import com.stratum.domain.importing.UnmatchedExerciseSample
import com.stratum.core.infrastructure.logging.LoggingPort
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
                append("preCustomCatalogSetMatches=${summary.preCustomCatalogSetMatches} ")
                append("distinctPreCustomCatalogEnabled=${summary.distinctPreCustomCatalogLiftsEnabled} ")
                append("distinctNamesMatchedPreCustom=${summary.distinctExerciseNamesMatchedPreCustom} ")
                append("unmatchedNames=${summary.distinctUnmatchedExerciseNames} ")
                append("customCreated=${summary.customLiftsCreated} ")
                append("postCustomMatches=${summary.postCustomAdditionalMatches} ")
                append("enabledSeed=${summary.finalEnabledSeedLiftCount} ")
                append("enabledCustom=${summary.finalEnabledCustomLiftCount} ")
                append("enabledTotal=${summary.finalEnabledTotal} ")
                append("unresolvedSets=${summary.unresolvedSetCount}")
                if (summary.customLiftInferenceBatchesAttempted > 0) {
                    append(" inferenceBatches=${summary.customLiftInferenceBatchesAttempted}")
                    append(" inferenceFailedBatches=${summary.customLiftInferenceBatchesFailed}")
                    append(" llmInferred=${summary.customLiftLlmInferredCount}")
                    append(" skipped=${summary.customLiftsSkipped}")
                    summary.customLiftInferenceLastError?.let { append(" inferenceError=\"$it\"") }
                }
            },
        )

        // §10 diagnostics carried on the summary are recorded here, exactly once each,
        // with the real cause attached (this logger is called once per import, from
        // RunFullHistoricalImportUseCase, and is the only drain of the summary).
        // These per-failure records supersede the former aggregate warnings for the
        // custom-lift and embedding failures (STR-240 chunk 8): those warnings were
        // message-only records of the same failures, so keeping them would record
        // each failure twice. The counts (batches attempted/failed, LLM-inferred,
        // skipped, last error, embedding failed) remain on the info summary above.
        summary.diagnostics.forEach { diagnostic ->
            when (diagnostic) {
                is ResolveImportLiftsDiagnostic.EquipmentInferenceFailed ->
                    loggingPort.error(
                        TAG,
                        "Import equipment inference failed — Tier B equipment enablement " +
                            "may be missing. Error: ${diagnostic.cause.message ?: "unknown"}",
                        diagnostic.cause,
                    )
                is ResolveImportLiftsDiagnostic.CustomLiftInferenceFailed ->
                    loggingPort.error(
                        TAG,
                        "Import custom lift inference failed — names in that batch have " +
                            "no inferred metadata. Error: ${diagnostic.cause.message ?: "unknown"}",
                        diagnostic.cause,
                    )
                is ResolveImportLiftsDiagnostic.LiftNameEmbeddingFailed ->
                    loggingPort.error(
                        TAG,
                        "Import lift name embedding failed — gray-zone matches may be " +
                            "unresolved. Error: ${diagnostic.cause.message ?: "unknown"}",
                        diagnostic.cause,
                    )
            }
        }

        if (summary.seedCatalogLiftCount < MIN_EXPECTED_SEED_CATALOG_LIFTS) {
            loggingPort.warning(
                TAG,
                "Seed catalog looks incomplete (${summary.seedCatalogLiftCount} lifts). " +
                    "BM25 matching may fail until bundled catalog is seeded.",
            )
        }

        if (summary.finalEnabledTotal <= summary.finalEnabledCustomLiftCount &&
            summary.distinctPreCustomCatalogLiftsEnabled == 0
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
