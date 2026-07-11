package com.stratum.domain.importing

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.ExtractResponseProfileUseCase
import com.stratum.domain.response.ResponseProfilePersistencePort
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.di.DefaultDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Which history an insights extraction covers.
 *
 * - [ImportOnly]: original import-orchestration scope — only sessions/sets whose origin is
 *   `IMPORT`. Used by [PersistImportedHistoryUseCase] so a re-import never pulls live work into
 *   the import-derived provenance window.
 * - [All]: every session/set, imported + live. Used by [com.stratum.domain.training.FinalizeTrainingSessionUseCase]
 *   so the dashboard never reflects a frozen import snapshot — a workout logged and completed
 *   after import updates `lift_training_insights` / `muscle_volume_baselines` immediately.
 */
enum class InsightsAnalysisScope {
    ImportOnly,
    All,
}

class ExtractTrainingInsightsUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val supersetAffinityRepository: SupersetAffinityRepository,
    private val muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository,
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val liftRepository: LiftRepository,
) {
    suspend operator fun invoke(
        provenLiftIds: Set<LiftId> = emptySet(),
        scope: InsightsAnalysisScope = InsightsAnalysisScope.ImportOnly,
    ): Result<ExtractTrainingInsightsSummary> = runCatching {
        // A live refresh over All history must not clobber proven-lift provenance recorded at
        // import time — re-read the previously persisted metadata and carry its proven ids forward.
        // ImportOnly callers always pass an explicit provenLiftIds set (from lift resolution).
        val effectiveProvenLiftIds =
            if (scope == InsightsAnalysisScope.All && provenLiftIds.isEmpty()) {
                importAnalysisMetadataRepository.get()?.provenLiftIds?.toSet() ?: emptySet()
            } else {
                provenLiftIds
            }
        val sessions =
            if (scope == InsightsAnalysisScope.All) {
                trainingLogRepository.getAllSessions()
            } else {
                trainingLogRepository.getImportSessions()
            }
        val sets =
            if (scope == InsightsAnalysisScope.All) {
                trainingLogRepository.getAllSets()
            } else {
                trainingLogRepository.getImportSets()
            }
        val lifts = liftRepository.observeLifts().first()
        val output = TrainingInsightsExtractor.analyze(
            TrainingInsightsExtractor.AnalysisInput(sessions, sets, lifts, effectiveProvenLiftIds),
        )
        liftTrainingInsightsRepository.replaceAll(output.liftInsights)
        supersetAffinityRepository.replaceAll(output.supersetAffinities)
        muscleVolumeBaselineRepository.replaceAll(output.volumeBaselines)
        importAnalysisMetadataRepository.save(output.metadata)
        ExtractTrainingInsightsSummary(
            liftsWithInsights = output.liftInsights.size,
            supersetPairs = output.supersetAffinities.size,
            musclesWithBaselines = output.volumeBaselines.size,
        )
    }
}

