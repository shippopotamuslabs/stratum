package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.featureflag.FakeFeatureFlagRepository
import com.stratum.core.testing.lift.FakeIncrementScanPort
import com.stratum.core.testing.lift.testForkEquipmentWithTiers
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentProposalPreferences
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.featureflag.ObserveIncrementsAiScanUseCase
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveEquipmentAffectedLiftsUseCase
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.ScanIncrementTiersUseCase
import com.stratum.domain.lift.SetEquipmentIncrementTiersUseCase
import com.stratum.domain.lift.ValidateMicroIncrementsUseCase
import com.stratum.domain.lift.SetLiftEquipmentUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class EquipmentIncrementsViewModelTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val openFive =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

    private val fakeEquipmentRepository = FakeEquipmentRepository()
    private val fakeLiftRepository = FakeLiftRepository()
    private val fakeFeatureFlags = FakeFeatureFlagRepository()
    private val fakeProposalPrefs = FakeEquipmentProposalPreferences()
    private val equipmentPickResult = EquipmentPickResult()
    private lateinit var subject: EquipmentIncrementsViewModel

    @BeforeEach
    fun setup() {
        fakeFeatureFlags.clear()
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(
                    id = cableId,
                    name = "Cables",
                    incrementTiers = openFive,
                    isCustom = false,
                ),
            ),
        )
        fakeLiftRepository.seedLifts(
            listOf(
                lift("lat-pulldown", "Lat pulldown", enabled = true),
                lift("cable-row", "Cable row", enabled = false),
                lift("face-pull", "Face pull", enabled = true),
                lift("tricep-pushdown", "Tricep pushdown", enabled = true),
                lift("cable-crunch", "Cable crunch", enabled = true),
            ),
        )
        subject =
            EquipmentIncrementsViewModel(
                observeEquipmentCatalog = ObserveEquipmentCatalogUseCase(fakeEquipmentRepository),
                observeEquipmentAffectedLifts =
                    ObserveEquipmentAffectedLiftsUseCase(fakeLiftRepository),
                setEquipmentIncrementTiers =
                    SetEquipmentIncrementTiersUseCase(
                        fakeEquipmentRepository,
                        ValidateIncrementTiersUseCase(),
                        ValidateMicroIncrementsUseCase(),
                        FakeLoggingPort(),
                    ),
                validateIncrementTiers = ValidateIncrementTiersUseCase(),
                validateMicroIncrements = ValidateMicroIncrementsUseCase(),
                forkEquipmentWithTiers =
                    testForkEquipmentWithTiers(fakeEquipmentRepository, fakeLiftRepository),
                setLiftEquipment = SetLiftEquipmentUseCase(fakeLiftRepository, FakeLoggingPort()),
                dismissEquipmentProposal =
                    DismissEquipmentProposalUseCase(
                        fakeProposalPrefs,
                        FakeLoggingPort(),
                    ),
                equipmentPickResult = equipmentPickResult,
                observeWeightDisplayPreferences =
                    com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase(
                        com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository(),
                    ),
                scanDelegate =
                    IncrementScanDelegate(
                        ScanIncrementTiersUseCase(
                            FakeIncrementScanPort(),
                            ValidateIncrementTiersUseCase(),
                            ValidateMicroIncrementsUseCase(),
                            FakeLoggingPort(),
                        ),
                        ObserveIncrementsAiScanUseCase(fakeFeatureFlags),
                    ),
            )
    }

    @Test
    @DisplayName(
        "Given inheritors including disabled when initialized then count and names include all linked lifts",
    )
    fun affectedCountIncludesDisabledAndAllLinked() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()

        subject.uiState.test {
            val state = awaitItemWith { it.equipmentName == "Cables" && it.affectedLiftCount > 0 }
            // lat, cable-row (disabled), face-pull, tricep, cable-crunch — every linked
            // lift inherits equipment defaults (STR-82/83 removed per-lift overrides).
            assertEquals(5, state.affectedLiftCount)
            assertEquals(
                listOf("Cable crunch", "Cable row", "Face pull", "Lat pulldown", "Tricep pushdown"),
                state.affectedLiftNames,
            )
            assertTrue(state.editor.tiers.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("formatAffectsBanner helpers: preview limit is 3 names")
    fun affectedNamesOrderedForPreview() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()
        subject.uiState.test {
            val state = awaitItemWith { it.affectedLiftCount == 5 }
            assertEquals(5, state.affectedLiftNames.size)
            // First 3 alphabetical for "+2 more" UI path
            assertEquals(
                listOf("Cable crunch", "Cable row", "Face pull"),
                state.affectedLiftNames.take(3),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName(
        "Given edited tiers when save as new from a lift then the copy takes the edits, " +
            "the shared tiers are untouched, and the lift moves to the copy",
    )
    fun saveAsNewForksEditsAndReassignsSourceLift() = runTest {
        subject.initialize(cableId.value, "lat-pulldown")
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.onTierIncrementChanged(0, "10")
            subject.saveAsNew()
            assertTrue(awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        val rows = fakeEquipmentRepository.current
        val original = rows.single { it.id == cableId }
        assertEquals(openFive, original.incrementTiers)
        val copy = rows.single { it.id != cableId }
        assertEquals("Cables (copy)", copy.name)
        assertEquals(
            listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb))),
            copy.incrementTiers,
        )
        val moved =
            fakeLiftRepository.getLiftsByIds(listOf(LiftId("lat-pulldown"))).single()
        assertEquals(copy.id, moved.equipmentId)
    }

    @Test
    @DisplayName("Given a band end edit when applied then the neighbor re-tiles with no gap")
    fun bandEndEditRetilesNeighbor() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.addTier()
            awaitItemWith { it.editor.tiers.size == 2 }
            subject.onBandEndChanged(0, "100")
            val retiled = awaitItemWith { it.editor.tiers[0].upperBound == "100" }
            assertEquals("0", retiled.editor.tiers[0].lowerBound)
            assertEquals("100", retiled.editor.tiers[1].lowerBound)
            assertTrue(retiled.editor.tiers[1].openEnded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given an unedited config when initialized then the first band still starts at 0")
    fun existingConfigsDefaultFirstStartToZero() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            val state =
                awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            assertEquals("0", state.editor.tiers[0].lowerBound)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a first-band start edit when applied then the start changes and neighbors still tile")
    fun firstBandStartIsEditable() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.addTier()
            awaitItemWith { it.editor.tiers.size == 2 }
            subject.onBandEndChanged(0, "50")
            awaitItemWith { it.editor.tiers[0].upperBound == "50" }
            subject.onBandStartChanged(0, "10")
            val edited = awaitItemWith { it.editor.tiers[0].lowerBound == "10" }
            assertEquals("10", edited.editor.tiers[0].lowerBound)
            assertEquals("50", edited.editor.tiers[0].upperBound)
            assertEquals("50", edited.editor.tiers[1].lowerBound)
            assertTrue(edited.editor.tiers[1].openEnded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a start edit on a later band when applied then it is ignored")
    fun laterBandStartEditIsIgnored() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.addTier()
            awaitItemWith { it.editor.tiers.size == 2 }
            subject.onBandEndChanged(0, "50")
            awaitItemWith { it.editor.tiers[0].upperBound == "50" }
            subject.onBandStartChanged(1, "99")
            // Ignored edits emit no new state; the derived start still tiles from band 0's end.
            assertEquals("50", subject.uiState.value.editor.tiers[1].lowerBound)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a first-band start of 10 when saved then the stored tiers keep it")
    fun savePersistsCustomFirstBandStart() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.onBandStartChanged(0, "10")
            awaitItemWith { it.editor.tiers[0].lowerBound == "10" }
            subject.save()
            assertTrue(awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        val stored = fakeEquipmentRepository.current.single { it.id == cableId }
        assertEquals(
            listOf(IncrementTier(Weight(10.0, lb), null, Weight(5.0, lb))),
            stored.incrementTiers,
        )
    }

    @Test
    @DisplayName("Given bands when a middle band is removed then lowers re-derive and last stays open")
    fun removeMiddleBandRetiles() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.addTier()
            awaitItemWith { it.editor.tiers.size == 2 }
            subject.onBandEndChanged(0, "100")
            awaitItemWith { it.editor.tiers[0].upperBound == "100" }
            subject.addTier()
            awaitItemWith { it.editor.tiers.size == 3 }
            subject.removeTier(1)
            val retiled = awaitItemWith { it.editor.tiers.size == 2 }
            assertEquals("0", retiled.editor.tiers[0].lowerBound)
            assertEquals("100", retiled.editor.tiers[0].upperBound)
            assertEquals("100", retiled.editor.tiers[1].lowerBound)
            assertTrue(retiled.editor.tiers[1].openEnded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a fork name when save as new then the copy uses the entered name")
    fun saveAsNewUsesEnteredName() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.saveAsNew("Lat stack")
            assertTrue(awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        val copy = fakeEquipmentRepository.current.single { it.id != cableId }
        assertEquals("Lat stack", copy.name)
    }

    @Test
    @DisplayName("Given edited stops when save as new then the copy takes the stops")
    fun saveAsNewForksEditedMicroStops() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.onAddMicro()
            subject.saveAsNew()
            assertTrue(awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        val copy = fakeEquipmentRepository.current.single { it.id != cableId }
        assertEquals(listOf(Weight(5.0, lb)), copy.microIncrements)
    }

    @Test
    @DisplayName("Given rapid stop edits when applied then no update is lost")
    fun backToBackMicroEditsChainOnSourceFlow() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.onAddMicro()
            subject.onAddMicro()
            assertEquals(
                listOf("5", "5"),
                awaitItemWith { it.editor.microStops.size == 2 }.editor.microStops,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given back-to-back stop edits when saved then both stops persist")
    fun backToBackMicroEditsBothPersistOnSave() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            // No suspension between edits: save must validate the draft stops, not
            // the derived uiState list which lags writes by at least one emission.
            subject.onAddMicro()
            subject.onMicroChanged(0, "1.25")
            subject.onAddMicro()
            subject.onMicroChanged(1, "2.5")
            subject.save()
            assertTrue(awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        val stored = fakeEquipmentRepository.getEquipment(cableId)!!
        assertEquals(listOf(Weight(1.25, lb), Weight(2.5, lb)), stored.microIncrements)
    }

    @Test
    @DisplayName(
        "Given save as new without a source lift when saved then the copy is made " +
            "and no lift moves",
    )
    fun saveAsNewWithoutSourceLiftLeavesLifts() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.saveAsNew()
            assertTrue(awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        val rows = fakeEquipmentRepository.current
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.name == "Cables (copy)" })
        val untouched =
            fakeLiftRepository.getLiftsByIds(listOf(LiftId("lat-pulldown"))).single()
        assertEquals(cableId, untouched.equipmentId)
    }

    @Test
    @DisplayName(
        "Given inheritors when initialized then the sheet rows carry ids, names, and enabled flags",
    )
    fun affectedLiftsMapToSheetRows() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()

        subject.uiState.test {
            val state = awaitItemWith { it.affectedLiftCount == 5 }
            assertEquals(
                listOf("cable-crunch", "cable-row", "face-pull", "lat-pulldown", "tricep-pushdown"),
                state.affectedLifts.map { it.id }.sorted(),
            )
            val disabled = state.affectedLifts.single { it.id == "cable-row" }
            assertEquals("Cable row", disabled.name)
            assertFalse(disabled.enabled)
            assertTrue(state.affectedLifts.single { it.id == "lat-pulldown" }.enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName(
        "Given a swap pick when published after begin swap then the lift is reassigned",
    )
    fun swapPickReassignsPendingLift() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()
        subject.requestSwapForLift("lat-pulldown")
        equipmentPickResult.publish(EquipmentId("other-eq"))
        advanceUntilIdle()

        val moved =
            fakeLiftRepository.getLiftsByIds(listOf(LiftId("lat-pulldown"))).single()
        assertEquals(EquipmentId("other-eq"), moved.equipmentId)
    }

    @Test
    @DisplayName("Given a swap request when requested then one navigation is emitted")
    fun swapRequestEmitsNavigation() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()
        subject.requestSwapForLift("lat-pulldown")

        subject.swapNavRequests.test {
            assertEquals("lat-pulldown", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName(
        "Given no swap request when a pick is published then the pick is ignored",
    )
    fun strayPickIsIgnored() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()
        equipmentPickResult.publish(EquipmentId("other-eq"))
        advanceUntilIdle()

        val untouched =
            fakeLiftRepository.getLiftsByIds(listOf(LiftId("lat-pulldown"))).single()
        assertEquals(cableId, untouched.equipmentId)
    }

    @Test
    @DisplayName(
        "Given proposal tiers when initialized then drafts show the proposal and save persists them",
    )
    fun proposalPrefillSeedsDraftsAndSaves() = runTest {
        subject.initialize(
            cableId.value,
            null,
            listOf(
                ProposalTierArgs(0.0, 40.0, 2.5),
                ProposalTierArgs(40.0, null, 5.0),
            ),
        )
        subject.uiState.test {
            val prefilled =
                awaitItemWith {
                    it.equipmentName == "Cables" &&
                        it.editor.tiers.any { tier -> tier.increment == "2.5" }
                }
            assertEquals(
                listOf(
                    IncrementTierDraft("0", "40", "2.5", openEnded = false),
                    IncrementTierDraft("40", null, "5", openEnded = true),
                ),
                prefilled.editor.tiers,
            )
            assertEquals(null, prefilled.editor.validationMessage)
            // Prefill is display-only until the user saves.
            assertEquals(
                openFive,
                fakeEquipmentRepository.getEquipment(cableId)!!.incrementTiers,
            )
            subject.save()
            assertTrue(awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            listOf(
                IncrementTier(Weight(0.0, lb), Weight(40.0, lb), Weight(2.5, lb)),
                IncrementTier(Weight(40.0, lb), null, Weight(5.0, lb)),
            ),
            fakeEquipmentRepository.getEquipment(cableId)!!.incrementTiers,
        )
    }

    @Test
    @DisplayName("Given proposal context when saved then the proposal fingerprint is dismissed")
    fun proposalSaveDismissesFingerprint() = runTest {
        subject.initialize(
            cableId.value,
            null,
            listOf(
                ProposalTierArgs(0.0, 40.0, 2.5),
                ProposalTierArgs(40.0, null, 5.0),
            ),
            ProposalEditContext.PostWorkout(
                proposalId = "proposal-1",
                sampleWeightsLb = listOf(62.5),
            ),
        )
        subject.uiState.test {
            awaitItemWith { it.editor.tiers.any { tier -> tier.increment == "2.5" } }
            assertTrue(fakeProposalPrefs.dismissedFingerprints.isEmpty())
            subject.save()
            assertTrue(awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(setOf("proposal-1"), fakeProposalPrefs.dismissedFingerprints)
    }

    @Test
    @DisplayName("Given sample weights missing the steps when shown then warned band indexes are set")
    fun sampleWeightsMissingStepsWarnBands() = runTest {
        subject.initialize(
            cableId.value,
            null,
            emptyList(),
            ProposalEditContext.PostWorkout(
                proposalId = "proposal-1",
                sampleWeightsLb = listOf(62.5),
            ),
        )
        subject.uiState.test {
            // Catalog 0+ @ 5 misses 62.5 → band 0 warns; saving stays allowed.
            val state =
                awaitItemWith {
                    it.equipmentName == "Cables" && it.editor.warnedTierIndexes.isNotEmpty()
                }
            assertEquals(setOf(0), state.editor.warnedTierIndexes)
            assertEquals(null, state.editor.validationMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given no proposal context when shown then no band warnings")
    fun noContextWarnsNothing() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()
        subject.uiState.test {
            val state = awaitItemWith { it.editor.tiers.isNotEmpty() }
            assertTrue(state.editor.warnedTierIndexes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given equipment with stops when initialized then micro drafts seed")
    fun microStopsSeedFromEquipment() = runTest {
        seedPin()
        subject.initialize(pinId.value)
        advanceUntilIdle()

        subject.uiState.test {
            val state = awaitItemWith { it.equipmentName == "Pin" && it.editor.microStops.isNotEmpty() }
            assertEquals(listOf("5", "10"), state.editor.microStops)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given edited stops when saved then the equipment carries them")
    fun editedMicroSavesToEquipment() = runTest {
        seedPin()
        subject.initialize(pinId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Pin" && it.editor.microStops.isNotEmpty() }
            subject.onMicroChanged(0, "7.5")
            subject.onRemoveMicro(1)
            subject.save()
            assertTrue(awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            listOf(7.5),
            fakeEquipmentRepository.current.single().microIncrements.map { it.amount },
        )
    }

    @Test
    @DisplayName("Given a stop above the largest band when saved then save blocks with a message")
    fun invalidMicroBlocksSave() = runTest {
        seedPin()
        subject.initialize(pinId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Pin" && it.editor.microStops.isNotEmpty() }
            subject.onMicroChanged(0, "20")
            subject.save()
            val state = awaitItemWith { it.editor.validationMessage != null }
            assertTrue(state.editor.validationMessage!!.contains("largest band step"))
            assertFalse(state.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            listOf(5.0, 10.0),
            fakeEquipmentRepository.current.single().microIncrements.map { it.amount },
        )
    }

    @Test
    @DisplayName("Given equipment without stops when saved then it stays adder-free")
    fun emptyMicroSavesAsNoAdder() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.editor.tiers.isNotEmpty() }
            subject.save()
            assertTrue(awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(fakeEquipmentRepository.current.single().microIncrements.isEmpty())
    }

    @Test
    @DisplayName("Given no equipment when saved then a message surfaces and nothing is written")
    fun nullEquipmentSaveSurfacesMessage() = runTest {
        subject.initialize(id = null)
        subject.save()
        // The guard surfaces synchronously on validationOverride; the editor degrades to a
        // seeded, non-saving state instead of silently no-op.
        assertEquals(
            "Nothing to save: no equipment is being edited.",
            subject.validationOverride.value,
        )
        assertEquals(listOf("Cables"), fakeEquipmentRepository.current.map { it.name })
    }

    @Test
    @DisplayName("Given no equipment when save as new then a message surfaces and nothing forks")
    fun nullEquipmentSaveAsNewSurfacesMessage() = runTest {
        subject.initialize(id = null)
        subject.saveAsNew("Copy")
        // The guard surfaces synchronously on validationOverride; the editor degrades to a
        // seeded, non-saving state instead of silently no-op.
        assertEquals(
            "Nothing to fork: no equipment is being edited.",
            subject.validationOverride.value,
        )
        assertEquals(listOf("Cables"), fakeEquipmentRepository.current.map { it.name })
    }

    private val pinId = EquipmentId("pin-loaded-machine")

    private fun seedPin() {
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(
                    id = pinId,
                    name = "Pin",
                    incrementTiers =
                        listOf(IncrementTier(Weight(0.0, lb), null, Weight(15.0, lb))),
                    isCustom = true,
                    microIncrements = listOf(Weight(5.0, lb), Weight(10.0, lb)),
                ),
            ),
        )
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<EquipmentIncrementsUiState>.awaitItemWith(
        predicate: (EquipmentIncrementsUiState) -> Boolean,
    ): EquipmentIncrementsUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun lift(
        id: String,
        name: String,
        enabled: Boolean,
    ): Lift =
        Lift(
            id = LiftId(id),
            name = name,
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = emptySet(),
            incrementTiers = openFive,
            presetTags = emptySet(),
            equipmentId = cableId,
            equipmentName = "Cables",
            enabled = enabled,
        )
}
