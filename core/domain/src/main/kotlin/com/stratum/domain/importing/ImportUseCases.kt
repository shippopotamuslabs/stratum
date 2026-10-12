package com.stratum.domain.importing

import com.stratum.domain.lift.GymPreset
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
    // Catalog matching is O(distinctNames × catalogSize) CPU work and must not run on
    // Dispatchers.Main — it historically contributed to import ANRs. Defaulted so JVM tests can
    // construct the class without Hilt.
    @DefaultDispatcher private val resolutionDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        sets: List<LoggedSet>,
        onProgress: ImportProgressCallback = {},
    ): ResolveImportLiftsResult {
        awaitSeedCatalogReady()
        val fullCatalog = liftRepository.observeLifts().first()
        val seedLifts = fullCatalog.filter { !it.isCustom }
        val matchCatalogNames = fullCatalog.associate { it.id to it.name }
        val matchCatalogIds = matchCatalogNames.keys
        val preexistingCustomIds = fullCatalog.filter { it.isCustom }.map { it.id }.toSet()
        val preexistingCustomLiftCount = preexistingCustomIds.size
        val enablementCutoff = ImportWindowPolicy.liftEnablementCutoff(sets)
        val distinctExerciseNames = sets.map { it.exerciseNameRaw }.distinct()
        val liftIdHintByName =
            sets
                .groupBy { it.exerciseNameRaw }
                .mapValues { (_, setsForName) -> setsForName.firstNotNullOfOrNull { it.liftId } }

        fun isRecentForEnablement(set: LoggedSet): Boolean =
            enablementCutoff != null && ImportWindowPolicy.isInLiftEnablementWindow(set, enablementCutoff)

        return coroutineScope {
            onProgress(
                ImportProgress(
                    phase = ImportProgressPhase.EnrichingCustomLifts,
                    catalogStep =
                        ImportCatalogProgressStep.MatchingCatalog(
                            exerciseCount = distinctExerciseNames.size,
                        ),
                ),
            )

            // Stage 1–2: clear singleton embeddings → local match → residual MiniLM → re-resolve.
            val resolutionByExerciseName =
                withContext(resolutionDispatcher) {
                    ResidualLiftNameResolution.resolveNamesLocalThenResidual(
                        resolver = liftNameResolver,
                        distinctNames = distinctExerciseNames,
                        resolveLocal = { name ->
                            resolveExerciseName(
                                exerciseNameRaw = name,
                                catalogNames = matchCatalogNames,
                                catalogIds = matchCatalogIds,
                                existingLiftId = liftIdHintByName[name],
                            )
                        },
                    )
                }

            val applied =
                applyPreCustomMatches(
                    sets = sets,
                    matchCatalogIds = matchCatalogIds,
                    resolutionByExerciseName = resolutionByExerciseName,
                    isRecentForEnablement = ::isRecentForEnablement,
                )

            // Stage 3: equipment LLM for residual unmatched + matched preexisting customs (no
            // asset tags). Seed matches contribute Tier A tags only — happy path stays local.
            val equipmentInferenceNames =
                equipmentInferenceNames(
                    unmatchedCounts = applied.unmatchedCounts,
                    sets = sets,
                    resolutionByExerciseName = resolutionByExerciseName,
                    preexistingCustomIds = preexistingCustomIds,
                    isRecentForEnablement = ::isRecentForEnablement,
                )
            if (equipmentInferenceNames.isNotEmpty()) {
                onProgress(
                    ImportProgress(
                        phase = ImportProgressPhase.EnrichingCustomLifts,
                        catalogStep =
                            ImportCatalogProgressStep.InferringEquipment(
                                exerciseCount = equipmentInferenceNames.size,
                            ),
                    ),
                )
            }
            val equipmentJob =
                async {
                    if (equipmentInferenceNames.isEmpty()) {
                        emptyMap()
                    } else {
                        equipmentInferencePort
                            .inferEquipment(equipmentInferenceNames)
                            .getOrDefault(emptyMap())
                    }
                }

            // Stage 4: custom enrichment + post-custom rematch.
            val afterCustoms =
                createCustomsAndRematch(
                    resolvedSets = applied.resolvedSets,
                    unmatchedCounts = applied.unmatchedCounts,
                    preCustomCatalogMatchIds = applied.preCustomCatalogMatchIds,
                    catalogLiftsToEnable = applied.catalogLiftsToEnable,
                    isRecentForEnablement = ::isRecentForEnablement,
                    onProgress = onProgress,
                )

            liftRepository.enableLifts(afterCustoms.catalogLiftsToEnable)

            val provenLiftIds: Set<LiftId> =
                applied.preCustomCatalogMatchIds + afterCustoms.customLiftIds

            // Stage 5: Tier A/B enablement + sparse fallback.
            val inferredEquipment = equipmentJob.await()
            val confirmedEquipment =
                confirmedEquipment(
                    inferredEquipment = inferredEquipment,
                    preCustomCatalogMatchIds = applied.preCustomCatalogMatchIds,
                )
            val enablement =
                enableTiers(
                    provenLiftIds = provenLiftIds,
                    confirmedEquipment = confirmedEquipment,
                    onProgress = onProgress,
                )

            val finalEnabledLifts = liftRepository.observeEnabledLifts().first()
            val finalEnabledSeedLiftCount = finalEnabledLifts.count { !it.isCustom }
            val finalEnabledCustomLiftCount = finalEnabledLifts.count { it.isCustom }
            val unresolvedSetCount = afterCustoms.finalResolvedSets.count { it.liftId == null }

            // Stage 6: diagnostics against the same full catalog used for matching.
            val (unmatchedSamples, nearMissSamples) =
                withContext(resolutionDispatcher) {
                    buildDiagnosticSamples(
                        unmatchedCounts = applied.unmatchedCounts,
                        catalogNames = matchCatalogNames,
                    )
                }

            val embeddingBatchError = liftNameResolver.embeddingBatchFailure()
            ResolveImportLiftsResult(
                resolvedSets = afterCustoms.finalResolvedSets,
                provenLiftIds = provenLiftIds,
                summary =
                    ResolveImportLiftsSummary(
                        inputSetCount = sets.size,
                        distinctInputExerciseNames = sets.map { it.exerciseNameRaw }.toSet().size,
                        distinctInputExerciseNamesNormalized =
                            sets
                                .map { ImportExerciseNameNormalizer.normalizeForMatching(it.exerciseNameRaw) }
                                .toSet()
                                .size,
                        seedCatalogLiftCount = seedLifts.size,
                        preexistingCustomLiftCount = preexistingCustomLiftCount,
                        preCustomCatalogSetMatches = applied.preCustomCatalogSetMatches,
                        distinctPreCustomCatalogLiftsEnabled = applied.preCustomCatalogMatchIds.size,
                        distinctExerciseNamesMatchedPreCustom =
                            applied.preCustomMatchedExerciseNames.size,
                        distinctUnmatchedExerciseNames = applied.unmatchedCounts.size,
                        customLiftsCreated = afterCustoms.customLiftsCreated,
                        postCustomAdditionalMatches = afterCustoms.postCustomAdditionalMatches,
                        finalEnabledSeedLiftCount = finalEnabledSeedLiftCount,
                        finalEnabledCustomLiftCount = finalEnabledCustomLiftCount,
                        finalEnabledTotal = finalEnabledSeedLiftCount + finalEnabledCustomLiftCount,
                        unresolvedSetCount = unresolvedSetCount,
                        unmatchedExerciseSamples = unmatchedSamples,
                        nearMissSamples = nearMissSamples,
                        provenLiftIds = provenLiftIds,
                        confirmedEquipment = confirmedEquipment,
                        tierBLiftsEnabled = enablement.tierBIds.size,
                        derivedGymFallback = enablement.derivedGymFallback,
                        customLiftInferenceBatchesAttempted =
                            afterCustoms.customLiftInferenceBatchesAttempted,
                        customLiftInferenceBatchesFailed =
                            afterCustoms.customLiftInferenceBatchesFailed,
                        customLiftLlmInferredCount = afterCustoms.customLiftLlmInferredCount,
                        customLiftsSkipped = afterCustoms.customLiftsSkipped,
                        customLiftInferenceLastError = afterCustoms.customLiftInferenceLastError,
                        embeddingBatchFailed = embeddingBatchError != null,
                        embeddingBatchError = embeddingBatchError,
                    ),
            )
        }
    }

    private data class PreCustomApplyResult(
        val resolvedSets: List<LoggedSet>,
        val unmatchedCounts: MutableMap<String, UnmatchedExerciseAggregate>,
        val preCustomCatalogMatchIds: Set<LiftId>,
        val preCustomMatchedExerciseNames: Set<String>,
        val preCustomCatalogSetMatches: Int,
        val catalogLiftsToEnable: MutableSet<LiftId>,
    )

    private data class CustomsRematchResult(
        val finalResolvedSets: List<LoggedSet>,
        val catalogLiftsToEnable: MutableSet<LiftId>,
        val customLiftIds: Set<LiftId>,
        val customLiftsCreated: Int,
        val customLiftsSkipped: Int,
        val postCustomAdditionalMatches: Int,
        val customLiftInferenceBatchesAttempted: Int,
        val customLiftInferenceBatchesFailed: Int,
        val customLiftLlmInferredCount: Int,
        val customLiftInferenceLastError: String?,
    )

    private data class EnablementResult(
        val tierBIds: Set<LiftId>,
        val derivedGymFallback: GymPreset?,
    )

    private fun applyPreCustomMatches(
        sets: List<LoggedSet>,
        matchCatalogIds: Set<LiftId>,
        resolutionByExerciseName: Map<String, LiftId?>,
        isRecentForEnablement: (LoggedSet) -> Boolean,
    ): PreCustomApplyResult {
        val unmatchedCounts = mutableMapOf<String, UnmatchedExerciseAggregate>()
        val preCustomCatalogMatchIds = mutableSetOf<LiftId>()
        val preCustomMatchedExerciseNames = mutableSetOf<String>()
        var preCustomCatalogSetMatches = 0
        val catalogLiftsToEnable = mutableSetOf<LiftId>()
        val resolvedSets =
            sets.map { set ->
                val resolved = applyResolvedLiftId(set, matchCatalogIds, resolutionByExerciseName)
                if (resolved != null) {
                    if (isRecentForEnablement(set)) {
                        catalogLiftsToEnable += resolved
                        preCustomCatalogSetMatches += 1
                        preCustomCatalogMatchIds += resolved
                        preCustomMatchedExerciseNames +=
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
        return PreCustomApplyResult(
            resolvedSets = resolvedSets,
            unmatchedCounts = unmatchedCounts,
            preCustomCatalogMatchIds = preCustomCatalogMatchIds,
            preCustomMatchedExerciseNames = preCustomMatchedExerciseNames,
            preCustomCatalogSetMatches = preCustomCatalogSetMatches,
            catalogLiftsToEnable = catalogLiftsToEnable,
        )
    }

    /**
     * Names sent to equipment LLM: residual unmatched recent names, plus recent names that matched
     * preexisting customs (customs have no seed equipment tags). Pure seed matches rely on Tier A
     * asset tags only.
     */
    private fun equipmentInferenceNames(
        unmatchedCounts: Map<String, UnmatchedExerciseAggregate>,
        sets: List<LoggedSet>,
        resolutionByExerciseName: Map<String, LiftId?>,
        preexistingCustomIds: Set<LiftId>,
        isRecentForEnablement: (LoggedSet) -> Boolean,
    ): List<String> {
        val unmatchedRecent =
            unmatchedCounts.values.filter { it.isRecent }.map { it.displayName }
        val matchedCustomRecent =
            sets
                .filter { isRecentForEnablement(it) }
                .mapNotNull { set ->
                    val liftId = resolutionByExerciseName[set.exerciseNameRaw]
                    if (liftId != null && liftId in preexistingCustomIds) {
                        ImportExerciseNameNormalizer.displayName(set.exerciseNameRaw)
                    } else {
                        null
                    }
                }
        return (unmatchedRecent + matchedCustomRecent).distinct()
    }

    private suspend fun createCustomsAndRematch(
        resolvedSets: List<LoggedSet>,
        unmatchedCounts: Map<String, UnmatchedExerciseAggregate>,
        preCustomCatalogMatchIds: Set<LiftId>,
        catalogLiftsToEnable: MutableSet<LiftId>,
        isRecentForEnablement: (LoggedSet) -> Boolean,
        onProgress: ImportProgressCallback,
    ): CustomsRematchResult {
        if (unmatchedCounts.isEmpty()) {
            return CustomsRematchResult(
                finalResolvedSets = resolvedSets,
                catalogLiftsToEnable = catalogLiftsToEnable,
                customLiftIds = emptySet(),
                customLiftsCreated = 0,
                customLiftsSkipped = 0,
                postCustomAdditionalMatches = 0,
                customLiftInferenceBatchesAttempted = 0,
                customLiftInferenceBatchesFailed = 0,
                customLiftLlmInferredCount = 0,
                customLiftInferenceLastError = null,
            )
        }
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
        var customLiftsCreated = 0
        var customLiftsSkipped = 0
        var customLiftLlmInferredCount = 0
        val customLiftIds = mutableSetOf<LiftId>()
        unmatchedCounts.values.forEach { aggregate ->
            val inferred = enrichment.inferredByName[aggregate.displayName]
            if (inferred != null) {
                val createdId = liftRepository.addCustomLift(inferred.toCustomLiftDefinition())
                if (aggregate.displayName !in enrichment.nameRuleResolvedNames) {
                    customLiftLlmInferredCount += 1
                }
                if (aggregate.isRecent) {
                    customLiftIds += createdId
                } else {
                    liftRepository.setLiftEnabled(createdId, false)
                }
                customLiftsCreated += 1
            } else {
                // No name-rule hit and LLM returned no metadata — skip broken Squat/Quad stubs.
                customLiftsSkipped += 1
            }
        }

        val updatedCatalog = liftRepository.observeLifts().first()
        val updatedNames = updatedCatalog.associate { it.id to it.name }
        val updatedIds = updatedNames.keys
        val unmatchedSets = resolvedSets.filter { it.liftId == null }
        val postCustomByExerciseName =
            withContext(resolutionDispatcher) {
                resolveDistinctExerciseNames(
                    sets = unmatchedSets,
                    catalogNames = updatedNames,
                    catalogIds = updatedIds,
                )
            }
        var postCustomAdditionalMatches = 0
        val finalResolvedSets =
            resolvedSets.map { set ->
                if (set.liftId != null) {
                    set
                } else {
                    val resolved = applyResolvedLiftId(set, updatedIds, postCustomByExerciseName)
                    if (resolved != null) {
                        if (isRecentForEnablement(set)) {
                            catalogLiftsToEnable += resolved
                            if (resolved !in preCustomCatalogMatchIds) {
                                postCustomAdditionalMatches += 1
                            }
                        }
                        set.copy(liftId = resolved)
                    } else {
                        set
                    }
                }
            }
        return CustomsRematchResult(
            finalResolvedSets = finalResolvedSets,
            catalogLiftsToEnable = catalogLiftsToEnable,
            customLiftIds = customLiftIds,
            customLiftsCreated = customLiftsCreated,
            customLiftsSkipped = customLiftsSkipped,
            postCustomAdditionalMatches = postCustomAdditionalMatches,
            customLiftInferenceBatchesAttempted = enrichment.batchesAttempted,
            customLiftInferenceBatchesFailed = enrichment.batchesFailed,
            customLiftLlmInferredCount = customLiftLlmInferredCount,
            customLiftInferenceLastError = enrichment.lastFailureMessage,
        )
    }

    private fun confirmedEquipment(
        inferredEquipment: Map<String, Set<GymEquipment>>,
        preCustomCatalogMatchIds: Set<LiftId>,
    ): Set<GymEquipment> {
        val fromNames = inferredEquipment.values.flatten().toSet()
        val fromTierA =
            preCustomCatalogMatchIds.flatMap { equipmentCatalogPort.equipmentFor(it) }.toSet()
        return fromNames + fromTierA
    }

    private suspend fun enableTiers(
        provenLiftIds: Set<LiftId>,
        confirmedEquipment: Set<GymEquipment>,
        onProgress: ImportProgressCallback,
    ): EnablementResult {
        onProgress(
            ImportProgress(
                phase = ImportProgressPhase.EnrichingCustomLifts,
                catalogStep = ImportCatalogProgressStep.EnablingAvailableLifts,
            ),
        )
        val tierBIds =
            ImportEquipmentClosure.tierBLiftIds(
                equipmentCatalog = equipmentCatalogPort.all(),
                confirmedEquipment = confirmedEquipment,
                tierAIds = provenLiftIds,
            )
        liftRepository.enableLifts(tierBIds)
        val derivedGymFallback =
            ImportEquipmentClosure.sparseImportFallbackGym(
                tierBCount = tierBIds.size,
                confirmedEquipment = confirmedEquipment,
            )
        if (derivedGymFallback != null) {
            liftRepository.applyPreset(derivedGymFallback)
        }
        return EnablementResult(tierBIds = tierBIds, derivedGymFallback = derivedGymFallback)
    }

    private fun buildDiagnosticSamples(
        unmatchedCounts: Map<String, UnmatchedExerciseAggregate>,
        catalogNames: Map<LiftId, String>,
    ): Pair<List<UnmatchedExerciseSample>, List<NearMissExerciseSample>> {
        val cachedEvaluations = mutableMapOf<String, LiftNameMatchEvaluation>()
        fun evaluationFor(displayName: String): LiftNameMatchEvaluation =
            cachedEvaluations.getOrPut(displayName) {
                liftNameResolver.evaluate(displayName, catalogNames)
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
        return unmatched to nearMiss
    }

    private suspend fun awaitSeedCatalogReady() {
        withTimeout(SEED_CATALOG_WAIT_MS) {
            liftRepository.observeLifts(isCustom = false).first { it.size >= MIN_SEED_CATALOG_SIZE }
        }
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

    /** Resolution runs once per distinct [LoggedSet.exerciseNameRaw], not once per set row. */
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
    /**
     * Always allocates a fresh local job. Any non-terminal local job (left behind by cancel/stop
     * or a crashed coroutine) is failed first so we never re-upload under a remote job that is
     * still `pending_agent`/`agent_running` — that path used to surface "Import job is already
     * running" / "Another import is already in progress."
     */
    suspend operator fun invoke(): Result<ImportJob> = runCatching {
        failActiveImportJobs(
            importJobRepository = importJobRepository,
            errorMessage = "Superseded by a new import.",
        )
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
        policy: ImportHistoryPersistPolicy,
        sourceKind: ImportDedupeSourceKind = ImportDedupeSourceKind.ThirdParty,
        /**
         * Post-merge session ids from Stratum chunks (`preserveStableIds`). Those sessions
         * use id-only merge skip; all other sessions use ThirdParty content fingerprint.
         * Empty + [sourceKind] [ImportDedupeSourceKind.StratumStableIds] keeps the pure-Stratum
         * id-only path for callers that do not supply per-session ids.
         */
        stableIdSessionIds: Set<String> = emptySet(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Result<ExtractTrainingInsightsSummary> {
        return when (policy) {
            ImportHistoryPersistPolicy.ReplaceImportOrigin ->
                invokeReplace(sessions, sets, onProgress)
            ImportHistoryPersistPolicy.MergeSkipDuplicates ->
                invokeMerge(sessions, sets, onProgress, sourceKind, stableIdSessionIds, zone)
        }
    }

    private suspend fun invokeMerge(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
        onProgress: ImportProgressCallback,
        sourceKind: ImportDedupeSourceKind,
        stableIdSessionIds: Set<String>,
        zone: java.time.ZoneId,
    ): Result<ExtractTrainingInsightsSummary> = runCatching {
        val (sanitizedSessions, sanitizedSets) = ImportedHistorySanitizer.sanitize(sessions, sets)
        require(sanitizedSessions.all { it.origin == TrainingSessionOrigin.IMPORT }) {
            "Import persist requires all sessions to have origin IMPORT."
        }
        val existingSessions = trainingLogRepository.getAllSessions()
        val existingSets = trainingLogRepository.getAllSets()
        val filtered =
            ImportHistoryDedupe.filterForMerge(
                incomingSessions = sanitizedSessions,
                incomingSets = sanitizedSets,
                existingSessions = existingSessions,
                existingSets = existingSets,
                zone = zone,
                sourceKind = sourceKind,
                stableIdSessionIds = stableIdSessionIds,
            )
        val mergeStats =
            MergeImportStats(
                sessionsInserted = filtered.sessions.size,
                setsInserted = filtered.sets.size,
                sessionsSkipped = filtered.skippedSessionCount,
                setsSkipped = filtered.skippedSetCount,
            )
        if (filtered.sets.isEmpty()) {
            // All-skip path: no append. Still run insights when local history already exists so a
            // misconfigured insights pipeline fails closed without wiping prior IMPORT rows.
            // Fresh-empty device → summary zeros without extract.
            if (existingSessions.isEmpty()) {
                return@runCatching ExtractTrainingInsightsSummary(
                    liftsWithInsights = 0,
                    supersetPairs = 0,
                    musclesWithBaselines = 0,
                    mergeStats = mergeStats,
                )
            }
            onProgress(
                ImportProgress(
                    phase = ImportProgressPhase.ExtractingInsights,
                    catalogStep = ImportCatalogProgressStep.AnalyzingPatterns,
                ),
            )
            return@runCatching extractTrainingInsights()
                .getOrThrow()
                .copy(mergeStats = mergeStats)
        }
        val outcome = resolveImportLifts(filtered.sets, onProgress = onProgress)
        val factory = loggedLiftContextFactory ?: com.stratum.domain.training.DefaultLoggedLiftContextFactory()
        val lifts = liftRepository.observeLifts().first().associateBy { it.id }
        val enrichedSets =
            outcome.resolvedSets.map { set ->
                ImportHistoryTrainingMapper.enrichLiftContext(set, factory, lifts)
            }
        val computation =
            computePersonalRecords(enrichedSets).getOrThrow()

        onProgress(
            ImportProgress(
                phase = ImportProgressPhase.EnrichingCustomLifts,
                catalogStep = ImportCatalogProgressStep.SavingHistory,
            ),
        )
        // Historical PR/e1RM updates (demote and promote). Invert flags on unwind.
        val historicalPrUpdates = computation.oldPrSetsToUpdate
        val restorePrSets =
            historicalPrUpdates.map { it.copy(isPersonalRecord = !it.isPersonalRecord) }
        if (historicalPrUpdates.isNotEmpty()) {
            trainingLogRepository.updateSets(historicalPrUpdates)
        }
        val appendedSessions = filtered.sessions
        val appendedSets = computation.updatedIncomingSets
        trainingLogRepository.appendImportHistory(appendedSessions, appendedSets)
        onProgress(
            ImportProgress(
                phase = ImportProgressPhase.ExtractingInsights,
                catalogStep = ImportCatalogProgressStep.AnalyzingPatterns,
            ),
        )
        // On insights/response failure: remove only rows we just appended and restore demoted PRs.
        val summary =
            try {
                extractTrainingInsights(outcome.provenLiftIds)
                    .getOrThrow()
                    .copy(
                        liftResolution = outcome.summary,
                        mergeStats = mergeStats,
                    )
            } catch (error: Throwable) {
                unwindMergeAppend(appendedSessions, appendedSets, restorePrSets)
                throw error
            }
        try {
            extractResponseProfile(outcome.provenLiftIds).getOrThrow()
        } catch (error: Throwable) {
            unwindMergeAppend(appendedSessions, appendedSets, restorePrSets)
            throw error
        }
        liftRepository.setUsedHistoricalImport(true)
        liftRepository.setSelectedPreset(null)
        summary
    }

    /** Removes rows appended this merge and restores any LIVE PRs demoted for the import. */
    private suspend fun unwindMergeAppend(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
        restorePrSets: List<LoggedSet>,
    ) {
        removeAppendedImportRows(sessions, sets)
        if (restorePrSets.isNotEmpty()) {
            trainingLogRepository.updateSets(restorePrSets)
        }
    }

    private suspend fun removeAppendedImportRows(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
    ) {
        for (set in sets) {
            trainingLogRepository.deleteSet(set.id)
        }
        for (session in sessions) {
            trainingLogRepository.deleteSession(session.id)
        }
    }

    private suspend fun invokeReplace(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
        onProgress: ImportProgressCallback,
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
        // Demote prior live PRs first — unique (liftId, equipmentId, isPersonalRecord) cannot hold two trues per pair.
        if (computation.oldPrSetsToUpdate.isNotEmpty()) {
            trainingLogRepository.updateSets(computation.oldPrSetsToUpdate)
        }
        trainingLogRepository.replaceImportHistory(sessions, computation.updatedIncomingSets)
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