class ResolveImportLiftsUseCase @Inject constructor(
    private val liftRepository: LiftRepository,
    private val liftNameResolver: LiftNameResolverPort,
    private val enrichCustomLifts: EnrichCustomLiftsUseCase,
    private val equipmentCatalogPort: LiftEquipmentCatalogPort,
    private val equipmentInferencePort: ImportEquipmentInferencePort,
    // BM25/Levenshtein catalog matching is O(distinctNames × catalogSize × nameLen²) CPU work and
    // must not run on Dispatchers.Main — it historically contributed to import ANRs. Defaulted so
    // JVM tests can construct the class without Hilt.
    @DefaultDispatcher private val resolutionDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        sets: List<LoggedSet>,
        onProgress: ImportProgressCallback = {},
    ): ResolveImportLiftsResult {
        val catalog = awaitSeedCatalog()
        val seedLifts = catalog.filter { !it.isCustom }
        val seedCatalogNames = seedLifts.associate { it.id to it.name }
        val seedCatalogIds = seedCatalogNames.keys
        val preexistingCustomLiftCount = catalog.count { it.isCustom }
        val enablementCutoff = ImportWindowPolicy.liftEnablementCutoff(sets)

        fun isRecentForEnablement(set: LoggedSet): Boolean =
            enablementCutoff != null && ImportWindowPolicy.isInLiftEnablementWindow(set, enablementCutoff)

        val distinctInputExerciseNames = sets.map { it.exerciseNameRaw }.toSet().size
        val distinctInputExerciseNamesNormalized =
            sets
                .map { ImportExerciseNameNormalizer.normalizeForMatching(it.exerciseNameRaw) }
                .toSet()
                .size

        val unmatchedCounts = mutableMapOf<String, UnmatchedExerciseAggregate>()
        val firstPassCatalogMatchIds = mutableSetOf<LiftId>()
        val firstPassMatchedExerciseNames = mutableSetOf<String>()
        var firstPassCatalogMatches = 0
        val distinctExerciseNames = sets.map { it.exerciseNameRaw }.distinct()
        // Equipment inference input depends only on `sets`, not on lift resolution — launch it
        // concurrently to hide the Firebase round-trip behind BM25 + LLM enrichment.
        val equipmentDistinctNames =
            ImportWindowPolicy.recentSetsForLiftEnablement(sets)
                .map { it.exerciseNameRaw }
                .distinct()
        onProgress(
            ImportProgress(
                phase = ImportProgressPhase.EnrichingCustomLifts,
                catalogStep =
                    ImportCatalogProgressStep.InferringEquipment(
                        exerciseCount = equipmentDistinctNames.size,
                    ),
            ),
        )
        // Embedding fetch and equipment inference run concurrently with first-pass BM25.
        // High-confidence BM25 matches resolve without embeddings; gray-zone names are caught by
        // the second pass after the embedding job completes.
        return coroutineScope {
            val embeddingJob = async { liftNameResolver.prepareBatch(distinctExerciseNames) }
            val equipmentJob = async {
                equipmentInferencePort.inferEquipment(equipmentDistinctNames).getOrDefault(emptyMap())
            }

            onProgress(
                ImportProgress(
                    phase = ImportProgressPhase.EnrichingCustomLifts,
                    catalogStep =
                        ImportCatalogProgressStep.MatchingCatalog(
                            exerciseCount = sets.map { it.exerciseNameRaw }.distinct().size,
                        ),
                ),
            )
            // BM25 over the full seed catalog per distinct exercise name is the heaviest CPU work in
            // import (O(distinctNames × catalogSize × nameLen²) Levenshtein). Offload to the
            // resolution dispatcher; onProgress stays on the caller's context so that, when this
            // use case is invoked from a Flow producer, emit() doesn't cross dispatcher boundaries.
            val firstPassByExerciseName =
                withContext(resolutionDispatcher) {
                    resolveDistinctExerciseNames(
                        sets = sets,
                        catalogNames = seedCatalogNames,
                        catalogIds = seedCatalogIds,
                    )
                }
            // Ensure embeddings are ready before the second pass needs them for gray-zone rerank.
            embeddingJob.await()

        val catalogLiftsToEnable = mutableSetOf<LiftId>()
        val resolvedSets =
            sets.map { set ->
                val resolved = applyResolvedLiftId(set, seedCatalogIds, firstPassByExerciseName)
                if (resolved != null) {
                    if (isRecentForEnablement(set)) {
                        catalogLiftsToEnable += resolved
                        firstPassCatalogMatches += 1
                        firstPassCatalogMatchIds += resolved
                        firstPassMatchedExerciseNames +=
                            ImportExerciseNameNormalizer.normalizeForMatching(set.exerciseNameRaw)
                    }
                    set.copy(liftId = resolved)
                } else {
                    recordUnmatchedSet(
                        unmatchedCounts = unmatchedCounts,
                        exerciseNameRaw = set.exerciseNameRaw,
                        isRecent = isRecentForEnablement(set),
                    )
                    set.copy(liftId = null)
                }
            }

        var customLiftsCreated = 0
        var customLiftsSkipped = 0
        val customLiftIds = mutableSetOf<LiftId>()
        var secondPassAdditionalMatches = 0
        var customLiftInferenceBatchesAttempted = 0
        var customLiftInferenceBatchesFailed = 0
        var customLiftLlmInferredCount = 0
        var customLiftInferenceLastError: String? = null
        val finalResolvedSets =
            if (unmatchedCounts.isEmpty()) {
                resolvedSets
            } else {
                val candidates =
                    unmatchedCounts.values.map { aggregate ->
                        UnmatchedExerciseCandidate(
                            exerciseNameRaw = aggregate.displayName,
                            loggedSetCount = aggregate.loggedSetCount,
                        )
                    }
                onProgress(
                    ImportProgress(
                        phase = ImportProgressPhase.EnrichingCustomLifts,
                        catalogStep =
                            ImportCatalogProgressStep.IdentifyingCustomLifts(
                                exerciseCount = candidates.size,
                            ),
                    ),
                )
                val enrichment = withContext(resolutionDispatcher) { enrichCustomLifts(candidates) }
                val inferredByName = enrichment.inferredByName
                customLiftInferenceBatchesAttempted = enrichment.batchesAttempted
                customLiftInferenceBatchesFailed = enrichment.batchesFailed
                customLiftInferenceLastError = enrichment.lastFailureMessage
                unmatchedCounts.values.forEach { aggregate ->
                    val inferred = inferredByName[aggregate.displayName]
                    if (inferred != null) {
                        val createdId =
                            liftRepository.addCustomLift(inferred.toCustomLiftDefinition())
                        customLiftLlmInferredCount += 1
                        if (aggregate.isRecent) {
                            customLiftIds += createdId
                        } else {
                            liftRepository.setLiftEnabled(createdId, false)
                        }
                        customLiftsCreated += 1
                    } else {
                        // LLM inference returned no metadata for this name. Skip creating a custom
                        // lift rather than persisting a stub with guessed movement patterns/muscles
                        // — a broken lift in the catalog pollutes program generation. The set stays
                        // unresolved (liftId == null) and may match the catalog on the second pass.
                        customLiftsSkipped += 1
                    }
                }

                val updatedCatalog = liftRepository.observeLifts().first()
                val updatedNames = updatedCatalog.associate { it.id to it.name }
                val updatedIds = updatedNames.keys
                val unmatchedSets = resolvedSets.filter { it.liftId == null }
                val secondPassByExerciseName =
                    withContext(resolutionDispatcher) {
                        resolveDistinctExerciseNames(
                            sets = unmatchedSets,
                            catalogNames = updatedNames,
                            catalogIds = updatedIds,
                        )
                    }
                resolvedSets.map { set ->
                    if (set.liftId != null) {
                        set
                    } else {
                        val resolved = applyResolvedLiftId(set, updatedIds, secondPassByExerciseName)
                        if (resolved != null) {
                            if (isRecentForEnablement(set)) {
                                catalogLiftsToEnable += resolved
                                if (resolved !in firstPassCatalogMatchIds) {
                                    secondPassAdditionalMatches += 1
                                }
                            }
                            set.copy(liftId = resolved)
                        } else {
                            set
                        }
                    }
                }
            }

        liftRepository.enableLifts(catalogLiftsToEnable)

        // Tier A proven set: first-pass catalog matches + custom lifts created from the import.
        val provenLiftIds: Set<LiftId> = firstPassCatalogMatchIds + customLiftIds

        // Equipment inference was launched concurrently with first-pass BM25. Await the result
        // here and union with equipment required by the Tier A catalog matches.
        val inferredEquipment = equipmentJob.await()
        val confirmedEquipment: Set<GymEquipment> = run {
            val fromNames = inferredEquipment.values.flatten().toSet()
            val fromTierA = firstPassCatalogMatchIds.flatMap { equipmentCatalogPort.equipmentFor(it) }.toSet()
            fromNames + fromTierA
        }

        onProgress(
            ImportProgress(
                phase = ImportProgressPhase.EnrichingCustomLifts,
                catalogStep = ImportCatalogProgressStep.EnablingAvailableLifts,
            ),
        )
        // Tier B enablement — equipment closure (sole gate, no preset gate).
        val tierBIds = ImportEquipmentClosure.tierBLiftIds(
            equipmentCatalog = equipmentCatalogPort.all(),
            confirmedEquipment = confirmedEquipment,
            tierAIds = provenLiftIds,
        )
        liftRepository.enableLifts(tierBIds)

        // Sparse-import fallback: if closure enabled too few, derive a gym type from confirmed
        // equipment and backfill with the preset's tagged lifts. Derived gym is discarded after.
        val derivedGymFallback = ImportEquipmentClosure.sparseImportFallbackGym(
            tierBCount = tierBIds.size,
            confirmedEquipment = confirmedEquipment,
        )
        if (derivedGymFallback != null) {
            liftRepository.applyPreset(derivedGymFallback)
        }

        // §5: count enabled lifts from the enabled-only stream instead of reloading the whole
        // catalog — only the enabled seed/custom tallies are needed for the summary.
        val finalEnabledLifts = liftRepository.observeEnabledLifts().first()
        val finalEnabledSeedLiftCount = finalEnabledLifts.count { !it.isCustom }
        val finalEnabledCustomLiftCount = finalEnabledLifts.count { it.isCustom }
        val unresolvedSetCount = finalResolvedSets.count { it.liftId == null }

        // Diagnostic samples: evaluate each unmatched name once and derive both sample lists from
        // the cached evaluations (previously evaluate ran twice per name).
        val (unmatchedSamples, nearMissSamples) =
            withContext(resolutionDispatcher) {
                val cachedEvaluations = mutableMapOf<String, LiftNameMatchEvaluation>()
                fun evaluationFor(displayName: String): LiftNameMatchEvaluation =
                    cachedEvaluations.getOrPut(displayName) {
                        liftNameResolver.evaluate(displayName, seedCatalogNames)
                    }

                val unmatched =
                    unmatchedCounts.values
                        .sortedByDescending { it.loggedSetCount }
                        .take(MAX_DIAGNOSTIC_SAMPLES)
                        .map { aggregate ->
                            val evaluation = evaluationFor(aggregate.displayName)
                            UnmatchedExerciseSample(
                                exerciseNameRaw = aggregate.displayName,
                                loggedSetCount = aggregate.loggedSetCount,
                                bestCatalogName = evaluation.bestCatalogName,
                                bestScore = evaluation.bestScore,
                            )
                        }

                val nearMiss =
                    unmatchedCounts.values
                        .mapNotNull { aggregate ->
                            val evaluation = evaluationFor(aggregate.displayName)
                            val bestName = evaluation.bestCatalogName ?: return@mapNotNull null
                            if (evaluation.bestScore < NEAR_MISS_MIN_SCORE ||
                                evaluation.bestScore >= Bm25LiftNameResolver.MATCH_THRESHOLD
                            ) {
                                return@mapNotNull null
                            }
                            NearMissExerciseSample(
                                exerciseNameRaw = aggregate.displayName,
                                bestCatalogName = bestName,
                                bestScore = evaluation.bestScore,
                                loggedSetCount = aggregate.loggedSetCount,
                            )
                        }
                        .sortedByDescending { it.loggedSetCount }
                        .take(MAX_DIAGNOSTIC_SAMPLES)
                unmatched to nearMiss
            }

        val embeddingBatchError = liftNameResolver.embeddingBatchFailure()
        ResolveImportLiftsResult(
            resolvedSets = finalResolvedSets,
            provenLiftIds = provenLiftIds,
            summary =
                ResolveImportLiftsSummary(
                    inputSetCount = sets.size,
                    distinctInputExerciseNames = distinctInputExerciseNames,
                    distinctInputExerciseNamesNormalized = distinctInputExerciseNamesNormalized,
                    seedCatalogLiftCount = seedLifts.size,
                    preexistingCustomLiftCount = preexistingCustomLiftCount,
                    firstPassCatalogMatches = firstPassCatalogMatches,
                    distinctCatalogLiftsEnabledInFirstPass = firstPassCatalogMatchIds.size,
                    distinctExerciseNamesMatchedCatalogInFirstPass = firstPassMatchedExerciseNames.size,
                    distinctUnmatchedExerciseNames = unmatchedCounts.size,
                    customLiftsCreated = customLiftsCreated,
                    secondPassAdditionalMatches = secondPassAdditionalMatches,
                    finalEnabledSeedLiftCount = finalEnabledSeedLiftCount,
                    finalEnabledCustomLiftCount = finalEnabledCustomLiftCount,
                    finalEnabledTotal = finalEnabledSeedLiftCount + finalEnabledCustomLiftCount,
                    unresolvedSetCount = unresolvedSetCount,
                    unmatchedExerciseSamples = unmatchedSamples,
                    nearMissSamples = nearMissSamples,
                    provenLiftIds = provenLiftIds,
                    confirmedEquipment = confirmedEquipment,
                    tierBLiftsEnabled = tierBIds.size,
                    derivedGymFallback = derivedGymFallback,
                    customLiftInferenceBatchesAttempted = customLiftInferenceBatchesAttempted,
                    customLiftInferenceBatchesFailed = customLiftInferenceBatchesFailed,
                    customLiftLlmInferredCount = customLiftLlmInferredCount,
                    customLiftsSkipped = customLiftsSkipped,
                    customLiftInferenceLastError = customLiftInferenceLastError,
                    embeddingBatchFailed = embeddingBatchError != null,
                    embeddingBatchError = embeddingBatchError,
                ),
        )
        } // coroutineScope
    }

    private suspend fun awaitSeedCatalog(): List<Lift> =
        withTimeout(SEED_CATALOG_WAIT_MS) {
            liftRepository.observeLifts(isCustom = false).first { it.size >= MIN_SEED_CATALOG_SIZE }
        }

    private fun resolveExerciseName(
        exerciseNameRaw: String,
        catalogNames: Map<LiftId, String>,
        catalogIds: Set<LiftId>,
        existingLiftId: LiftId?,
    ): LiftId? {
        existingLiftId?.takeIf { it in catalogIds }?.let { return it }
        return liftNameResolver.resolve(exerciseNameRaw, catalogNames)
    }

    /** BM25 resolution runs once per distinct [LoggedSet.exerciseNameRaw], not once per set row. */
    private fun resolveDistinctExerciseNames(
        sets: List<LoggedSet>,
        catalogNames: Map<LiftId, String>,
        catalogIds: Set<LiftId>,
    ): Map<String, LiftId?> =
        sets
            .groupBy { it.exerciseNameRaw }
            .mapValues { (exerciseNameRaw, setsForName) ->
                val hint = setsForName.firstNotNullOfOrNull { it.liftId }
                resolveExerciseName(
                    exerciseNameRaw = exerciseNameRaw,
                    catalogNames = catalogNames,
                    catalogIds = catalogIds,
                    existingLiftId = hint,
                )
            }

    private fun applyResolvedLiftId(
        set: LoggedSet,
        catalogIds: Set<LiftId>,
        resolvedByExerciseName: Map<String, LiftId?>,
    ): LiftId? {
        set.liftId?.takeIf { it in catalogIds }?.let { return it }
        return resolvedByExerciseName[set.exerciseNameRaw]
    }

    private fun recordUnmatchedSet(
        unmatchedCounts: MutableMap<String, UnmatchedExerciseAggregate>,
        exerciseNameRaw: String,
        isRecent: Boolean,
    ) {
        val normalizedKey = ImportExerciseNameNormalizer.normalizeForMatching(exerciseNameRaw)
        val existing = unmatchedCounts[normalizedKey]
        if (existing == null) {
            unmatchedCounts[normalizedKey] =
                UnmatchedExerciseAggregate(
                    displayName = ImportExerciseNameNormalizer.displayName(exerciseNameRaw),
                    loggedSetCount = 1,
                    isRecent = isRecent,
                )
        } else {
            existing.loggedSetCount += 1
            if (isRecent) {
                existing.isRecent = true
            }
        }
    }

    private data class UnmatchedExerciseAggregate(
        val displayName: String,
        var loggedSetCount: Int,
        var isRecent: Boolean,
    )

    private companion object {
        const val MIN_SEED_CATALOG_SIZE = 50
        const val SEED_CATALOG_WAIT_MS = 60_000L
        const val MAX_DIAGNOSTIC_SAMPLES = 25
        const val NEAR_MISS_MIN_SCORE = 0.3
    }
}

