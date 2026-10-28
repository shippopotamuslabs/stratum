package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.AddCustomEquipmentUseCase
import com.stratum.domain.lift.DeleteCustomEquipmentUseCase
import com.stratum.domain.lift.GetEquipmentDeleteImpactUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.ForkEquipmentUseCase
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.EquipmentPickResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ManageEquipmentViewModelTest {

    private val lb = Weight.WeightUnit.Lb
    private val fakeEquipmentRepository = FakeEquipmentRepository()
    private val equipmentPickResult = EquipmentPickResult()
    private lateinit var subject: ManageEquipmentViewModel

    @BeforeEach
    fun setup() {
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(
                    id = EquipmentId("cable"),
                    name = "Cables",
                    incrementTiers =
                        listOf(
                            IncrementTier(Weight(0.0, lb), Weight(50.0, lb), Weight(5.0, lb)),
                            IncrementTier(Weight(50.0, lb), Weight(100.0, lb), Weight(10.0, lb)),
                            IncrementTier(Weight(100.0, lb), null, Weight(15.0, lb)),
                        ),
                ),
                Equipment(
                    id = EquipmentId("bodyweight"),
                    name = "Bodyweight",
                    incrementTiers = emptyList(),
                ),
                Equipment(
                    id = EquipmentId("default"),
                    name = "Default",
                    incrementTiers =
                        listOf(
                            IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb)),
                        ),
                ),
                Equipment(
                    id = EquipmentId("dumbbell"),
                    name = "Dumbbells",
                    incrementTiers =
                        listOf(
                            IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb)),
                        ),
                ),
            ),
        )
        val logging = FakeLoggingPort()
        subject =
            ManageEquipmentViewModel(
                observeEquipmentCatalog = ObserveEquipmentCatalogUseCase(fakeEquipmentRepository),
                addCustomEquipment =
                    AddCustomEquipmentUseCase(
                        fakeEquipmentRepository,
                        logging,
                    ),
                forkEquipment =
                    ForkEquipmentUseCase(
                        fakeEquipmentRepository,
                        logging,
                    ),
                deleteCustomEquipment =
                    DeleteCustomEquipmentUseCase(
                        fakeEquipmentRepository,
                        logging,
                    ),
                getDeleteImpact =
                    GetEquipmentDeleteImpactUseCase(
                        fakeEquipmentRepository,
                        FakeLiftRepository(),
                        FakeProgramRepository(),
                    ),
                equipmentPickResult = equipmentPickResult,
            )
    }

    @Test
    @DisplayName("Given catalog with bodyweight and default when observed then both are omitted")
    fun omitsHiddenSeedEquipmentFromRows() = runTest {
        subject.uiState.test {
            val state = awaitCatalogState()
            assertEquals(listOf("cable", "dumbbell"), state.rows.map { it.id })
            assertFalse(state.rows.any { it.name == "Bodyweight" })
            assertFalse(state.rows.any { it.name == "Default" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given multi-tier equipment when observed then all bands map to chips")
    fun mapsAllTiersToChips() = runTest {
        subject.uiState.test {
            val cables = awaitCatalogState().rows.first { it.id == "cable" }
            assertEquals(
                listOf(
                    EquipmentTierChipUi("0", "50", "5"),
                    EquipmentTierChipUi("50", "100", "10"),
                    EquipmentTierChipUi("100", null, "15"),
                ),
                cables.tierChips,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given open-ended single tier when observed then chip has null upper bound")
    fun mapsOpenEndedSingleTier() = runTest {
        subject.uiState.test {
            val dumbbells = awaitCatalogState().rows.first { it.id == "dumbbell" }
            assertEquals(
                listOf(EquipmentTierChipUi("0", null, "2.5")),
                dumbbells.tierChips,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given selection when selectEquipment then pick is published")
    fun selectEquipmentPublishesPick() = runTest {
        equipmentPickResult.result.test {
            val first = awaitItem()
            if (first != null) {
                // Consume stale; not expected for a fresh EquipmentPickResult.
            }
            subject.selectEquipment("cable")
            val pick = if (first?.equipmentId == EquipmentId("cable")) first else awaitItem()
            assertEquals(EquipmentId("cable"), pick?.equipmentId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given valid name when create equipment then draft clears and row appears")
    fun createEquipmentAddsRow() = runTest {
        subject.onNameDraftChange("Smith Machine")
        subject.uiState.test {
            var state = awaitCatalogState()
            if (state.nameDraft != "Smith Machine") {
                state = awaitItem()
            }
            assertEquals("Smith Machine", state.nameDraft)

            subject.events.test {
                subject.onCreateEquipment()
                advanceUntilIdle()
                val event = awaitItem() as ManageEquipmentEvent.Created
                assertTrue(event.equipmentId.isNotBlank())
                cancelAndIgnoreRemainingEvents()
            }

            state = expectMostRecentItem()
            assertEquals("", state.nameDraft)
            assertFalse(state.showCreateSheet)
            assertTrue(state.rows.any { it.name == "Smith Machine" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given seed row when start fork then draft prefilled with the numbered unique name")
    fun startForkPrefillsName() = runTest {
        subject.uiState.test {
            awaitCatalogState()
            subject.onStartFork("cable")
            val state = awaitItem()
            assertEquals("cable", state.forkingFromId)
            assertEquals("Cables", state.forkingFromName)
            assertEquals("Cables (1)", state.forkNameDraft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given fork sheet when confirm then custom row created with parent tiers")
    fun confirmForkCreatesCustomCopy() = runTest {
        val parentTiers =
            fakeEquipmentRepository.getEquipment(EquipmentId("cable"))!!.incrementTiers
        subject.uiState.test {
            awaitCatalogState()
            subject.onStartFork("cable")
            awaitItem() // fork open
            subject.onForkNameDraftChange("Life Fitness cables")
            awaitItem() // name change

            var createdId: String? = null
            subject.events.test {
                subject.onConfirmFork()
                advanceUntilIdle()
                val event = awaitItem() as ManageEquipmentEvent.Forked
                createdId = event.equipmentId
                cancelAndIgnoreRemainingEvents()
            }

            val state = expectMostRecentItem()
            assertNotNull(createdId)
            assertNull(state.forkingFromId)
            assertEquals("", state.forkNameDraft)
            assertTrue(state.rows.any { it.name == "Life Fitness cables" })
            assertTrue(state.userMessage?.contains("Life Fitness cables") == true)

            val forked = fakeEquipmentRepository.getEquipment(EquipmentId(createdId!!))!!
            assertTrue(forked.isCustom)
            assertEquals(parentTiers, forked.incrementTiers)
            // Parent untouched.
            assertEquals(
                parentTiers,
                fakeEquipmentRepository.getEquipment(EquipmentId("cable"))!!.incrementTiers,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given open fork when dismiss then draft cleared")
    fun dismissForkClearsDraft() = runTest {
        subject.uiState.test {
            awaitCatalogState()
            subject.onStartFork("dumbbell")
            awaitItem()
            subject.onDismissFork()
            val state = awaitItem()
            assertNull(state.forkingFromId)
            assertEquals("", state.forkNameDraft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given custom row when start delete then dialog arms with its name")
    fun startDeleteArmsDialogForCustomRow() = runTest {
        val id = fakeEquipmentRepository.addCustomEquipment("Old Stack")
        subject.uiState.test {
            awaitCatalogState()
            subject.onStartDelete(id.value)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals(id.value, state.pendingDelete?.id)
            assertEquals("Old Stack", state.pendingDelete?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given visible seed row when start delete then dialog arms (STR-105)")
    fun startDeleteArmsForVisibleSeedRow() = runTest {
        subject.uiState.test {
            awaitCatalogState()
            subject.onStartDelete("cable")
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals("cable", state.pendingDelete?.id)
            assertTrue(state.rows.first { it.id == "cable" }.canDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given hidden system row when start delete then dialog stays closed")
    fun startDeleteIgnoredForHiddenRow() = runTest {
        subject.uiState.test {
            awaitCatalogState()
            subject.onStartDelete("bodyweight")
            advanceUntilIdle()
            assertNull(subject.uiState.value.pendingDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Affected delete needs a replacement pick before confirm (STR-105)")
    fun affectedDeleteRequiresReplacementPick() = runTest {
        // Seed an affected lift through a dedicated VM wired to a lift fake.
        val liftRepo = FakeLiftRepository()
        val programRepo = FakeProgramRepository()
        val equipmentRepo = FakeEquipmentRepository(liftRepository = liftRepo, programRepository = programRepo)
        val deletedId = equipmentRepo.addCustomEquipment("Old Stack")
        val replacementId = equipmentRepo.addCustomEquipment("New Stack")
        liftRepo.seedLifts(
            listOf(
                com.stratum.domain.lift.Lift(
                    id = com.stratum.domain.lift.identity.LiftId("lift-a"),
                    name = "Lift A",
                    movementPatterns = setOf(com.stratum.domain.lift.MovementPattern.HorizontalPress),
                    primaryMuscles = setOf(com.stratum.domain.onboarding.MuscleGroup.Chest),
                    secondaryMuscles = emptySet(),
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    equipmentId = deletedId,
                    enabled = true,
                    isCustom = true,
                ),
            ),
        )
        val logging = FakeLoggingPort()
        val vm =
            ManageEquipmentViewModel(
                observeEquipmentCatalog = ObserveEquipmentCatalogUseCase(equipmentRepo),
                addCustomEquipment =
                    AddCustomEquipmentUseCase(
                        equipmentRepo,
                        logging,
                    ),
                forkEquipment =
                    ForkEquipmentUseCase(
                        equipmentRepo,
                        logging,
                    ),
                deleteCustomEquipment = DeleteCustomEquipmentUseCase(equipmentRepo, logging),
                getDeleteImpact = GetEquipmentDeleteImpactUseCase(equipmentRepo, liftRepo, programRepo),
                equipmentPickResult = EquipmentPickResult(),
            )
        vm.uiState.test {
            // Wait for the catalog to contain both rows.
            var state = awaitItem()
            while (state.rows.size < 2) state = awaitItem()
            vm.onStartDelete(deletedId.value)
            advanceUntilIdle()
            state = expectMostRecentItem()
            assertEquals(listOf("Lift A"), state.pendingDelete?.affectedLiftNames)
            assertEquals(listOf(replacementId.value), state.pendingDelete?.candidates?.map { it.id })
            // Confirm without a pick is a no-op.
            vm.onConfirmDelete()
            advanceUntilIdle()
            assertFalse(equipmentRepo.getEquipment(deletedId)!!.isDeleted)
            vm.onSelectReplacement(replacementId.value)
            vm.onConfirmDelete()
            advanceUntilIdle()
            assertTrue(equipmentRepo.getEquipment(deletedId)!!.isDeleted)
            assertEquals(replacementId, liftRepo.currentLifts.single().equipmentId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Cancel leaves state untouched (STR-105)")
    fun cancelDeleteChangesNothing() = runTest {
        val id = fakeEquipmentRepository.addCustomEquipment("Old Stack")
        subject.uiState.test {
            awaitCatalogState()
            subject.onStartDelete(id.value)
            advanceUntilIdle()
            expectMostRecentItem()
            subject.onDismissDelete()
            val state = awaitItem()
            assertNull(state.pendingDelete)
            assertTrue(state.rows.any { it.id == id.value })
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(fakeEquipmentRepository.getEquipment(id)!!.isDeleted)
    }

    @Test
    @DisplayName("Given armed delete when confirm then row leaves catalog and message shows")
    fun confirmDeleteTombstonesRow() = runTest {
        val id = fakeEquipmentRepository.addCustomEquipment("Old Stack")
        subject.uiState.test {
            awaitCatalogState()
            subject.onStartDelete(id.value)
            advanceUntilIdle()
            expectMostRecentItem() // dialog armed
            subject.onConfirmDelete()
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertNull(state.pendingDelete)
            assertTrue(state.rows.none { it.id == id.value })
            assertTrue(state.userMessage?.contains("Old Stack") == true)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fakeEquipmentRepository.getEquipment(id)!!.isDeleted)
    }

    @Test
    @DisplayName("Given armed delete when dismiss then dialog closes and row stays")
    fun dismissDeleteKeepsRow() = runTest {
        val id = fakeEquipmentRepository.addCustomEquipment("Old Stack")
        subject.uiState.test {
            awaitCatalogState()
            subject.onStartDelete(id.value)
            advanceUntilIdle()
            expectMostRecentItem() // dialog armed
            subject.onDismissDelete()
            val state = awaitItem()
            assertNull(state.pendingDelete)
            assertTrue(state.rows.any { it.id == id.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * [stateIn] may emit the empty seed first, then the catalog. Accept either order.
     */
    private suspend fun app.cash.turbine.ReceiveTurbine<ManageEquipmentUiState>.awaitCatalogState():
        ManageEquipmentUiState {
        val first = awaitItem()
        return if (first.rows.isEmpty()) awaitItem() else first
    }
}
