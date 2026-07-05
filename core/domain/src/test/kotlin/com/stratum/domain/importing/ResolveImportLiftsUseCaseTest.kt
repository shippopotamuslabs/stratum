package com.stratum.domain.importing

import com.stratum.core.testing.importing.FakeCustomLiftInferencePort
import com.stratum.core.testing.importing.FakeImportEquipmentInferencePort
import com.stratum.core.testing.importing.FakeLiftEmbeddingCatalog
import com.stratum.core.testing.importing.FakeLiftEquipmentCatalog
import com.stratum.core.testing.importing.FakeLiftNameEmbeddingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.importing.ImportHistoryTrainingMapper
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResolveImportLiftsUseCaseTest {
    @Test
    @DisplayName("Given unmatched import names when inference succeeds then custom lifts use inferred metadata")
    fun createsInferredCustomLiftsForUnmatchedNames() = runTest {
        val liftRepo = FakeLiftRepository(initialLifts = minimalSeedCatalog())
        val inference =
            FakeCustomLiftInferencePort(
                results =
                    listOf(
                        InferredCustomLiftMetadata(
                            exerciseNameRaw = "Katana Triceps Extension",
                            movementPattern = MovementPattern.ElbowExtension,
                            primaryMuscles = setOf(MuscleGroup.Tricep),
                            secondaryMuscles = emptySet(),
                            equipment = "cable",
                        ),
                    ),
            )
        val subject = buildSubject(liftRepo, customInference = inference)

        val outcome =
            subject(
                listOf(
                    loggedSet(exerciseNameRaw = "Katana Triceps Extension"),
                    loggedSet(exerciseNameRaw = "Katana Triceps Extension"),
                ),
            )

        assertEquals(2, outcome.resolvedSets.size)
        assertTrue(outcome.resolvedSets.all { it.liftId != null })
        assertEquals(1, outcome.summary.customLiftsCreated)
        assertEquals(0, outcome.summary.distinctCatalogLiftsEnabledInFirstPass)
        val custom = liftRepo.currentLifts.single { it.isCustom }
        assertEquals("Katana Triceps Extension", custom.name)
        assertEquals(MovementPattern.ElbowExtension, custom.movementPatterns.first())
        assertEquals(setOf(MuscleGroup.Tricep), custom.primaryMuscles)
        assertEquals("cable", custom.equipment)
        assertEquals(1, inference.invocations.single().size)
    }

    @Test
    @DisplayName("Given inferred horizontal pull when creating custom lift then bicep secondary is added")
    fun inferredHorizontalPullAddsBicepSecondary() = runTest {
        val liftRepo = FakeLiftRepository(initialLifts = minimalSeedCatalog())
        val inference =
            FakeCustomLiftInferencePort(
                results =
                    listOf(
                        InferredCustomLiftMetadata(
                            exerciseNameRaw = "Neutral Wide Grip Cable Row",
                            movementPattern = MovementPattern.HorizontalPull,
                            primaryMuscles = setOf(MuscleGroup.Back),
                            secondaryMuscles = emptySet(),
                            equipment = "cable",
                        ),
                    ),
            )
        val subject = buildSubject(liftRepo, customInference = inference)

        subject(listOf(loggedSet(exerciseNameRaw = "Neutral Wide Grip Cable Row")))

        val custom = liftRepo.currentLifts.single { it.isCustom }
        assertEquals(MovementPattern.HorizontalPull, custom.movementPatterns.first())
        assertEquals(setOf(MuscleGroup.Bicep), custom.secondaryMuscles)
    }

    @Test
    @DisplayName("Given inference failure when names are unmatched then stub custom lifts are created")
    fun fallsBackToStubCustomLiftsWhenInferenceFails() = runTest {
        val liftRepo = FakeLiftRepository(initialLifts = minimalSeedCatalog())
        val subject = buildSubject(liftRepo, customInference = FakeCustomLiftInferencePort(fail = true))

        subject(listOf(loggedSet(exerciseNameRaw = "Katana Triceps Extension")))

        val custom = liftRepo.currentLifts.single { it.isCustom }
        assertEquals("Katana Triceps Extension", custom.name)
        assertEquals(MovementPattern.ElbowExtension, custom.movementPatterns.first())
        assertEquals(setOf(MuscleGroup.Tricep), custom.primaryMuscles)
    }

    @Test
    @DisplayName("Given bogus imported liftId when name matches catalog then catalog lift is enabled")
    fun enablesCatalogLiftWhenImportedLiftIdIsUnknown() = runTest {
        val squatId = LiftId("back-squat")
        val liftRepo =
            FakeLiftRepository(
                initialLifts = minimalSeedCatalog(catalogLift(id = squatId, name = "Back Squat")),
            )
        val subject = buildSubject(liftRepo)

        val outcome =
            subject(
                listOf(
                    loggedSet(exerciseNameRaw = "Back Squat")
                        .copy(liftId = LiftId("bogus-export-id")),
                ),
            )

        assertEquals(squatId, outcome.resolvedSets.single().liftId)
        assertEquals(1, outcome.summary.distinctCatalogLiftsEnabledInFirstPass)
        assertEquals(1, outcome.summary.finalEnabledSeedLiftCount)
        assertTrue(liftRepo.currentLifts.single { it.id == squatId }.enabled)
        assertTrue(liftRepo.currentLifts.none { it.isCustom })
    }

    @Test
    @DisplayName("Given resolution completes then summary reflects enabled lift counts")
    fun emitsDiagnosticsSummary() = runTest {
        val squatId = LiftId("back-squat")
        val liftRepo =
            FakeLiftRepository(
                initialLifts = minimalSeedCatalog(catalogLift(id = squatId, name = "Back Squat")),
            )
        val subject = buildSubject(liftRepo)

        val outcome =
            subject(
                listOf(
                    loggedSet(exerciseNameRaw = "Back Squat"),
                    loggedSet(exerciseNameRaw = "Unknown Movement XYZ"),
                ),
            )

        assertEquals(2, outcome.summary.inputSetCount)
        assertEquals(1, outcome.summary.firstPassCatalogMatches)
        assertEquals(1, outcome.summary.customLiftsCreated)
        assertEquals(2, outcome.summary.finalEnabledTotal)
        assertEquals(1, outcome.summary.unmatchedExerciseSamples.size)
        assertTrue(outcome.summary.unmatchedExerciseSamples.first().bestScore < Bm25LiftNameResolver.MATCH_THRESHOLD)
    }

    @Test
    @DisplayName("Given superset suffix variants when names are unmatched then one custom lift is created")
    fun deduplicatesCustomLiftsByNormalizedName() = runTest {
        val liftRepo = FakeLiftRepository(initialLifts = minimalSeedCatalog())
        val subject = buildSubject(liftRepo, customInference = FakeCustomLiftInferencePort())

        val outcome =
            subject(
                listOf(
                    loggedSet(exerciseNameRaw = "Katana Triceps Extension ∈ SS1"),
                    loggedSet(exerciseNameRaw = "Katana Triceps Extension ∈ SS2"),
                ),
            )

        assertEquals(1, outcome.summary.customLiftsCreated)
        assertEquals(1, outcome.summary.distinctUnmatchedExerciseNames)
        assertEquals("Katana Triceps Extension", liftRepo.currentLifts.single { it.isCustom }.name)
    }

    @Test
    fun prefersSeedCatalogOverCustomNameCollision() = runTest {
        val squatId = LiftId("back-squat")
        val customSquatId = LiftId("custom-squat")
        val liftRepo =
            FakeLiftRepository(
                initialLifts =
                    minimalSeedCatalog(catalogLift(id = squatId, name = "Back Squat")) +
                        listOf(
                            catalogLift(id = customSquatId, name = "Back Squat").copy(isCustom = true, enabled = true),
                        ),
            )
        val subject = buildSubject(liftRepo)

        val outcome = subject(listOf(loggedSet(exerciseNameRaw = "Back Squat")))

        assertEquals(squatId, outcome.resolvedSets.single().liftId)
        assertTrue(liftRepo.currentLifts.first { it.id == squatId }.enabled)
    }

    @Test
    @DisplayName("Given proven catalog match then provenLiftIds contains it and it is enabled")
    fun tierACollectsProvenLiftIds() = runTest {
        val squatId = LiftId("back-squat")
        val liftRepo =
            FakeLiftRepository(
                initialLifts = minimalSeedCatalog(catalogLift(id = squatId, name = "Back Squat")),
            )
        val subject = buildSubject(liftRepo)

        val outcome = subject(listOf(loggedSet(exerciseNameRaw = "Back Squat")))

        assertTrue(squatId in outcome.provenLiftIds)
        assertTrue(squatId in outcome.summary.provenLiftIds)
    }

    @Test
    @DisplayName("Given confirmed CableStation then Tier B enables un-logged cable catalog lifts without a preset gate")
    fun tierBEquipmentClosureEnablesCompatibleLifts() = runTest {
        val loggedCableId = LiftId("cable-row-logged")
        val unloggedCableId = LiftId("cable-pulldown-unlogged")
        val unloggedLegPressId = LiftId("leg-press-unlogged")
        val catalog = minimalSeedCatalog(
            catalogLift(id = loggedCableId, name = "Seated Cable Row"),
            catalogLift(id = unloggedCableId, name = "Lat Pulldown"),
            catalogLift(id = unloggedLegPressId, name = "Leg Press"),
        )
        val liftRepo = FakeLiftRepository(initialLifts = catalog)
        val equipmentCatalog =
            FakeLiftEquipmentCatalog(
                mapOf(
                    loggedCableId to setOf(GymEquipment.CableStation, GymEquipment.SeatedCableRowStation),
                    unloggedCableId to setOf(GymEquipment.CableStation),
                    unloggedLegPressId to setOf(GymEquipment.LegPress),
                ),
            )
        // The logged name resolves to the cable row; equipment inference also returns CableStation
        // for the user's distinct names. Leg Press needs LegPress (not confirmed) -> not enabled.
        val subject =
            buildSubject(
                liftRepo,
                equipmentCatalog = equipmentCatalog,
                equipmentInference =
                    FakeImportEquipmentInferencePort(
                        mapOf("Seated Cable Row" to setOf(GymEquipment.CableStation)),
                    ),
            )

        val outcome = subject(listOf(loggedSet(exerciseNameRaw = "Seated Cable Row")))

        assertTrue(loggedCableId in outcome.provenLiftIds)
        assertTrue(liftRepo.currentLifts.first { it.id == unloggedCableId }.enabled)
        assertFalse(liftRepo.currentLifts.first { it.id == unloggedLegPressId }.enabled)
        assertEquals(1, outcome.summary.tierBLiftsEnabled)
        assertFalse(unloggedCableId in outcome.provenLiftIds)
    }

    @Test
    @DisplayName("Given sparse import with few closure matches then derived gym preset backfills preset-tagged lifts")
    fun sparseImportFallbackAppliesDerivedPreset() = runTest {
        // Only one logged lift, cable closure yields < MIN_TIER_B_FOR_EXPANSION, so a commercial gym
        // is derived from the cable equipment and preset-tagged lifts get enabled.
        val loggedCableId = LiftId("cable-row-logged")
        val commercialOnlyLift = LiftId("commercial-only-machine")
        val garageOnlyLift = LiftId("garage-only-plate")
        val catalog = minimalSeedCatalog(
            catalogLift(id = loggedCableId, name = "Seated Cable Row")
                .copy(presetTags = setOf(GymPreset.CommercialGym, GymPreset.GarageGym)),
            catalogLift(id = commercialOnlyLift, name = "Hack Squat Machine")
                .copy(presetTags = setOf(GymPreset.CommercialGym)),
            catalogLift(id = garageOnlyLift, name = "Barbell Good Morning")
                .copy(presetTags = setOf(GymPreset.GarageGym)),
        )
        val liftRepo = FakeLiftRepository(initialLifts = catalog)
        val equipmentCatalog =
            FakeLiftEquipmentCatalog(
                mapOf(loggedCableId to setOf(GymEquipment.CableStation)),
            )
        val subject =
            buildSubject(
                liftRepo,
                equipmentCatalog = equipmentCatalog,
                equipmentInference =
                    FakeImportEquipmentInferencePort(
                        mapOf("Seated Cable Row" to setOf(GymEquipment.CableStation)),
                    ),
            )

        val outcome = subject(listOf(loggedSet(exerciseNameRaw = "Seated Cable Row")))

        assertEquals(GymPreset.CommercialGym, outcome.summary.derivedGymFallback)
        assertTrue(liftRepo.currentLifts.first { it.id == commercialOnlyLift }.enabled)
        // Garage-only lift should NOT be enabled by a Commercial backfill.
        assertFalse(liftRepo.currentLifts.first { it.id == garageOnlyLift }.enabled)
    }

    @Test
    @DisplayName("Given import sets when resolving lifts then catalog progress steps are reported in order")
    fun reportsCatalogProgressStepsInOrder() = runTest {
        val squatId = LiftId("back-squat")
        val liftRepo =
            FakeLiftRepository(
                initialLifts = minimalSeedCatalog(catalogLift(id = squatId, name = "Back Squat")),
            )
        val subject = buildSubject(liftRepo)
        val progress = mutableListOf<ImportCatalogProgressStep>()

        subject(
            sets =
                listOf(
                    loggedSet(exerciseNameRaw = "Back Squat"),
                    loggedSet(exerciseNameRaw = "Katana Triceps Extension"),
                ),
            onProgress = { update -> update.catalogStep?.let(progress::add) },
        )

        assertEquals(4, progress.size)
        val matching = progress[0] as ImportCatalogProgressStep.MatchingCatalog
        assertEquals(2, matching.exerciseCount)
        val custom = progress[1] as ImportCatalogProgressStep.IdentifyingCustomLifts
        assertEquals(1, custom.exerciseCount)
        val equipment = progress[2] as ImportCatalogProgressStep.InferringEquipment
        assertEquals(2, equipment.exerciseCount)
        assertEquals(ImportCatalogProgressStep.EnablingAvailableLifts, progress[3])
    }

    @Test
    @DisplayName("Given many sets sharing exercise names when matching then BM25 runs once per distinct name")
    fun resolvesEachDistinctExerciseNameOnce() = runTest {
        val squatId = LiftId("back-squat")
        val liftRepo =
            FakeLiftRepository(
                initialLifts = minimalSeedCatalog(catalogLift(id = squatId, name = "Back Squat")),
            )
        val countingResolver = CountingLiftNameResolver(Bm25LiftNameResolver())
        val subject =
            ResolveImportLiftsUseCase(
                liftRepository = liftRepo,
                liftNameResolver = countingResolver,
                enrichCustomLifts = EnrichCustomLiftsUseCase(FakeCustomLiftInferencePort()),
                equipmentCatalogPort = FakeLiftEquipmentCatalog(),
                equipmentInferencePort = FakeImportEquipmentInferencePort(),
            )

        subject(List(100) { loggedSet(exerciseNameRaw = "Back Squat") })

        assertEquals(1, countingResolver.resolveInvocations)
    }

    @Test
    @DisplayName("Given many sets for one catalog lift when matching then enableLifts runs once for that lift")
    fun enablesMatchedCatalogLiftsInOneBatch() = runTest {
        val squatId = LiftId("back-squat")
        val liftRepo =
            FakeLiftRepository(
                initialLifts = minimalSeedCatalog(catalogLift(id = squatId, name = "Back Squat")),
            )
        val subject = buildSubject(liftRepo)

        subject(List(100) { loggedSet(exerciseNameRaw = "Back Squat") })

        assertEquals(1, liftRepo.enableLiftsInvocations.size)
        assertEquals(setOf(squatId), liftRepo.enableLiftsInvocations.single())
    }

    @Test
    @DisplayName("Given non-recent unmatched exercise when importing then custom lift is created disabled and excluded from provenLiftIds but set is mapped")
    fun nonRecentUnmatchedExerciseCreatedDisabledAndMapped() = runTest {
        val liftRepo = FakeLiftRepository(initialLifts = minimalSeedCatalog())
        val subject = buildSubject(liftRepo)

        val recentSet = loggedSet(exerciseNameRaw = "Recent Bench").copy(
            completedAt = Instant.parse("2025-01-01T12:00:00Z"),
        )
        val nonRecentSet = loggedSet(exerciseNameRaw = "Old Incline Press").copy(
            completedAt = Instant.parse("2024-05-01T12:00:00Z"), // > 183 days ago
        )

        val outcome = subject(listOf(recentSet, nonRecentSet))

        // Find the custom lifts created
        val currentLifts = liftRepo.currentLifts.filter { it.isCustom }
        assertEquals(2, currentLifts.size)

        val recentCustomLift = currentLifts.first { it.name == "Recent Bench" }
        val nonRecentCustomLift = currentLifts.first { it.name == "Old Incline Press" }

        // Recent custom lift should be enabled and in provenLiftIds
        assertTrue(recentCustomLift.enabled)
        assertTrue(recentCustomLift.id in outcome.provenLiftIds)

        // Non-recent custom lift should be disabled and NOT in provenLiftIds
        assertFalse(nonRecentCustomLift.enabled)
        assertFalse(nonRecentCustomLift.id in outcome.provenLiftIds)

        // Both sets should still be resolved to their respective custom lift IDs!
        val resolvedRecentSet = outcome.resolvedSets.first { it.exerciseNameRaw == "Recent Bench" }
        val resolvedNonRecentSet = outcome.resolvedSets.first { it.exerciseNameRaw == "Old Incline Press" }

        assertEquals(recentCustomLift.id, resolvedRecentSet.liftId)
        assertEquals(nonRecentCustomLift.id, resolvedNonRecentSet.liftId)
    }

    @Test
    fun `prepareBatch is called once with distinct exercise names`() = runTest {
        val embeddingPort = FakeLiftNameEmbeddingPort()
        val liftRepo =
            FakeLiftRepository(
                initialLifts = minimalSeedCatalog(catalogLift(id = LiftId("squat"), name = "Back Squat")),
            )
        val subject =
            ResolveImportLiftsUseCase(
                liftRepository = liftRepo,
                liftNameResolver =
                    HybridLiftNameResolver(
                        bm25 = Bm25LiftNameResolver(),
                        catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                        nameEmbedding = embeddingPort,
                    ),
                enrichCustomLifts = EnrichCustomLiftsUseCase(FakeCustomLiftInferencePort()),
                equipmentCatalogPort = FakeLiftEquipmentCatalog(),
                equipmentInferencePort = FakeImportEquipmentInferencePort(),
            )

        subject(
            listOf(
                loggedSet(exerciseNameRaw = "Back Squat"),
                loggedSet(exerciseNameRaw = "Back Squat"),
                loggedSet(exerciseNameRaw = "Front Squat"),
            ),
        )

        assertEquals(listOf("Back Squat", "Front Squat"), embeddingPort.lastRequestedNames)
    }

    private class CountingLiftNameResolver(
        private val delegate: LiftNameResolverPort,
    ) : LiftNameResolverPort {
        var resolveInvocations = 0
            private set

        override fun resolve(exerciseNameRaw: String, catalogNames: Map<LiftId, String>): LiftId? {
            resolveInvocations += 1
            return delegate.resolve(exerciseNameRaw, catalogNames)
        }

        override fun evaluate(
            exerciseNameRaw: String,
            catalogNames: Map<LiftId, String>,
        ): LiftNameMatchEvaluation = delegate.evaluate(exerciseNameRaw, catalogNames)

        override suspend fun prepareBatch(exerciseNames: List<String>) {
            delegate.prepareBatch(exerciseNames)
        }
    }

    private fun buildSubject(
        liftRepo: FakeLiftRepository,
        customInference: FakeCustomLiftInferencePort = FakeCustomLiftInferencePort(),
        equipmentCatalog: FakeLiftEquipmentCatalog = FakeLiftEquipmentCatalog(),
        equipmentInference: FakeImportEquipmentInferencePort = FakeImportEquipmentInferencePort(),
    ): ResolveImportLiftsUseCase {
        val bm25 = Bm25LiftNameResolver()
        return ResolveImportLiftsUseCase(
            liftRepository = liftRepo,
            liftNameResolver =
                HybridLiftNameResolver(
                    bm25 = bm25,
                    catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                    nameEmbedding = FakeLiftNameEmbeddingPort(),
                ),
            enrichCustomLifts = EnrichCustomLiftsUseCase(customInference),
            equipmentCatalogPort = equipmentCatalog,
            equipmentInferencePort = equipmentInference,
        )
    }

    private fun minimalSeedCatalog(vararg lifts: Lift): List<Lift> {
        val required = 50
        val fillerCount = (required - lifts.size).coerceAtLeast(0)
        val filler =
            List(fillerCount) { index ->
                catalogLift(id = LiftId("seed-filler-$index"), name = "Seed Filler $index")
            }
        return filler + lifts.toList()
    }

    private fun catalogLift(id: LiftId, name: String): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = emptySet(),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            allowWeightChangeBetweenSets = false,
            enabled = false,
            isCustom = false,
        )

    private fun loggedSet(exerciseNameRaw: String): LoggedSet =
        ImportHistoryTrainingMapper.parseSet(
            id = LoggedSetId("set-${exerciseNameRaw.hashCode()}"),
            sessionId = TrainingSessionId("session-1"),
            exerciseNameRaw = exerciseNameRaw,
            setIndex = 0,
            weight = Weight(50.0, Weight.WeightUnit.Lb),
            reps = 8,
            rir = null,
            completedAt = Instant.parse("2025-01-01T12:00:00Z"),
        )
}
