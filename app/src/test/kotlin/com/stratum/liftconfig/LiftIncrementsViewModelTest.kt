package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentProposalPreferences
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.SetLiftIncrementTiersUseCase
import com.stratum.domain.lift.ValidateMicroIncrementsUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs
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
                setLiftIncrementTiers =
                    SetLiftIncrementTiersUseCase(
                        fakeLiftRepository,
                        ValidateIncrementTiersUseCase(),
                        ValidateMicroIncrementsUseCase(),
                        FakeLoggingPort(),
                    ),
                validateIncrementTiers = ValidateIncrementTiersUseCase(),
                dismissEquipmentProposal =
                    DismissEquipmentProposalUseCase(
                        fakeProposalPrefs,
                        FakeLoggingPort(),
                    ),
                observeWeightDisplayPreferences =
                    ObserveWeightDisplayPreferencesUseCase(
                        FakeWeightDisplayPreferencesRepository(),
                    ),
            )
    }

    @Test
    @DisplayName("Given proposal tiers when initialized then drafts show the proposal and save persists them")
    fun proposalPrefillSeedsDraftsAndSaves() = runTest {
        subject.initialize(
            latId.value,
            listOf(ProposalTierArgs(0.0, null, 2.5)),
        )
        subject.uiState.test {
            val prefilled =
                awaitItemWith {
                    it.liftName == "Lat pulldown" &&
                        it.tiers.any { tier -> tier.increment == "2.5" }
                }
            assertEquals(
                listOf(IncrementTierDraft("0", null, "2.5", openEnded = true)),
                prefilled.tiers,
            )
            assertNull(prefilled.validationMessage)
            // Prefill is display-only until the user saves.
            assertEquals(
                catalogTiers,
                fakeLiftRepository.getLiftsByIds(listOf(latId)).single().incrementTiers,
            )
            subject.save()
            assertEquals(true, awaitItemWith { it.saveSucceeded }.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(
            listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb))),
            fakeLiftRepository.getLiftsByIds(listOf(latId)).single().incrementTiers,
        )
    }

    @Test
    @DisplayName("Given proposal context when saved then the proposal fingerprint is dismissed")
    fun proposalSaveDismissesFingerprint() = runTest {
        subject.initialize(
            latId.value,
            listOf(ProposalTierArgs(0.0, null, 2.5)),
            ProposalEditContext.PostWorkout(
                proposalId = "proposal-9",
                sampleWeightsLb = listOf(62.5),
            ),
        )
        subject.uiState.test {
            awaitItemWith { it.tiers.any { tier -> tier.increment == "2.5" } }
            assertTrue(fakeProposalPrefs.dismissedFingerprints.isEmpty())
            subject.save()
            assertEquals(true, awaitItemWith { it.saveSucceeded }.saveSucceeded)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(setOf("proposal-9"), fakeProposalPrefs.dismissedFingerprints)
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
                    it.liftName == "Lat pulldown" && it.warnedTierIndexes.isNotEmpty()
                }
            assertEquals(setOf(0), state.warnedTierIndexes)
            assertNull(state.validationMessage)
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
                    it.liftName == "Lat pulldown" && it.tiers.isNotEmpty()
                }
            assertEquals(
                listOf(IncrementTierDraft("0", null, "5", openEnded = true)),
                state.tiers,
            )
            cancelAndIgnoreRemainingEvents()
        }
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
