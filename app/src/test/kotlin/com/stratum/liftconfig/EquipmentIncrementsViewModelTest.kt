package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.featureflag.FakeFeatureFlagRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentProposalPreferences
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.featureflag.AppFeatureFlag
import com.stratum.domain.featureflag.ObserveEquipmentManagementV2UseCase
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.ForkEquipmentUseCase
import com.stratum.domain.lift.ForkEquipmentWithTiersUseCase
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveEquipmentAffectedLiftsUseCase
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.SetEquipmentIncrementTiersUseCase
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
                lift("lat-pulldown", "Lat pulldown", usesDefaults = true, enabled = true),
                lift("cable-row", "Cable row", usesDefaults = true, enabled = false),
                lift("face-pull", "Face pull", usesDefaults = false, enabled = true),
                lift("tricep-pushdown", "Tricep pushdown", usesDefaults = true, enabled = true),
                lift("cable-crunch", "Cable crunch", usesDefaults = true, enabled = true),
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
                        FakeLoggingPort(),
                    ),
                validateIncrementTiers = ValidateIncrementTiersUseCase(),
                forkEquipmentWithTiers =
                    ForkEquipmentWithTiersUseCase(
                        ForkEquipmentUseCase(
                            fakeEquipmentRepository,
                            ValidateIncrementTiersUseCase(),
                            FakeLoggingPort(),
                        ),
                        SetEquipmentIncrementTiersUseCase(
                            fakeEquipmentRepository,
                            ValidateIncrementTiersUseCase(),
                            FakeLoggingPort(),
                        ),
                        SetLiftEquipmentUseCase(fakeLiftRepository, FakeLoggingPort()),
                    ),
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
                observeEquipmentManagementV2 =
                    ObserveEquipmentManagementV2UseCase(fakeFeatureFlags),
            )
    }

    @Test
    @DisplayName(
        "Given inheritors including disabled when initialized then count and names exclude overrides",
    )
    fun affectedCountIncludesDisabledExcludesOverrides() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()

        subject.uiState.test {
            val state = awaitItemWith { it.equipmentName == "Cables" && it.affectedLiftCount > 0 }
            // lat, cable-row (disabled), tricep, cable-crunch — face-pull has override
            assertEquals(4, state.affectedLiftCount)
            assertEquals(
                listOf("Cable crunch", "Cable row", "Lat pulldown", "Tricep pushdown"),
                state.affectedLiftNames,
            )
            assertTrue(state.tiers.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("formatAffectsBanner helpers: preview limit is 3 names")
    fun affectedNamesOrderedForPreview() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()
        subject.uiState.test {
            val state = awaitItemWith { it.affectedLiftCount == 4 }
            assertEquals(4, state.affectedLiftNames.size)
            // First 3 alphabetical for "+1 more" UI path
            assertEquals(
                listOf("Cable crunch", "Cable row", "Lat pulldown"),
                state.affectedLiftNames.take(3),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given the flag off when initialized then the v2 equipment UI is disabled")
    fun equipmentV2DisabledByDefault() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()

        subject.uiState.test {
            assertFalse(awaitItemWith { it.tiers.isNotEmpty() }.equipmentV2Enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given the flag on when initialized then the v2 equipment UI is enabled")
    fun equipmentV2EnabledWhenFlagSet() = runTest {
        fakeFeatureFlags.setFlag(AppFeatureFlag.EQUIPMENT_MANAGEMENT_V2, true)
        subject.initialize(cableId.value)
        advanceUntilIdle()

        subject.uiState.test {
            assertTrue(awaitItemWith { it.equipmentV2Enabled }.equipmentV2Enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName(
        "Given edited tiers when save as new from a lift then the copy takes the edits, " +
            "the shared tiers are untouched, and the lift moves to the copy",
    )
    fun saveAsNewForksEditsAndReassignsSourceLift() = runTest {
        fakeFeatureFlags.setFlag(AppFeatureFlag.EQUIPMENT_MANAGEMENT_V2, true)
        subject.initialize(cableId.value, "lat-pulldown")
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.tiers.isNotEmpty() }
            subject.onTierIncrementChanged(0, "10")
            subject.saveAsNew()
            assertTrue(awaitItemWith { it.saveSucceeded }.saveSucceeded)
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
    @DisplayName(
        "Given save as new without a source lift when saved then the copy is made " +
            "and no lift moves",
    )
    fun saveAsNewWithoutSourceLiftLeavesLifts() = runTest {
        subject.initialize(cableId.value)
        subject.uiState.test {
            awaitItemWith { it.equipmentName == "Cables" && it.tiers.isNotEmpty() }
            subject.saveAsNew()
            assertTrue(awaitItemWith { it.saveSucceeded }.saveSucceeded)
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
            val state = awaitItemWith { it.affectedLiftCount == 4 }
            assertEquals(
                listOf("cable-crunch", "cable-row", "lat-pulldown", "tricep-pushdown"),
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
                        it.tiers.any { tier -> tier.increment == "2.5" }
                }
            assertEquals(
                listOf(
                    IncrementTierDraft("0", "40", "2.5", openEnded = false),
                    IncrementTierDraft("40", null, "5", openEnded = true),
                ),
                prefilled.tiers,
            )
            assertEquals(null, prefilled.validationMessage)
            // Prefill is display-only until the user saves.
            assertEquals(
                openFive,
                fakeEquipmentRepository.getEquipment(cableId)!!.incrementTiers,
            )
            subject.save()
            assertTrue(awaitItemWith { it.saveSucceeded }.saveSucceeded)
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
            ProposalEditContext(
                proposalId = "proposal-1",
                sampleWeightsLb = listOf(62.5),
            ),
        )
        subject.uiState.test {
            awaitItemWith { it.tiers.any { tier -> tier.increment == "2.5" } }
            assertTrue(fakeProposalPrefs.dismissedFingerprints.isEmpty())
            subject.save()
            assertTrue(awaitItemWith { it.saveSucceeded }.saveSucceeded)
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
            ProposalEditContext(
                proposalId = "proposal-1",
                sampleWeightsLb = listOf(62.5),
            ),
        )
        subject.uiState.test {
            // Catalog 0+ @ 5 misses 62.5 → band 0 warns; saving stays allowed.
            val state =
                awaitItemWith {
                    it.equipmentName == "Cables" && it.warnedTierIndexes.isNotEmpty()
                }
            assertEquals(setOf(0), state.warnedTierIndexes)
            assertEquals(null, state.validationMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given no proposal context when shown then no band warnings")
    fun noContextWarnsNothing() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()
        subject.uiState.test {
            val state = awaitItemWith { it.tiers.isNotEmpty() }
            assertTrue(state.warnedTierIndexes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
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
        usesDefaults: Boolean,
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
            usesEquipmentDefaultIncrements = usesDefaults,
            enabled = enabled,
        )
}
