package com.stratum.domain.importing

import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.ExtractResponseProfileUseCase
import com.stratum.domain.response.ResponseProfilePersistencePort
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

class ExtractTrainingInsightsUseCase @Inject constructor(
    private val trainingHistoryRepository: TrainingHistoryRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val supersetAffinityRepository: SupersetAffinityRepository,
    private val muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository,
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val liftRepository: LiftRepository,
) {
    suspend operator fun invoke(
        provenLiftIds: Set<LiftId> = emptySet(),
    ): Result<ExtractTrainingInsightsSummary> = runCatching {
        val sessions = trainingHistoryRepository.getAllSessions()
        val sets = trainingHistoryRepository.getAllSets()
        val lifts = liftRepository.observeLifts().first()
        val output = TrainingInsightsExtractor.analyze(
            TrainingInsightsExtractor.AnalysisInput(sessions, sets, lifts, provenLiftIds),
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
        onProgress(
            ImportProgress(
                phase = ImportProgressPhase.EnrichingCustomLifts,
                catalogStep =
                    ImportCatalogProgressStep.MatchingCatalog(
                        exerciseCount = sets.map { it.exerciseNameRaw }.distinct().size,
                    ),
            ),
        )
        val distinctExerciseNames = sets.map { it.exerciseNameRaw }.distinct()
        liftNameResolver.prepareBatch(distinctExerciseNames)
        val firstPassByExerciseName =
            resolveDistinctExerciseNames(
                sets = sets,
                catalogNames = seedCatalogNames,
                catalogIds = seedCatalogIds,
            )
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
        val customLiftIds = mutableSetOf<LiftId>()
        var secondPassAdditionalMatches = 0
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
                val inferredByName = enrichCustomLifts(candidates)
                val defaultIncrement = Weight(DEFAULT_CUSTOM_INCREMENT_LB, Weight.WeightUnit.Lb)

                unmatchedCounts.values.forEach { aggregate ->
                    val inferred = inferredByName[aggregate.displayName]
                    val createdId =
                        if (inferred != null) {
                            liftRepository.addCustomLift(inferred.toCustomLiftDefinition(defaultIncrement))
                        } else {
                            liftRepository.addCustomLift(
                                ImportStubLiftGuesser.definitionFromName(
                                    aggregate.displayName,
                                    defaultIncrement,
                                ),
                            )
                        }
                    if (aggregate.isRecent) {
                        customLiftIds += createdId
                    } else {
                        liftRepository.setLiftEnabled(createdId, false)
                    }
                    customLiftsCreated += 1
                }

                val updatedCatalog = liftRepository.observeLifts().first()
                val updatedNames = updatedCatalog.associate { it.id to it.name }
                val updatedIds = updatedNames.keys
                val unmatchedSets = resolvedSets.filter { it.liftId == null }
                val secondPassByExerciseName =
                    resolveDistinctExerciseNames(
                        sets = unmatchedSets,
                        catalogNames = updatedNames,
                        catalogIds = updatedIds,
                    )
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

        // Transient equipment inference: map the user's distinct import names to equipment, then
        // union with equipment required by the Tier A catalog matches (looked up in the asset).
        val confirmedEquipment: Set<GymEquipment> = run {
            val distinctNames =
                ImportWindowPolicy.recentSetsForLiftEnablement(sets)
                    .map { it.exerciseNameRaw }
                    .distinct()
            onProgress(
                ImportProgress(
                    phase = ImportProgressPhase.EnrichingCustomLifts,
                    catalogStep =
                        ImportCatalogProgressStep.InferringEquipment(
                            exerciseCount = distinctNames.size,
                        ),
                ),
            )
            val inferred = equipmentInferencePort.inferEquipment(distinctNames).getOrDefault(emptyMap())
            val fromNames = inferred.values.flatten().toSet()
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

        val finalCatalog = liftRepository.observeLifts().first()
        val finalEnabledSeedLiftCount = finalCatalog.count { !it.isCustom && it.enabled }
        val finalEnabledCustomLiftCount = finalCatalog.count { it.isCustom && it.enabled }
        val unresolvedSetCount = finalResolvedSets.count { it.liftId == null }

        val unmatchedSamples =
            unmatchedCounts.values
                .sortedByDescending { it.loggedSetCount }
                .take(MAX_DIAGNOSTIC_SAMPLES)
                .map { aggregate ->
                    val evaluation = liftNameResolver.evaluate(aggregate.displayName, seedCatalogNames)
                    UnmatchedExerciseSample(
                        exerciseNameRaw = aggregate.displayName,
                        loggedSetCount = aggregate.loggedSetCount,
                        bestCatalogName = evaluation.bestCatalogName,
                        bestScore = evaluation.bestScore,
                    )
                }

        val nearMissSamples =
            unmatchedCounts.values
                .mapNotNull { aggregate ->
                    val evaluation = liftNameResolver.evaluate(aggregate.displayName, seedCatalogNames)
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

        return ResolveImportLiftsResult(
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
                ),
        )
    }

    private suspend fun awaitSeedCatalog(): List<Lift> =
        withTimeout(SEED_CATALOG_WAIT_MS) {
            liftRepository.observeLifts().first { lifts ->
                lifts.count { !it.isCustom } >= MIN_SEED_CATALOG_SIZE
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
        const val DEFAULT_CUSTOM_INCREMENT_LB = 5.0
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
    private val trainingHistoryRepository: TrainingHistoryRepository,
    private val resolveImportLifts: ResolveImportLiftsUseCase,
    private val extractTrainingInsights: ExtractTrainingInsightsUseCase,
    private val extractResponseProfile: ExtractResponseProfileUseCase,
    private val responseProfilePersistence: ResponseProfilePersistencePort,
    private val liftRepository: LiftRepository,
) {
    suspend operator fun invoke(
        sessions: List<HistoricalSession>,
        sets: List<LoggedSet>,
        onProgress: ImportProgressCallback = {},
    ): Result<ExtractTrainingInsightsSummary> = runCatching {
        val (sessions, sanitizedSets) = ImportedHistorySanitizer.sanitize(sessions, sets)
        require(sanitizedSets.isNotEmpty()) {
            "Import contained no valid training sets."
        }
        val outcome = resolveImportLifts(sanitizedSets, onProgress = onProgress)
        onProgress(
            ImportProgress(
                phase = ImportProgressPhase.EnrichingCustomLifts,
                catalogStep = ImportCatalogProgressStep.SavingHistory,
            ),
        )
        trainingHistoryRepository.replaceAll(sessions, outcome.resolvedSets)
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
                trainingHistoryRepository.replaceAll(emptyList(), emptyList())
                throw error
            }
        try {
            extractResponseProfile(outcome.provenLiftIds).getOrThrow()
        } catch (error: Throwable) {
            trainingHistoryRepository.replaceAll(emptyList(), emptyList())
            responseProfilePersistence.clearAll()
            throw error
        }
        liftRepository.setUsedHistoricalImport(true)
        liftRepository.setSelectedPreset(null)
        summary
    }
}

class RollbackHistoricalImportUseCase @Inject constructor(
    private val trainingHistoryRepository: TrainingHistoryRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val supersetAffinityRepository: SupersetAffinityRepository,
    private val muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository,
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val responseProfilePersistence: ResponseProfilePersistencePort,
    private val liftRepository: LiftRepository,
) {
    suspend operator fun invoke() {
        trainingHistoryRepository.replaceAll(emptyList(), emptyList())
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
