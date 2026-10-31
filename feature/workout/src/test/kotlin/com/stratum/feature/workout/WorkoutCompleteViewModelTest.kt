package com.stratum.feature.workout

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository
import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository
import com.stratum.core.testing.importing.FakeSupersetAffinityRepository
import com.stratum.core.testing.onboarding.FakeEquipmentProposalPreferences
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncStatusPort
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.workout.FakeRepRangeShiftProposalPreferences
import com.stratum.application.healthconnect.ClearHealthConnectSyncErrorUseCase
import com.stratum.application.healthconnect.ObserveHealthConnectSyncErrorsUseCase
import com.stratum.application.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.application.lift.AnalyzeSessionEquipmentProposalsUseCase
import com.stratum.application.lift.ApplyEquipmentIncrementProposalUseCase
import com.stratum.application.lift.EquipmentProposalDiagnostic
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.application.training.GetWorkoutSummaryUseCase
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.application.workout.AnalyzeSessionRepRangeShiftProposalsUseCase
import com.stratum.application.workout.ApplyRepRangeShiftUseCase
import com.stratum.application.workout.RepRangeShiftDiagnostic
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.feature.workout.ui.EquipmentEditorDestination
import com.stratum.feature.workout.ui.WorkoutCompleteEvent
import com.stratum.feature.workout.ui.WorkoutCompleteUiState
import com.stratum.feature.workout.ui.resolveEquipmentEditorDestination
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Presentation-mapping tests for [WorkoutCompleteViewModel]. State-based assertions on
 * `uiState` only (UNIT_TESTING.md §1). Wired with real use cases against `:core:testing`
 * fakes — no MockK.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
