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
import com.stratum.domain.featureflag.AppFeatureFlag
import com.stratum.domain.featureflag.ObserveIncrementsAiScanUseCase
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.IncrementScanPort
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.ScanIncrementTiersUseCase
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.ScannedTierRow
import com.stratum.domain.lift.ScannedTiers
import com.stratum.domain.lift.SetLiftIncrementTiersUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.ValidateMicroIncrementsUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Scan-state coverage for the per-lift override editor: the launch flag gates the
 * affordance, a successful scan prefills bands and adder stops without saving, a
 * failure keeps drafts with a failed state, and a mid-scan initialize drops the
 * stale result (UNIT_TESTING.md §1 — fakes, no MockK).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class LiftIncrementsScanTest {

    private val lb = Weight.WeightUnit.Lb
    private val latId = LiftId("lat-pulldown")
    private val rowId = LiftId("cable-row")
    private val catalogTiers =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))
    private val photo = ScanPhoto(byteArrayOf(1, 2, 3), "image/jpeg")

    private val fakeLiftRepository = FakeLiftRepository()
    private val fakeEquipmentRepository = FakeEquipmentRepository()
    private val fakeProposalPrefs = FakeEquipmentProposalPreferences()
    private val fakeFeatureFlags = FakeFeatureFlagRepository()
    private val fakeScanPort = FakeIncrementScanPort()
    private lateinit var subject: LiftIncrementsViewModel

    @BeforeEach
    fun setup() {
        fakeFeatureFlags.clear()
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
        subject = buildSubject(fakeScanPort)
    }

    private fun buildSubject(scanPort: IncrementScanPort): LiftIncrementsViewModel =
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
                        scanPort,
                        ValidateIncrementTiersUseCase(),
                        ValidateMicroIncrementsUseCase(),
                        FakeLoggingPort(),
                    ),
                    ObserveIncrementsAiScanUseCase(fakeFeatureFlags),
                ),
        )

    @Test
    @DisplayName("Given the flag off when shown then no scan affordance and scan is a no-op")
    fun flagOffHidesScanAffordance() = runTest {
        subject.initialize(latId.value)
        advanceUntilIdle()

        subject.uiState.test {
            val state =
                awaitItemWith { it.liftName == "Lat pulldown" && it.editor.tiers.isNotEmpty() }
            assertFalse(state.editor.scanAvailable)
            assertEquals(IncrementScanState.Idle, state.editor.scanUi)
            subject.scan(photo)
            runCurrent()
            // Gated scans return before touching the port, so drafts cannot move.
            assertTrue(fakeScanPort.calls.isEmpty())
            assertEquals(
                listOf(IncrementTierDraft("0", null, "5", openEnded = true)),
                subject.uiState.value.editor.tiers,
            )
            assertEquals(IncrementScanState.Idle, subject.uiState.value.editor.scanUi)
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
        subject.initialize(latId.value)
        advanceUntilIdle()

        subject.uiState.test {
            awaitItemWith { it.editor.scanAvailable && it.editor.tiers.isNotEmpty() }
            subject.scan(photo)
            // The drafts write and the terminal scan state arrive on separate
            // combine paths, so await the item carrying both.
            val scanned =
                awaitItemWith {
                    it.editor.tiers.any { tier -> tier.increment == "2.5" } &&
                        it.editor.scanUi is IncrementScanState.Scanned
                }
            assertEquals(
                listOf(
                    IncrementTierDraft("0", "40", "2.5", openEnded = false),
                    IncrementTierDraft("40", null, "5", openEnded = true),
                ),
                scanned.editor.tiers,
            )
            assertEquals(listOf("2.5"), scanned.editor.microStops)
            cancelAndIgnoreRemainingEvents()
        }

        // Prefill is display-only until the user saves.
        assertEquals(
            catalogTiers,
            fakeLiftRepository.getLiftsByIds(listOf(latId)).single().incrementTiers,
        )
    }

    @Test
    @DisplayName("Given the flag on and a failing scan when scanned then drafts are kept with a failed state")
    fun failureKeepsDraftsWithFailedState() = runTest {
        fakeScanPort.result = Result.failure(RuntimeException("network"))
        fakeFeatureFlags.setFlag(AppFeatureFlag.INCREMENTS_AI_SCAN, true)
        subject.initialize(latId.value)
        advanceUntilIdle()

        subject.uiState.test {
            awaitItemWith { it.editor.scanAvailable && it.editor.tiers.isNotEmpty() }
            subject.scan(photo)
            val failed = awaitItemWith { it.editor.scanUi is IncrementScanState.Failed }
            assertEquals(
                listOf(IncrementTierDraft("0", null, "5", openEnded = true)),
                failed.editor.tiers,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a scan in flight when initialized to another lift then the stale result is dropped")
    fun initializeDuringScanDropsStaleResult() = runTest {
        fakeFeatureFlags.setFlag(AppFeatureFlag.INCREMENTS_AI_SCAN, true)
        fakeLiftRepository.seedLifts(
            listOf(
                lift(latId, "Lat pulldown", catalogTiers),
                lift(
                    rowId,
                    "Cable row",
                    listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb))),
                ),
            ),
        )
        val gate = CompletableDeferred<Result<ScannedTiers>>()
        val gatedPort = GatedScanPort(gate)
        val gatedSubject = buildSubject(gatedPort)
        gatedSubject.initialize(latId.value)

        gatedSubject.uiState.test {
            awaitItemWith { it.liftName == "Lat pulldown" && it.editor.scanAvailable }
            gatedSubject.scan(photo)
            awaitItemWith { it.editor.scanUi is IncrementScanState.Scanning }
            // Switch lifts mid-scan: the stale completion must not prefill the new lift.
            gatedSubject.initialize(rowId.value)
            awaitItemWith {
                it.liftName == "Cable row" && it.editor.scanUi is IncrementScanState.Idle
            }
            gate.complete(
                Result.success(
                    ScannedTiers(
                        lb,
                        listOf(ScannedTierRow(0.0, null, 2.5)),
                        microStops = listOf(1.25),
                    ),
                ),
            )
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }

        val final = gatedSubject.uiState.value
        assertEquals(rowId.value, final.liftId)
        assertEquals(
            listOf(IncrementTierDraft("0", null, "10", openEnded = true)),
            final.editor.tiers,
        )
        assertEquals(emptyList<String>(), final.editor.microStops)
        assertEquals(IncrementScanState.Idle, final.editor.scanUi)
    }

    private fun lift(id: LiftId, name: String, tiers: List<IncrementTier>) =
        Lift(
            id = id,
            name = name,
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = emptySet(),
            incrementTiers = tiers,
            presetTags = emptySet(),
            usesEquipmentDefaultIncrements = false,
            enabled = true,
        )

    private suspend fun app.cash.turbine.ReceiveTurbine<LiftIncrementsUiState>.awaitItemWith(
        predicate: (LiftIncrementsUiState) -> Boolean,
    ): LiftIncrementsUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
