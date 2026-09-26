package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.featureflag.FakeFeatureFlagRepository
import com.stratum.core.testing.lift.FakeIncrementScanPort
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentProposalPreferences
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.featureflag.AppFeatureFlag
import com.stratum.domain.featureflag.ObserveEquipmentManagementV2UseCase
import com.stratum.domain.featureflag.ObserveIncrementsAiScanUseCase
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.ForkEquipmentUseCase
import com.stratum.domain.lift.ForkEquipmentWithTiersUseCase
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.ObserveEquipmentAffectedLiftsUseCase
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.ScanIncrementTiersUseCase
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.ScannedTierRow
import com.stratum.domain.lift.ScannedTiers
import com.stratum.domain.lift.SetEquipmentIncrementTiersUseCase
import com.stratum.domain.lift.SetLiftEquipmentUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.ValidateMicroIncrementsUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Scan-state coverage for the shared-defaults editor: the launch flag gates the
 * affordance, a successful scan prefills bands and adder stops without saving,
 * and a failure keeps drafts with a message (UNIT_TESTING.md §1 — fakes, no MockK).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class EquipmentIncrementsScanTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val openFive =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))
    private val photo = ScanPhoto(byteArrayOf(1, 2, 3), "image/jpeg")

    private val fakeEquipmentRepository = FakeEquipmentRepository()
    private val fakeLiftRepository = FakeLiftRepository()
    private val fakeFeatureFlags = FakeFeatureFlagRepository()
    private val fakeProposalPrefs = FakeEquipmentProposalPreferences()
    private val fakeScanPort = FakeIncrementScanPort()
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
                    ForkEquipmentWithTiersUseCase(
                        ForkEquipmentUseCase(
                            fakeEquipmentRepository,
                            ValidateIncrementTiersUseCase(),
                            ValidateMicroIncrementsUseCase(),
                            FakeLoggingPort(),
                        ),
                        SetEquipmentIncrementTiersUseCase(
                            fakeEquipmentRepository,
                            ValidateIncrementTiersUseCase(),
                            ValidateMicroIncrementsUseCase(),
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
                    ObserveWeightDisplayPreferencesUseCase(
                        com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository(),
                    ),
                observeEquipmentManagementV2 =
                    ObserveEquipmentManagementV2UseCase(fakeFeatureFlags),
                observeIncrementsAiScan =
                    ObserveIncrementsAiScanUseCase(fakeFeatureFlags),
                scanDelegate =
                    IncrementScanDelegate(
                        ScanIncrementTiersUseCase(
                            fakeScanPort,
                            ValidateIncrementTiersUseCase(),
                            ValidateMicroIncrementsUseCase(),
                        ),
                    ),
            )
    }

    @Test
    @DisplayName("Given the flag off when shown then no scan affordance and scan is a no-op")
    fun flagOffHidesScanAffordance() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()

        subject.uiState.test {
            val state =
                awaitItemWith { it.equipmentName == "Cables" && it.tiers.isNotEmpty() }
            assertFalse(state.scanAvailable)
            assertFalse(state.isScanning)
            assertNull(state.scanMessage)
            subject.scan(photo)
            // Gated scans return before touching the port, so drafts cannot move.
            assertTrue(fakeScanPort.calls.isEmpty())
            assertEquals(
                listOf(IncrementTierDraft("0", null, "5", openEnded = true)),
                state.tiers,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given the flag on and a successful scan when scanned then bands and stops prefill")
    fun successPrefillsTiersAndMicro() = runTest {
        fakeScanPort.result =
            Result.success(
                ScannedTiers(
                    lb,
                    listOf(
                        ScannedTierRow(0.0, 40.0, 2.5),
                        ScannedTierRow(40.0, null, 5.0),
                    ),
                    microStops = listOf(2.5),
                ),
            )
        fakeFeatureFlags.setFlag(AppFeatureFlag.INCREMENTS_AI_SCAN, true)
        subject.initialize(cableId.value)
        advanceUntilIdle()

        subject.uiState.test {
            awaitItemWith { it.scanAvailable && it.tiers.isNotEmpty() }
            subject.scan(photo)
            val scanned =
                awaitItemWith { it.tiers.any { tier -> tier.increment == "2.5" } }
            assertEquals(
                listOf(
                    IncrementTierDraft("0", "40", "2.5", openEnded = false),
                    IncrementTierDraft("40", null, "5", openEnded = true),
                ),
                scanned.tiers,
            )
            assertEquals(listOf("2.5"), scanned.microStops)
            assertNull(scanned.scanMessage)
            assertFalse(scanned.isScanning)
            cancelAndIgnoreRemainingEvents()
        }

        // Prefill is display-only until the user saves.
        assertEquals(
            openFive,
            fakeEquipmentRepository.getEquipment(cableId)!!.incrementTiers,
        )
    }

    @Test
    @DisplayName("Given the flag on and a failing scan when scanned then drafts are kept with a message")
    fun failureKeepsDraftsWithMessage() = runTest {
        fakeScanPort.result = Result.failure(RuntimeException("network"))
        fakeFeatureFlags.setFlag(AppFeatureFlag.INCREMENTS_AI_SCAN, true)
        subject.initialize(cableId.value)
        advanceUntilIdle()

        subject.uiState.test {
            awaitItemWith { it.scanAvailable && it.tiers.isNotEmpty() }
            subject.scan(photo)
            val failed = awaitItemWith { it.scanMessage != null }
            assertEquals(IncrementScanDelegate.SCAN_FAILURE_MESSAGE, failed.scanMessage)
            assertEquals(
                listOf(IncrementTierDraft("0", null, "5", openEnded = true)),
                failed.tiers,
            )
            assertFalse(failed.isScanning)
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
}