@DisplayName("WorkoutCompleteViewModel presentation mapping")
class WorkoutCompleteViewModelTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val latId = LiftId("lat-pulldown")
    private val openFive =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

    @Test
    @DisplayName("Loaded state maps lift name, performance, RIR, and e1RM for a loaded set")
    fun loadedStateMapsBestSetFields() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 185.0,
            reps = 3,
            rir = 1,
        )

        viewModel.loadSummary("ts-1")
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        val bestSet = loaded.bestSets.single()
        assertEquals("Barbell Bench Press", bestSet.liftName)
        assertEquals("185 × 3", bestSet.performanceLabel)
        assertEquals("1", bestSet.rirLabel)
        assertEquals(expectedOneRmLabel(185.0, 3, 1), bestSet.estimatedOneRmLabel)
        assertTrue(loaded.equipmentProposals.isEmpty())
    }

    @Test
    @DisplayName("Sticky sync error surfaces the mapped text and clears")
    fun stickySyncErrorSurfacesMappedTextAndClears() = runTest {
        val syncStatus = FakeHealthConnectSyncStatusPort()
        val viewModel =
            buildViewModelWithBenchSet(
                weight = 135.0,
                reps = 5,
                rir = 2,
                healthConnectSyncStatus = syncStatus,
            )

        viewModel.loadSummary("ts-1")
        advanceUntilIdle()

        syncStatus.reportSyncError(HealthFailureKind.Unavailable)
        advanceUntilIdle()

        var loaded = viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertEquals(
            "Health Connect SDK is not available on this device",
            loaded.userMessage,
        )
        assertEquals(null, syncStatus.currentPendingError)
        viewModel.userMessageShown()
        advanceUntilIdle()

        syncStatus.reportSyncError(HealthFailureKind.PermissionDenied)
        advanceUntilIdle()

        loaded = viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertEquals(
            "Health Connect write permission is not granted",
            loaded.userMessage,
        )
        assertEquals(null, syncStatus.currentPendingError)
        viewModel.userMessageShown()
        advanceUntilIdle()

        syncStatus.reportSyncError(HealthFailureKind.Unknown)
        advanceUntilIdle()

        loaded = viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertEquals(
            "Failed to sync workout to Health Connect",
            loaded.userMessage,
        )
        assertEquals(null, syncStatus.currentPendingError)
    }

    @Test
    @DisplayName("Null RIR renders as an em dash")
    fun nullRirRendersAsDash() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 135.0,
            reps = 5,
            rir = null,
        )

        viewModel.loadSummary("ts-1")
        advanceUntilIdle()

        val bestSet = (viewModel.uiState.value as WorkoutCompleteUiState.Loaded).bestSets.single()
        assertEquals("—", bestSet.rirLabel)
    }

    @Test
    @DisplayName("Bodyweight set (weight 0.0) performance label shows reps only")
    fun bodyweightPerformanceLabelOmitsWeight() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 0.0,
            reps = 8,
            rir = 2,
        )

        viewModel.loadSummary("ts-1")
        advanceUntilIdle()

        val bestSet = (viewModel.uiState.value as WorkoutCompleteUiState.Loaded).bestSets.single()
        assertEquals("8", bestSet.performanceLabel)
        assertEquals("—", bestSet.estimatedOneRmLabel)
    }

    @Test
    @DisplayName("Best-set performance label includes +N partials")
    fun performanceLabelIncludesPartials() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 185.0,
            reps = 3,
            rir = 1,
            partialReps = 2,
        )
        viewModel.loadSummary("ts-1")
        advanceUntilIdle()

        val bestSet = (viewModel.uiState.value as WorkoutCompleteUiState.Loaded).bestSets.single()
        assertEquals("185 × 3+2", bestSet.performanceLabel)
    }

    @Test
    @DisplayName("isPersonalRecord maps through from the summary best set")
    fun personalRecordMapsFromSummary() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 0.0,
            reps = 12,
            rir = 0,
            isPersonalRecord = true,
        )

        viewModel.loadSummary("ts-1")
        advanceUntilIdle()

        val bestSet = (viewModel.uiState.value as WorkoutCompleteUiState.Loaded).bestSets.single()
        assertTrue(bestSet.isPersonalRecord)
        assertEquals("12", bestSet.performanceLabel)
        assertEquals("—", bestSet.estimatedOneRmLabel)
    }

    @Test
    @DisplayName("Loaded state flows through uiState Turbine stream")
    fun loadedStateObservableViaTurbine() = runTest {
        val viewModel = buildViewModelWithBenchSet(
            weight = 185.0,
            reps = 3,
            rir = 1,
        )

        viewModel.uiState.test {
            assertEquals(WorkoutCompleteUiState.Loading, awaitItem())
            viewModel.loadSummary("ts-1")
            val loaded = awaitItem() as WorkoutCompleteUiState.Loaded
            assertEquals("Workout A", loaded.workoutName)
            assertEquals("1:00:00", loaded.durationFormatted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given steep fatigue + weak last set when loaded then rep-range card is mapped")
    fun mapsRepRangeProposalCard() = runTest {
        val harness = buildRepRangeHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertEquals(1, loaded.repRangeProposals.size)
        val card = loaded.repRangeProposals.single()
        assertTrue(card.id.startsWith("rrs:"))
        assertEquals("Barbell Bench Press", card.liftName)
        assertEquals("8–10", card.currentBand)
        assertTrue(card.proposedBand.isNotBlank())
        assertFalse(card.isApplying)
    }

    @Test
    @DisplayName("Given rep-range proposal when accept then template band updates and card removes")
    fun acceptRepRangeAppliesAndRemoves() = runTest {
        val harness = buildRepRangeHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val before = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        val card = before.repRangeProposals.single()
        harness.viewModel.onAcceptRepRangeShift(card.id)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertTrue(loaded.repRangeProposals.isEmpty())
        assertNotNull(loaded.userMessage)
        val updated =
            harness.programRepo.current!!
                .workouts
                .single()
                .workoutLifts
                .single()
        assertEquals(true, updated.sets[0].repMin > 8)
    }

    @Test
    @DisplayName("Given rep-range proposal when not now then fingerprint dismissed on shared prefs")
    fun notNowRepRangeDismissesFingerprint() = runTest {
        val harness = buildRepRangeHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val id =
            (harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded)
                .repRangeProposals
                .single()
                .id
        harness.viewModel.onNotNowProposal(id)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertTrue(loaded.repRangeProposals.isEmpty())
        assertTrue(id in harness.repRangePrefs.dismissedFingerprints)
    }

    @Test
    @DisplayName("Given off-lattice cable sets when loaded then proposal card is mapped")
    fun mapsEquipmentProposalCard() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertEquals(1, loaded.equipmentProposals.size)
        val card = loaded.equipmentProposals.single()
        assertTrue(card.id.isNotBlank())
        assertTrue(card.title.isNotBlank())
        assertTrue(card.tierSummary.isNotBlank())
        assertEquals("Lat pulldown", card.liftNames)
        assertEquals("Accept", card.primaryButtonLabel)
        // Many inheritors → Fork primary → no blast confirm on Accept.
        assertFalse(card.requiresConfirm)
        assertTrue(card.blastRadiusCount > 3)
        assertFalse(card.isApplying)
    }

    @Test
    @DisplayName("Given proposal when skip then card removed and prefs untouched")
    fun skipRemovesCardWithoutDismissPref() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val id =
            (harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded)
                .equipmentProposals
                .single()
                .id
        harness.viewModel.onSkipProposal(id)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertTrue(loaded.equipmentProposals.isEmpty())
        assertTrue(harness.prefs.dismissedFingerprints.isEmpty())
    }

    @Test
    @DisplayName("Given proposal when not now then card removed and fingerprint dismissed")
    fun notNowDismissesFingerprint() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val id =
            (harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded)
                .equipmentProposals
                .single()
                .id
        harness.viewModel.onNotNowProposal(id)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertTrue(loaded.equipmentProposals.isEmpty())
        assertTrue(id in harness.prefs.dismissedFingerprints)
    }

    @Test
    @DisplayName("Given proposal when edit then editor event carries override target and proposed tiers")
    fun editEmitsEditorRequestAndRemovesCard() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val id =
            (harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded)
                .equipmentProposals
                .single()
                .id
        harness.viewModel.events.test {
            harness.viewModel.onEditProposal(id)

            val event = awaitItem() as WorkoutCompleteEvent.OpenEquipmentEditor
            // Fork primary on seed Cables → shared equipment editor anchored on the
            // anomalous lift (STR-86).
            assertEquals("lat-pulldown", event.request.liftId)
            assertEquals("cable", event.request.target.equipmentId)
            assertEquals(
                EquipmentEditorDestination.SharedEquipment("cable", "lat-pulldown"),
                resolveEquipmentEditorDestination(event.request),
            )
            assertTrue(event.request.initialTiersLb.isNotEmpty())
            assertTrue(event.request.initialTiersLb.all { it.incrementLb > 0.0 })
            cancelAndIgnoreRemainingEvents()
        }

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertTrue(loaded.equipmentProposals.isEmpty())
        // Session-scoped removal only — no permanent fingerprint like Not now.
        assertTrue(harness.prefs.dismissedFingerprints.isEmpty())
    }

    @Test
    @DisplayName("Given fixed tiers when refresh after editor return then card stays gone")
    fun refreshAfterTiersFixedClearsCard() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val before = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertEquals(1, before.equipmentProposals.size)
        // Simulate the editor save: 62.5 / 67.5 now sit on a 2.5-lb lattice.
        // (Per-lift tier writes are gone in STR-84; the fake lift is re-seeded with
        // the fixed lattice the shared-equipment save would now produce.)
        val fixed = listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb)))
        harness.liftRepo.seedLifts(
            harness.liftRepo.currentLifts.map { lift ->
                if (lift.id == latId) lift.copy(incrementTiers = fixed) else lift
            },
        )
        harness.viewModel.refreshEquipmentProposals(harness.sessionId)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertTrue(loaded.equipmentProposals.isEmpty())
        // Summary content is preserved by the equipment-only refresh.
        assertEquals(before.bestSets, loaded.bestSets)
    }

    @Test
    @DisplayName("Given unchanged tiers when refresh then card remains")
    fun refreshWithoutChangesKeepsCard() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        harness.viewModel.refreshEquipmentProposals(harness.sessionId)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertEquals(1, loaded.equipmentProposals.size)
    }

    @Test
    @DisplayName("Given unknown proposal when edit then no event and state unchanged")
    fun editUnknownIdEmitsNothing() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        harness.viewModel.events.test {
            harness.viewModel.onEditProposal("missing-id")
            expectNoEvents()
        }

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertEquals(1, loaded.equipmentProposals.size)
    }

    @Test
    @DisplayName("Given fork primary when accept then applies and removes card")
    fun acceptForkAppliesAndRemoves() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val id =
            (harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded)
                .equipmentProposals
                .single()
                .id
        harness.viewModel.onAcceptPrimary(id)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertTrue(loaded.equipmentProposals.isEmpty())
        assertNull(loaded.blastRadiusConfirm)
        assertNotNull(loaded.userMessage)
        assertTrue(loaded.userMessage!!.startsWith("Created"))
        // Custom equipment was created (catalog grew beyond seed cable).
        assertTrue(harness.equipmentRepo.current.any { it.isCustom })
    }

    @Test
    @DisplayName("Given seed amend alternative when chosen then stages blast-radius confirm")
    fun alternativeAmendStagesConfirm() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val card =
            (harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded)
                .equipmentProposals
                .single()
        assertEquals("Update shared instead", card.alternativeButtonLabel)

        harness.viewModel.onAcceptAlternative(card.id)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertNotNull(loaded.blastRadiusConfirm)
        assertEquals(card.id, loaded.blastRadiusConfirm!!.proposalId)
        assertTrue(loaded.blastRadiusConfirm!!.body.contains(card.blastRadiusCount.toString()))
        // Card still present until confirm.
        assertEquals(1, loaded.equipmentProposals.size)
    }

    @Test
    @DisplayName("Given staged confirm when confirmed then amends seed tiers and removes card")
    fun confirmAcceptAmendsSeed() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val card =
            (harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded)
                .equipmentProposals
                .single()
        harness.viewModel.onAcceptAlternative(card.id)
        advanceUntilIdle()
        harness.viewModel.onConfirmAccept()
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertTrue(loaded.equipmentProposals.isEmpty())
        assertNull(loaded.blastRadiusConfirm)
        assertEquals("Updated equipment steps.", loaded.userMessage)
        val cableTiers = harness.equipmentRepo.getEquipment(cableId)!!.incrementTiers
        // No longer a single 5-lb open band — inference proposed a finer step.
        assertTrue(cableTiers.any { it.increment.amount < 5.0 } || cableTiers != openFive)
    }

    @Test
    @DisplayName("Given staged confirm when dismissed then card remains and no write")
    fun dismissConfirmKeepsCard() = runTest {
        val harness = buildCableAnomalyHarness()
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val beforeTiers = harness.equipmentRepo.getEquipment(cableId)!!.incrementTiers
        val card =
            (harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded)
                .equipmentProposals
                .single()
        harness.viewModel.onAcceptAlternative(card.id)
        advanceUntilIdle()
        harness.viewModel.onDismissConfirm()
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertNull(loaded.blastRadiusConfirm)
        assertEquals(1, loaded.equipmentProposals.size)
        assertEquals(beforeTiers, harness.equipmentRepo.getEquipment(cableId)!!.incrementTiers)
    }

    @Test
    @DisplayName("Given apply failure when accept then card is restored for retry")
    fun applyFailureRestoresCard() = runTest {
        val harness = buildCableAnomalyHarness()
        // Delete seed equipment so Fork still works... actually make apply fail by
        // emptying tiers validation — force repository to throw on create.
        harness.equipmentRepo.failNextWrite = true
        harness.viewModel.loadSummary(harness.sessionId)
        advanceUntilIdle()

        val before =
            (harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded)
                .equipmentProposals
                .single()
        harness.viewModel.onAcceptPrimary(before.id)
        advanceUntilIdle()

        val loaded = harness.viewModel.uiState.value as WorkoutCompleteUiState.Loaded
        assertEquals(1, loaded.equipmentProposals.size)
        assertEquals(before.id, loaded.equipmentProposals.single().id)
        assertFalse(loaded.equipmentProposals.single().isApplying)
        assertEquals("Couldn't apply equipment update.", loaded.userMessage)
    }

    private data class CableHarness(
        val viewModel: WorkoutCompleteViewModel,
        val prefs: FakeEquipmentProposalPreferences,
        val equipmentRepo: FakeEquipmentRepository,
        val liftRepo: FakeLiftRepository,
        val sessionId: String,
    )

    private data class RepRangeHarness(
        val viewModel: WorkoutCompleteViewModel,
        val programRepo: FakeProgramRepository,
        val repRangePrefs: FakeRepRangeShiftProposalPreferences,
        val sessionId: String,
    )

    /**
     * Multi-session steep fatigue + current last set under floor on a 4-set 8–10 Double lift.
     */
    private fun buildRepRangeHarness(): RepRangeHarness {
        val sessionId = "ts-rrs-vm"
        val wlId = com.stratum.domain.program.identity.WorkoutLiftId("wl-bench-vm")
        val program =
            WorkoutLoggingFixtures.twoWorkoutProgram(
                workouts =
                    listOf(
                        com.stratum.domain.program.Workout(
                            id = WorkoutLoggingFixtures.workoutAId,
                            programId = WorkoutLoggingFixtures.programId,
                            name = "Workout A",
                            weekday = com.stratum.domain.onboarding.Weekday.Monday,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    com.stratum.domain.program.WorkoutLift(
                                        id = wlId,
                                        workoutId = WorkoutLoggingFixtures.workoutAId,
                                        liftId = WorkoutLoggingFixtures.liftBenchId,
                                        progressionScheme =
                                            com.stratum.domain.program.ProgressionScheme.Double,
                                        position = 0,
                                        sets =
                                            (0 until 4).map { index ->
                                                com.stratum.domain.program.LiftSet(
                                                    id =
                                                        com.stratum.domain.program.identity.LiftSetId(
                                                            "ls-$index",
                                                        ),
                                                    workoutLiftId = wlId,
                                                    position = index,
                                                    setType =
                                                        com.stratum.domain.program.SetType.Standard,
                                                    repMin = if (index == 0) 8 else 0,
                                                    repMax = if (index == 0) 10 else 0,
                                                    rirGoal = if (index == 0) 2 else 1,
                                                    isTopSet = index == 0,
                                                )
                                            },
                                    ),
                                ),
                        ),
                    ),
            )
        val programRepo = FakeProgramRepository(program)
        val logRepo = FakeTrainingLogRepository(programRepository = programRepo)
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        // Seed history + current via runBlocking is awkward in sync factory — use
        // initial sessions/sets constructed inline after coroutine-free inserts.
        // FakeTrainingLogRepository seed methods are suspend; use constructor seeds.
        val histSessions =
            (1..3).map { sessionIndex ->
                WorkoutLoggingFixtures.completedLiveSession(
                    id = "ts-hist-$sessionIndex",
                ).copy(
                    startedAt = t0.plusSeconds(sessionIndex * 86_400L),
                    endedAt = t0.plusSeconds(sessionIndex * 86_400L + 3600),
                )
            }
        val histSets =
            histSessions.flatMap { session ->
                listOf(14, 11, 8).mapIndexed { setIndex, reps ->
                    LoggedSet(
                        id = LoggedSetId("${session.id.value}-$setIndex"),
                        sessionId = session.id,
                        exerciseNameRaw = "Bench Press",
                        liftId = WorkoutLoggingFixtures.liftBenchId,
                        setIndex = setIndex,
                        weight = Weight(135.0, lb),
                        reps = reps,
                        rir = 0,
                        completedAt = session.startedAt.plusSeconds(setIndex * 120L),
                        liftContext =
                            com.stratum.domain.training.LoggedLiftContext(
                                liftName = "Barbell Bench Press",
                                workoutLiftId = wlId,
                            ),
                    )
                }
            }
        val current =
            WorkoutLoggingFixtures.completedLiveSession(id = sessionId).copy(
                startedAt = t0.plusSeconds(10 * 86_400L),
                endedAt = t0.plusSeconds(10 * 86_400L + 3600),
            )
        val currentSets =
            listOf(8, 6, 5, 3).mapIndexed { setIndex, reps ->
                LoggedSet(
                    id = LoggedSetId("cur-$setIndex"),
                    sessionId = current.id,
                    exerciseNameRaw = "Bench Press",
                    liftId = WorkoutLoggingFixtures.liftBenchId,
                    setIndex = setIndex,
                    weight = Weight(135.0, lb),
                    reps = reps,
                    rir = 2,
                    completedAt = current.startedAt.plusSeconds(setIndex * 120L),
                    liftContext =
                        com.stratum.domain.training.LoggedLiftContext(
                            liftName = "Barbell Bench Press",
                            workoutLiftId = wlId,
                        ),
                )
            }
        val allSessions = histSessions + current
        val allSets = histSets + currentSets
        val seededLog =
            FakeTrainingLogRepository(
                initialSessions = allSessions,
                initialSets = allSets,
                programRepository = programRepo,
            )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val equipmentRepo = FakeEquipmentRepository()
        val equipPrefs = FakeEquipmentProposalPreferences()
        val repRangePrefs = FakeRepRangeShiftProposalPreferences()
        val logging = FakeLoggingPort()
        val insightsRepo = FakeLiftTrainingInsightsRepository()
        val healthConnectSyncStatus = FakeHealthConnectSyncStatusPort()
        val viewModel =
            WorkoutCompleteViewModel(
                getWorkoutSummary =
                    GetWorkoutSummaryUseCase(
                        seededLog,
                        programRepo,
                        ObserveLiftCatalogUseCase(liftRepo),
                        logging,
                    ),
                analyzeSessionEquipmentProposals =
                    AnalyzeSessionEquipmentProposalsUseCase(
                        trainingLogRepository = seededLog,
                        liftRepository = liftRepo,
                        equipmentRepository = equipmentRepo,
                        preferences = equipPrefs,
                    ),
                applyEquipmentIncrementProposal =
                    ApplyEquipmentIncrementProposalUseCase(
                        equipmentRepository = equipmentRepo,
                        loggingPort = logging,
                    ),
                equipmentProposalPreferences = equipPrefs,
                analyzeSessionRepRangeShiftProposals =
                    AnalyzeSessionRepRangeShiftProposalsUseCase(
                        trainingLogRepository = seededLog,
                        programRepository = programRepo,
                        liftTrainingInsightsRepository = insightsRepo,
                        liftRepository = liftRepo,
                        preferences = repRangePrefs,
                        extractTrainingInsights =
                            ExtractTrainingInsightsUseCase(
                                trainingLogRepository = seededLog,
                                liftTrainingInsightsRepository = insightsRepo,
                                supersetAffinityRepository = FakeSupersetAffinityRepository(),
                                muscleVolumeBaselineRepository =
                                    FakeMuscleVolumeBaselineRepository(),
                                liftRepository = liftRepo,
                                importAnalysisMetadataRepository =
                                    FakeImportAnalysisMetadataRepository(),
                            ),
                    ),
                applyRepRangeShift =
                    ApplyRepRangeShiftUseCase(
                        programRepository = programRepo,
                        programChangeEventRepository = FakeProgramChangeEventRepository(),
                        loggingPort = logging,
                    ),
                repRangeShiftProposalPreferences = repRangePrefs,
                logError = LogErrorUseCase(logging),
                observeWeightDisplayPreferences =
                    ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
                observeHealthConnectSyncErrors =
                    ObserveHealthConnectSyncErrorsUseCase(healthConnectSyncStatus),
                clearHealthConnectSyncError =
                    ClearHealthConnectSyncErrorUseCase(healthConnectSyncStatus, logging),
            )
        return RepRangeHarness(viewModel, programRepo, repRangePrefs, sessionId)
    }

    /**
     * Off-lattice cable lat work on a high-inheritor seed so analyze emits a Fork-primary
     * proposal with Amend as alternative (blast-radius confirm path).
     */
    private fun buildCableAnomalyHarness(): CableHarness {
        val sessionId = "ts-cable-1"
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = sessionId)
        val latSets =
            listOf(
                cableSet(sessionId, latId, 62.5, 0, "s1"),
                cableSet(sessionId, latId, 62.5, 1, "s2"),
                cableSet(sessionId, latId, 67.5, 2, "s3"),
            )
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                initialSets = latSets,
                programRepository = programRepo,
            )
        val liftRepo = FakeLiftRepository()
        val equipmentRepo = FakeEquipmentRepository(liftRepository = liftRepo)
        equipmentRepo.seed(
            listOf(
                Equipment(
                    id = cableId,
                    name = "Cables",
                    incrementTiers = openFive,
                    isCustom = false,
                ),
            ),
        )
        val lifts =
            (0 until 12).map { index ->
                val id = if (index == 0) latId else LiftId("cable-lift-$index")
                val name = if (index == 0) "Lat pulldown" else "Cable lift $index"
                Lift(
                    id = id,
                    name = name,
                    movementPatterns = setOf(MovementPattern.VerticalPull),
                    primaryMuscles = setOf(MuscleGroup.Back),
                    secondaryMuscles = emptySet(),
                    incrementTiers = openFive,
                    presetTags = emptySet(),
                    enabled = true,
                    equipmentId = cableId,
                    loadType = LoadType.Weighted,
                )
            }
        liftRepo.seedLifts(lifts)
        liftRepo.equipmentNamesById = mapOf(cableId to "Cables")
        val prefs = FakeEquipmentProposalPreferences()
        val repRangePrefs = FakeRepRangeShiftProposalPreferences()
        val logging = FakeLoggingPort()
        val insightsRepo = FakeLiftTrainingInsightsRepository()
        val healthConnectSyncStatus = FakeHealthConnectSyncStatusPort()
        val viewModel =
            WorkoutCompleteViewModel(
                getWorkoutSummary =
                    GetWorkoutSummaryUseCase(
                        logRepo,
                        programRepo,
                        ObserveLiftCatalogUseCase(liftRepo),
                        logging,
                    ),
                analyzeSessionEquipmentProposals =
                    AnalyzeSessionEquipmentProposalsUseCase(
                        trainingLogRepository = logRepo,
                        liftRepository = liftRepo,
                        equipmentRepository = equipmentRepo,
                        preferences = prefs,
                    ),
                applyEquipmentIncrementProposal =
                    ApplyEquipmentIncrementProposalUseCase(
                        equipmentRepository = equipmentRepo,
                        loggingPort = logging,
                    ),
                equipmentProposalPreferences = prefs,
                analyzeSessionRepRangeShiftProposals =
                    AnalyzeSessionRepRangeShiftProposalsUseCase(
                        trainingLogRepository = logRepo,
                        programRepository = programRepo,
                        liftTrainingInsightsRepository = insightsRepo,
                        liftRepository = liftRepo,
                        preferences = repRangePrefs,
                        extractTrainingInsights =
                            ExtractTrainingInsightsUseCase(
                                trainingLogRepository = logRepo,
                                liftTrainingInsightsRepository = insightsRepo,
                                supersetAffinityRepository = FakeSupersetAffinityRepository(),
                                muscleVolumeBaselineRepository =
                                    FakeMuscleVolumeBaselineRepository(),
                                liftRepository = liftRepo,
                                importAnalysisMetadataRepository =
                                    FakeImportAnalysisMetadataRepository(),
                            ),
                    ),
                applyRepRangeShift =
                    ApplyRepRangeShiftUseCase(
                        programRepository = programRepo,
                        programChangeEventRepository = FakeProgramChangeEventRepository(),
                        loggingPort = logging,
                    ),
                repRangeShiftProposalPreferences = repRangePrefs,
                logError = LogErrorUseCase(logging),
                observeWeightDisplayPreferences =
                    ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
                observeHealthConnectSyncErrors =
                    ObserveHealthConnectSyncErrorsUseCase(healthConnectSyncStatus),
                clearHealthConnectSyncError =
                    ClearHealthConnectSyncErrorUseCase(healthConnectSyncStatus, logging),
            )
        return CableHarness(viewModel, prefs, equipmentRepo, liftRepo, sessionId)
    }

    private fun cableSet(
        sessionId: String,
        liftId: LiftId,
        weight: Double,
        setIndex: Int,
        id: String,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = TrainingSessionId(sessionId),
            exerciseNameRaw = "Lat pulldown",
            liftId = liftId,
            setIndex = setIndex,
            weight = Weight(weight, lb),
            reps = 8,
            rir = 1,
            completedAt = Instant.parse("2025-01-01T10:05:00Z"),
            liftContext = WorkoutLoggingFixtures.importLiftContext(),
        )

    @Test
    @DisplayName("Analyzer diagnostics drain exactly once with the bound cause attached")
    fun analyzerDiagnosticsDrainOnce() = runTest {
        val (viewModel, logging) = buildDrainHarness()
        val sessionId = TrainingSessionId("ts-1")
        val equipmentCause = IllegalStateException("equipment boom")
        val repRangeCause = IllegalStateException("rep-range boom")

        viewModel.logEquipmentDiagnostics(
            listOf(EquipmentProposalDiagnostic.AnalysisFailed(sessionId, equipmentCause)),
        )
        viewModel.logRepRangeDiagnostics(
            listOf(RepRangeShiftDiagnostic.AnalysisFailed(sessionId, repRangeCause)),
        )

        assertEquals(2, logging.errors.size)
        assertSame(equipmentCause, logging.errors[0].throwable)
        assertSame(repRangeCause, logging.errors[1].throwable)
    }

    @Test
    @DisplayName("Empty analyzer diagnostics drain to zero log writes")
    fun emptyDiagnosticsDrainSilently() = runTest {
        val (viewModel, logging) = buildDrainHarness()

        viewModel.logEquipmentDiagnostics(emptyList())
        viewModel.logRepRangeDiagnostics(emptyList())

        assertTrue(logging.errors.isEmpty())
    }

    private fun buildDrainHarness(): Pair<WorkoutCompleteViewModel, FakeLoggingPort> {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val logRepo = FakeTrainingLogRepository(programRepository = programRepo)
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val equipmentRepo = FakeEquipmentRepository()
        val prefs = FakeEquipmentProposalPreferences()
        val repRangePrefs = FakeRepRangeShiftProposalPreferences()
        val logging = FakeLoggingPort()
        val insightsRepo = FakeLiftTrainingInsightsRepository()
        val healthConnectSyncStatus = FakeHealthConnectSyncStatusPort()
        val viewModel =
            WorkoutCompleteViewModel(
                getWorkoutSummary =
                    GetWorkoutSummaryUseCase(
                        logRepo,
                        programRepo,
                        ObserveLiftCatalogUseCase(liftRepo),
                        logging,
                    ),
                analyzeSessionEquipmentProposals =
                    AnalyzeSessionEquipmentProposalsUseCase(
                        trainingLogRepository = logRepo,
                        liftRepository = liftRepo,
                        equipmentRepository = equipmentRepo,
                        preferences = prefs,
                    ),
                applyEquipmentIncrementProposal =
                    ApplyEquipmentIncrementProposalUseCase(
                        equipmentRepository = equipmentRepo,
                        loggingPort = logging,
                    ),
                equipmentProposalPreferences = prefs,
                analyzeSessionRepRangeShiftProposals =
                    AnalyzeSessionRepRangeShiftProposalsUseCase(
                        trainingLogRepository = logRepo,
                        programRepository = programRepo,
                        liftTrainingInsightsRepository = insightsRepo,
                        liftRepository = liftRepo,
                        preferences = repRangePrefs,
                        extractTrainingInsights =
                            ExtractTrainingInsightsUseCase(
                                trainingLogRepository = logRepo,
                                liftTrainingInsightsRepository = insightsRepo,
                                supersetAffinityRepository = FakeSupersetAffinityRepository(),
                                muscleVolumeBaselineRepository = FakeMuscleVolumeBaselineRepository(),
                                liftRepository = liftRepo,
                                importAnalysisMetadataRepository =
                                    FakeImportAnalysisMetadataRepository(),
                            ),
                    ),
                applyRepRangeShift =
                    ApplyRepRangeShiftUseCase(
                        programRepository = programRepo,
                        programChangeEventRepository = FakeProgramChangeEventRepository(),
                        loggingPort = logging,
                    ),
                repRangeShiftProposalPreferences = repRangePrefs,
                logError = LogErrorUseCase(logging),
                observeWeightDisplayPreferences =
                    ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
                observeHealthConnectSyncErrors =
                    ObserveHealthConnectSyncErrorsUseCase(healthConnectSyncStatus),
                clearHealthConnectSyncError =
                    ClearHealthConnectSyncErrorUseCase(healthConnectSyncStatus, logging),
            )
        return viewModel to logging
    }

    private fun buildViewModelWithBenchSet(
        weight: Double,
        reps: Int,
        rir: Int?,
        partialReps: Int? = null,
        isPersonalRecord: Boolean = false,
        healthConnectSyncStatus: FakeHealthConnectSyncStatusPort = FakeHealthConnectSyncStatusPort(),
    ): WorkoutCompleteViewModel {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val benchSet =
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Barbell Bench Press",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(weight, Weight.WeightUnit.Lb),
                reps = reps,
                rir = rir,
                completedAt = Instant.parse("2025-01-01T10:05:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
                partialReps = partialReps,
                isPersonalRecord = isPersonalRecord,
            )
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                initialSets = listOf(benchSet),
                programRepository = programRepo,
            )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val equipmentRepo = FakeEquipmentRepository()
        val prefs = FakeEquipmentProposalPreferences()
        val repRangePrefs = FakeRepRangeShiftProposalPreferences()
        val logging = FakeLoggingPort()
        val insightsRepo = FakeLiftTrainingInsightsRepository()
        return WorkoutCompleteViewModel(
            getWorkoutSummary =
                GetWorkoutSummaryUseCase(
                    logRepo,
                    programRepo,
                    ObserveLiftCatalogUseCase(liftRepo),
                    logging,
                ),
            analyzeSessionEquipmentProposals =
                AnalyzeSessionEquipmentProposalsUseCase(
                    trainingLogRepository = logRepo,
                    liftRepository = liftRepo,
                    equipmentRepository = equipmentRepo,
                    preferences = prefs,
                ),
            applyEquipmentIncrementProposal =
                ApplyEquipmentIncrementProposalUseCase(
                    equipmentRepository = equipmentRepo,
                    loggingPort = logging,
                ),
            equipmentProposalPreferences = prefs,
            analyzeSessionRepRangeShiftProposals =
                AnalyzeSessionRepRangeShiftProposalsUseCase(
                    trainingLogRepository = logRepo,
                    programRepository = programRepo,
                    liftTrainingInsightsRepository = insightsRepo,
                    liftRepository = liftRepo,
                    preferences = repRangePrefs,
                    extractTrainingInsights =
                        ExtractTrainingInsightsUseCase(
                            trainingLogRepository = logRepo,
                            liftTrainingInsightsRepository = insightsRepo,
                            supersetAffinityRepository = FakeSupersetAffinityRepository(),
                            muscleVolumeBaselineRepository = FakeMuscleVolumeBaselineRepository(),
                            liftRepository = liftRepo,
                            importAnalysisMetadataRepository =
                                FakeImportAnalysisMetadataRepository(),
                        ),
                ),
            applyRepRangeShift =
                ApplyRepRangeShiftUseCase(
                    programRepository = programRepo,
                    programChangeEventRepository = FakeProgramChangeEventRepository(),
                    loggingPort = logging,
                ),
            repRangeShiftProposalPreferences = repRangePrefs,
            logError = LogErrorUseCase(logging),
            observeWeightDisplayPreferences =
                ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
            observeHealthConnectSyncErrors =
                ObserveHealthConnectSyncErrorsUseCase(healthConnectSyncStatus),
            clearHealthConnectSyncError =
                ClearHealthConnectSyncErrorUseCase(healthConnectSyncStatus, logging),
        )
    }

    private fun expectedOneRmLabel(weight: Double, reps: Int, rir: Int? = null): String {
        val e1Rm = EstimatedOneRm.calculate(weight, reps + (rir ?: 0))
        val rounded = (e1Rm * 10).toInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private fun benchLift(): Lift =
        Lift(
            id = WorkoutLoggingFixtures.liftBenchId,
            name = "Barbell Bench Press",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )
}