class StartHistoricalImportUseCase @Inject constructor(
    private val importJobRepository: ImportJobRepository,
) {
    suspend operator fun invoke(): Result<ImportJob> = runCatching {
        importJobRepository.getActiveJob()?.let { existing ->
            if (existing.status != ImportJobStatus.Failed &&
                existing.status != ImportJobStatus.Completed
            ) {
                return@runCatching existing
            }
        }
        importJobRepository.createJob()
    }
}

class PersistImportedHistoryUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val resolveImportLifts: ResolveImportLiftsUseCase,
    private val extractTrainingInsights: ExtractTrainingInsightsUseCase,
    private val extractResponseProfile: ExtractResponseProfileUseCase,
    private val responseProfilePersistence: ResponseProfilePersistencePort,
    private val liftRepository: LiftRepository,
    private val loggedLiftContextFactory: com.stratum.domain.training.LoggedLiftContextFactory? = null,
    private val computePersonalRecords: com.stratum.domain.training.ComputePersonalRecordsUseCase,
) {
    suspend operator fun invoke(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
        onProgress: ImportProgressCallback = {},
    ): Result<ExtractTrainingInsightsSummary> = runCatching {
        val (sessions, sanitizedSets) = ImportedHistorySanitizer.sanitize(sessions, sets)
        require(sessions.all { it.origin == TrainingSessionOrigin.IMPORT }) {
            "Import persist requires all sessions to have origin IMPORT."
        }
        require(sanitizedSets.isNotEmpty()) {
            "Import contained no valid training sets."
        }
        val outcome = resolveImportLifts(sanitizedSets, onProgress = onProgress)
        val factory = loggedLiftContextFactory ?: com.stratum.domain.training.DefaultLoggedLiftContextFactory()
        val lifts = liftRepository.observeLifts().first().associateBy { it.id }
        val enrichedSets =
            outcome.resolvedSets.map { set ->
                ImportHistoryTrainingMapper.enrichLiftContext(set, factory, lifts)
            }

        val existingImportSets = trainingLogRepository.getImportSets()
        val existingImportSetIds = existingImportSets.map { it.id.value }.toSet()
        val computation = computePersonalRecords(enrichedSets, excludeSetIds = existingImportSetIds).getOrThrow()

        onProgress(
            ImportProgress(
                phase = ImportProgressPhase.EnrichingCustomLifts,
                catalogStep = ImportCatalogProgressStep.SavingHistory,
            ),
        )
        trainingLogRepository.replaceImportHistory(sessions, computation.updatedIncomingSets)
        if (computation.oldPrSetsToUpdate.isNotEmpty()) {
            trainingLogRepository.updateSets(computation.oldPrSetsToUpdate)
        }
        onProgress(
            ImportProgress(
                phase = ImportProgressPhase.ExtractingInsights,
                catalogStep = ImportCatalogProgressStep.AnalyzingPatterns,
            ),
        )
        val summary =
            try {
                extractTrainingInsights(outcome.provenLiftIds)
                    .getOrThrow()
                    .copy(liftResolution = outcome.summary)
            } catch (error: Throwable) {
                trainingLogRepository.replaceImportHistory(emptyList(), emptyList())
                throw error
            }
        try {
            extractResponseProfile(outcome.provenLiftIds).getOrThrow()
        } catch (error: Throwable) {
            trainingLogRepository.replaceImportHistory(emptyList(), emptyList())
            responseProfilePersistence.clearAll()
            throw error
        }
        liftRepository.setUsedHistoricalImport(true)
        liftRepository.setSelectedPreset(null)
        summary
    }
}

class RollbackHistoricalImportUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val supersetAffinityRepository: SupersetAffinityRepository,
    private val muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository,
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val responseProfilePersistence: ResponseProfilePersistencePort,
    private val liftRepository: LiftRepository,
) {
    suspend operator fun invoke() {
        trainingLogRepository.replaceImportHistory(emptyList(), emptyList())
        liftTrainingInsightsRepository.replaceAll(emptyList())
        supersetAffinityRepository.replaceAll(emptyList())
        muscleVolumeBaselineRepository.replaceAll(emptyList())
        importAnalysisMetadataRepository.clear()
        responseProfilePersistence.clearAll()
        liftRepository.setUsedHistoricalImport(false)
    }
}

/**
 * Import is only complete when analysis metadata exists (written after insights succeed).
 * Orphan history or a stale flag from a partial failed import is cleared here.
 */
class ReconcileHistoricalImportUseCase @Inject constructor(
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val rollbackHistoricalImport: RollbackHistoricalImportUseCase,
) {
    suspend operator fun invoke(): Boolean {
        if (importAnalysisMetadataRepository.get() != null) {
            return true
        }
        rollbackHistoricalImport()
        return false
    }
}
