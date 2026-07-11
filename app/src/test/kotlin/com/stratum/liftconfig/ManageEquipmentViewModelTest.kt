package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.domain.lift.AddCustomEquipmentUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
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
                    id = EquipmentId("dumbbell"),
                    name = "Dumbbells",
                    incrementTiers =
                        listOf(
                            IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb)),
                        ),
                ),
            ),
        )
        subject =
            ManageEquipmentViewModel(
                observeEquipmentCatalog = ObserveEquipmentCatalogUseCase(fakeEquipmentRepository),
                addCustomEquipment =
                    AddCustomEquipmentUseCase(
                        fakeEquipmentRepository,
                        ValidateIncrementTiersUseCase(),
                        FakeLoggingPort(),
                    ),
                equipmentPickResult = equipmentPickResult,
            )
    }

    @Test
    @DisplayName("Given catalog with bodyweight when observed then bodyweight is omitted")
    fun omitsBodyweightFromRows() = runTest {
        subject.uiState.test {
            val state = awaitCatalogState()
            assertEquals(listOf("cable", "dumbbell"), state.rows.map { it.id })
            assertFalse(state.rows.any { it.name == "Bodyweight" })
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
            // StateFlow may already hold a prior pick in theory; seed is null.
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
            // Draft may already be applied (Unconfined) or arrive as a later emission.
            if (state.nameDraft != "Smith Machine") {
                state = awaitItem()
            }
            assertEquals("Smith Machine", state.nameDraft)

            var createdId: String? = null
            subject.onCreateEquipment { createdId = it }
            advanceUntilIdle()

            state = expectMostRecentItem()
            assertTrue(createdId != null)
            assertEquals("", state.nameDraft)
            assertTrue(state.rows.any { it.name == "Smith Machine" })
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
