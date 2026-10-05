package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.featureflag.FakeFeatureFlagRepository
import com.stratum.core.testing.lift.FakeIncrementScanPort
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
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
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.ScanIncrementTiersUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.ValidateMicroIncrementsUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.workout.ui.ProposalEditContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Prefill coverage for the per-lift override editor: proposal bands seed the drafts and
 * save persists them, while legacy init still shows catalog tiers
 * (UNIT_TESTING.md §1 — fakes, no MockK).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class LiftIncrementsViewModelTest {

    private val lb = Weight.WeightUnit.Lb
    private val latId = LiftId("lat-pulldown")
    private val catalogTiers =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

    private val fakeLiftRepository = FakeLiftRepository()
    private val fakeEquipmentRepository = FakeEquipmentRepository()
    private val fakeProposalPrefs = FakeEquipmentProposalPreferences()
    private lateinit var subject: LiftIncrementsViewModel

    @BeforeEach
    fun setup() {
        fakeLiftRepository.seedLifts(
            listOf(
                Lift(
                    id = latId,
                    name = "Lat pulldown",
                    movementPatterns = setOf(MovementPattern.VerticalPull),
                    primaryMuscles = setOf(MuscleGroup.Back),
                    secondaryMuscles = emptySet(),
                    incrementTiers = catalogTiers,
                    presetTags = emptySet(),
                    usesEquipmentDefaultIncrements = false,
                    enabled = true,
                ),
            ),
        )
        subject =
            LiftIncrementsViewModel(
                observeLiftCatalog = ObserveLiftCatalogUseCase(fakeLiftRepository),
                validateIncrementTiers = ValidateIncrementTiersUseCase(),
                validateMicroIncrements = ValidateMicroIncrementsUseCase(),
                dismissEquipmentProposal =
                    DismissEquipmentProposalUseCase(
                        fakeProposalPrefs,
                        FakeLoggingPort(),
                    ),
                forkEquipmentWithTiers =
                    testForkEquipmentWithTiers(fakeEquipmentRepository, fakeLiftRepository),
                observeWeightDisplayPreferences =
                    ObserveWeightDisplayPreferencesUseCase(
                        FakeWeightDisplayPreferencesRepository(),
                    ),
                scanDelegate =
                    IncrementScanDelegate(
                        ScanIncrementTiersUseCase(
                            FakeIncrementScanPort(),
                            ValidateIncrementTiersUseCase(),
                            ValidateMicroIncrementsUseCase(),
                            FakeLoggingPort(),
                        ),
                        ObserveIncrementsAiScanUseCase(FakeFeatureFlagRepository()),
                    ),
            )
    }

    @Test
    @DisplayName("Given sample weights missing the steps when shown then warned band indexes are set")
    fun sampleWeightsMissingStepsWarnBands() = runTest {
        subject.initialize(
            latId.value,
            emptyList(),
            ProposalEditContext.PostWorkout(
                proposalId = "proposal-9",
                sampleWeightsLb = listOf(62.5),
            ),
        )
        subject.uiState.test {
            // Catalog 0+ @ 5 misses 62.5 → band 0 warns; saving stays allowed.
            val state =
                awaitItemWith {
                    it.liftName == "Lat pulldown" && it.editor.warnedTierIndexes.isNotEmpty()
                }
            assertEquals(setOf(0), state.editor.warnedTierIndexes)
            assertNull(state.editor.validationMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given no proposal tiers when initialized then catalog tiers are shown")
    fun legacyInitShowsCatalogTiers() = runTest {
        subject.initialize(latId.value)
        subject.uiState.test {
            val state =
                awaitItemWith {
                    it.liftName == "Lat pulldown" && it.editor.tiers.isNotEmpty()
                }
            assertEquals(
                listOf(IncrementTierDraft("0", null, "5", openEnded = true)),
                state.editor.tiers,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given lift with stops when initialized then micro drafts seed from the effective stops")
    fun microSeedsFromEffectiveStops() = runTest {
        seedLift(
            micro = listOf(Weight(5.0, lb), Weight(10.0, lb)),
            usesDefaultMicro = false,
        )
        subject.initialize(latId.value)
        subject.uiState.test {
            val state = awaitItemWith { it.liftName == "Lat pulldown" && it.editor.microStops.isNotEmpty() }
            assertEquals(listOf("5", "10"), state.editor.microStops)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a band end edit when applied then the neighbor re-tiles with no gap")
    fun bandEndEditRetilesNeighbor() = runTest {
        subject.initialize(latId.value)
        subject.uiState.test {
            awaitItemWith { it.liftName == "Lat pulldown" && it.editor.tiers.isNotEmpty() }
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
    @DisplayName(
        "Given an equipment-backed lift when save as new with a name then the copy takes " +
            "the edits and the lift moves to the copy",
    )
    fun saveAsNewForksAndReassignsLift() = runTest {
        val cableId = EquipmentId("cable")
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(
                    id = cableId,
                    name = "Cables",
                    incrementTiers = catalogTiers,
                    isCustom = false,
                ),
            ),
        )
        fakeLiftRepository.seedLifts(
            listOf(
                Lift(
                    id = latId,
                    name = "Lat pulldown",
                    movementPatterns = setOf(MovementPattern.VerticalPull),
                    primaryMuscles = setOf(MuscleGroup.Back),
                    secondaryMuscles = emptySet(),
                    incrementTiers = catalogTiers,
                    presetTags = emptySet(),
                    usesEquipmentDefaultIncrements = false,
                    equipmentId = cableId,
                    equipmentName = "Cables",
                    enabled = true,
                ),
            ),
        )
        subject.initialize(latId.value)
        subject.uiState.test {
            val ready =
                awaitItemWith {
                    it.liftName == "Lat pulldown" && it.forkSourceId == cableId.value
                }
            assertEquals("Cables (copy)", ready.forkSuggestion)
            subject.onTierIncrementChanged(0, "10")
            subject.saveAsNew("Lat stack")
            assertEquals(true, awaitItemWith { it.editor.saveSucceeded }.editor.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        val copy = fakeEquipmentRepository.current.single { it.id != cableId }
        assertEquals("Lat stack", copy.name)
        assertEquals(
            listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb))),
            copy.incrementTiers,
        )
        val moved = fakeLiftRepository.getLiftsByIds(listOf(latId)).single()
        assertEquals(copy.id, moved.equipmentId)
    }

    @Test
    @DisplayName("Given fallback bands with no equipment when shown then the fork entry stays hidden")
    fun fallbackHidesFork() = runTest {
        subject.initialize(latId.value)
        subject.uiState.test {
            val state =
                awaitItemWith {
                    it.liftName == "Lat pulldown" && it.editor.tiers.isNotEmpty()
                }
            assertNull(state.forkSourceId)
            subject.saveAsNew("Lat stack")
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(fakeEquipmentRepository.current.isEmpty())
    }

    private fun seedLift(micro: List<Weight>, usesDefaultMicro: Boolean) {
        fakeLiftRepository.seedLifts(
            listOf(
                Lift(
                    id = latId,
                    name = "Lat pulldown",
                    movementPatterns = setOf(MovementPattern.VerticalPull),
                    primaryMuscles = setOf(MuscleGroup.Back),
                    secondaryMuscles = emptySet(),
                    incrementTiers = catalogTiers,
                    presetTags = emptySet(),
                    usesEquipmentDefaultIncrements = false,
                    microIncrements = micro,
                    usesEquipmentDefaultMicro = usesDefaultMicro,
                    enabled = true,
                ),
            ),
        )
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<LiftIncrementsUiState>.awaitItemWith(
        predicate: (LiftIncrementsUiState) -> Boolean,
    ): LiftIncrementsUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
