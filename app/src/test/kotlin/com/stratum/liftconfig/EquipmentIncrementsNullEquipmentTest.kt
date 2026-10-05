package com.stratum.liftconfig

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
import com.stratum.domain.lift.ObserveEquipmentAffectedLiftsUseCase
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.ScanIncrementTiersUseCase
import com.stratum.domain.lift.SetEquipmentIncrementTiersUseCase
import com.stratum.domain.lift.ValidateMicroIncrementsUseCase
import com.stratum.domain.lift.SetLiftEquipmentUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.feature.workout.ui.ProposalTierArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Null-equipment mode of [EquipmentIncrementsViewModel] (STR-86): the editor degrades to a
 * seeded, non-saving state when launched with no equipment.
 *
 * Uses a shared [StandardTestDispatcher] for both `Dispatchers.Main` and `runTest` so that
 * the ViewModel's `stateIn(WhileSubscribed)` sharing (which runs on Main) advances with the
 * test scheduler. The class-wide [com.stratum.core.testing.MainDispatcherRule] uses an
 * unconfined dispatcher whose resumptions are invisible to `runTest`, which stalls the
 * sharing when the equipment id does not change (null -> null).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentIncrementsNullEquipmentTest {

    private val testDispatcher = StandardTestDispatcher()
    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")

    private val fakeEquipmentRepository = FakeEquipmentRepository()
    private val fakeLiftRepository = FakeLiftRepository()
    private val fakeFeatureFlags = FakeFeatureFlagRepository()
    private val fakeProposalPrefs = FakeEquipmentProposalPreferences()
    private val equipmentPickResult = EquipmentPickResult()
    private lateinit var subject: EquipmentIncrementsViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeFeatureFlags.clear()
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(
                    id = cableId,
                    name = "Cables",
                    incrementTiers =
                        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb))),
                    isCustom = false,
                ),
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

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("Given no equipment when initialized then seeded tiers show")
    fun nullEquipmentSeedsFromProposalArgs() =
        runTest(testDispatcher) {
            val collectJob = launch { subject.uiState.collect { } }
            subject.initialize(
                id = null,
                initialTiersLb =
                    listOf(
                        ProposalTierArgs(lowerBoundLb = 0.0, upperBoundLb = null, incrementLb = 2.5),
                    ),
            )
            testScheduler.advanceUntilIdle()
            val state = subject.uiState.value
            assertEquals("", state.equipmentId)
            assertEquals(listOf("0"), state.editor.tiers.map { it.lowerBound })
            assertEquals(listOf("2.5"), state.editor.tiers.map { it.increment })
            collectJob.cancel()
        }

    @Test
    @DisplayName("Given no equipment when saved then a message surfaces and nothing is written")
    fun nullEquipmentSaveSurfacesMessage() =
        runTest(testDispatcher) {
            val collectJob = launch { subject.uiState.collect { } }
            subject.initialize(id = null)
            testScheduler.advanceUntilIdle()
            subject.save()
            testScheduler.advanceUntilIdle()
            val state = subject.uiState.value
            assertEquals(
                "Nothing to save: no equipment is being edited.",
                state.editor.validationMessage,
            )
            assertFalse(state.editor.saveSucceeded)
            collectJob.cancel()

            assertEquals(listOf("Cables"), fakeEquipmentRepository.current.map { it.name })
        }

    @Test
    @DisplayName("Given no equipment when save as new then a message surfaces and nothing forks")
    fun nullEquipmentSaveAsNewSurfacesMessage() =
        runTest(testDispatcher) {
            val collectJob = launch { subject.uiState.collect { } }
            subject.initialize(id = null)
            testScheduler.advanceUntilIdle()
            subject.saveAsNew("Copy")
            testScheduler.advanceUntilIdle()
            val state = subject.uiState.value
            assertEquals(
                "Nothing to fork: no equipment is being edited.",
                state.editor.validationMessage,
            )
            assertFalse(state.editor.saveSucceeded)
            collectJob.cancel()

            assertEquals(listOf("Cables"), fakeEquipmentRepository.current.map { it.name })
        }
}
