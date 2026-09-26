package com.stratum.liftconfig

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.lift.FakeIncrementScanPort
import com.stratum.domain.lift.IncrementScanPort
import com.stratum.domain.lift.ScanIncrementTiersUseCase
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.ScannedTierRow
import com.stratum.domain.lift.ScannedTiers
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.ValidateMicroIncrementsUseCase
import com.stratum.domain.lift.Weight
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * State coverage for the shared scan runner: success maps storage-lb results to
 * display-unit drafts, empty/port failures surface the failure message, and the
 * in-flight state is observable (UNIT_TESTING.md §1 — fakes, no MockK).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class IncrementScanDelegateTest {

    private val lb = Weight.WeightUnit.Lb
    private val kg = Weight.WeightUnit.Kg
    private val photo = ScanPhoto(byteArrayOf(1, 2, 3), "image/jpeg")

    private val fakePort = FakeIncrementScanPort()

    private fun subject(port: IncrementScanPort = fakePort) =
        IncrementScanDelegate(
            ScanIncrementTiersUseCase(
                port,
                ValidateIncrementTiersUseCase(),
                ValidateMicroIncrementsUseCase(),
            ),
        )

    @Test
    @DisplayName("Given a fresh delegate when read then the scan state is idle")
    fun startsIdle() {
        assertEquals(IncrementScanState.Idle, subject().scanState.value)
    }

    @Test
    @DisplayName("Given a successful scan when run then tiers and micro map to display-unit drafts")
    fun successMapsToDrafts() = runTest {
        fakePort.result =
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
        val delegate = subject()

        delegate.scan(photo, lb)

        val state = delegate.scanState.value
        assertTrue(state is IncrementScanState.Scanned, "expected Scanned but was $state")
        state as IncrementScanState.Scanned
        assertEquals(
            listOf(
                IncrementTierDraft("0", "40", "2.5", openEnded = false),
                IncrementTierDraft("40", null, "5", openEnded = true),
            ),
            state.tierDrafts,
        )
        assertEquals(listOf("2.5"), state.microStopDrafts)
    }

    @Test
    @DisplayName("Given an empty scan when run then the state is failed with the scan message")
    fun emptyScanFails() = runTest {
        // FakeIncrementScanPort defaults to an empty tier list.
        val delegate = subject()

        delegate.scan(photo, lb)

        val state = delegate.scanState.value
        assertTrue(state is IncrementScanState.Failed, "expected Failed but was $state")
        state as IncrementScanState.Failed
        assertEquals(IncrementScanDelegate.SCAN_FAILURE_MESSAGE, state.message)
    }

    @Test
    @DisplayName("Given a port failure when run then the state is failed with the scan message")
    fun portFailureFails() = runTest {
        fakePort.result = Result.failure(RuntimeException("network"))
        val delegate = subject()

        delegate.scan(photo, lb)

        val state = delegate.scanState.value
        assertTrue(state is IncrementScanState.Failed, "expected Failed but was $state")
        state as IncrementScanState.Failed
        assertEquals(IncrementScanDelegate.SCAN_FAILURE_MESSAGE, state.message)
    }

    @Test
    @DisplayName("Given kg display when scanned then drafts parse back to the storage-lb amounts")
    fun kgDisplayRoundTripsToStorageLb() = runTest {
        fakePort.result =
            Result.success(
                ScannedTiers(
                    lb,
                    listOf(
                        ScannedTierRow(0.0, 100.0, 10.0),
                        ScannedTierRow(100.0, null, 10.0),
                    ),
                    microStops = listOf(5.0),
                ),
            )
        val delegate = subject()

        delegate.scan(photo, kg)

        val state = delegate.scanState.value
        assertTrue(state is IncrementScanState.Scanned, "expected Scanned but was $state")
        state as IncrementScanState.Scanned
        // Display strings round for the field, so the round trip only holds within
        // that rounding — still far tighter than ignoring the unit (which would be
        // off by the full lb/kg factor).
        val tiers = IncrementTierDraftOps.toDomainOrNull(state.tierDrafts, kg)!!
        assertEquals(2, tiers.size)
        assertEquals(0.0, tiers[0].lowerBound.amount, 1e-6)
        assertEquals(lb, tiers[0].lowerBound.unit)
        assertEquals(100.0, tiers[0].upperBound!!.amount, 0.5)
        assertEquals(10.0, tiers[0].increment.amount, 0.5)
        val micro = IncrementTierDraftOps.microToDomainOrNull(state.microStopDrafts, kg)!!
        assertEquals(1, micro.size)
        assertEquals(5.0, micro[0].amount, 0.5)
        assertEquals(lb, micro[0].unit)
    }

    @Test
    @DisplayName("Given a suspended port when run then the scanning state is observable")
    fun scanningIsObservable() = runTest {
        val gate = CompletableDeferred<Result<ScannedTiers>>()
        val gatingPort =
            object : IncrementScanPort {
                override suspend fun scanTiers(photo: ScanPhoto): Result<ScannedTiers> =
                    gate.await()
            }
        val delegate = subject(gatingPort)

        val job = launch { delegate.scan(photo, lb) }
        runCurrent()
        assertEquals(IncrementScanState.Scanning, delegate.scanState.value)

        gate.complete(
            Result.success(ScannedTiers(lb, listOf(ScannedTierRow(0.0, null, 5.0)))),
        )
        job.join()
        assertTrue(delegate.scanState.value is IncrementScanState.Scanned)
    }

    @Test
    @DisplayName("Given a terminal scan when reset then the state returns to idle")
    fun resetReturnsToIdle() = runTest {
        val delegate = subject()

        delegate.scan(photo, lb)
        delegate.reset()

        assertEquals(IncrementScanState.Idle, delegate.scanState.value)
    }
}
