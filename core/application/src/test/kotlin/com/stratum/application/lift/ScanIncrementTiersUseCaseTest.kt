package com.stratum.application.lift

import com.stratum.core.testing.lift.FakeIncrementScanPort
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.ScannedTierRow
import com.stratum.domain.lift.ScannedTiers
import com.stratum.domain.lift.Weight

class ScanIncrementTiersUseCaseTest {

    private val photo = ScanPhoto(byteArrayOf(1, 2, 3), "image/jpeg")

    private fun subject(
        port: FakeIncrementScanPort,
    ): ScanIncrementTiersUseCase =
        ScanIncrementTiersUseCase(
            port,
        )

    @Test
    @DisplayName("Given lb rows when invoked then tiers are returned in storage pounds")
    fun returnsLbTiers() = runTest {
        val port = FakeIncrementScanPort(Result.success(scannedLb()))
        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.Success)
        assertEquals(
            listOf(
                tier(0.0, 100.0, 5.0),
                tier(100.0, null, 10.0),
            ),
            (result as ScanIncrementTiersResult.Success).scanned.tiers,
        )
        assertEquals(listOf(photo), port.calls)
    }

    @Test
    @DisplayName("Given kg rows when invoked then amounts are converted to storage pounds")
    fun convertsKgToStoragePounds() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Kg,
                        tiers = listOf(ScannedTierRow(0.0, null, 2.5)),
                        microStops = listOf(1.25),
                    ),
                ),
            )
        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.Success)
        val tiers = (result as ScanIncrementTiersResult.Success).scanned.tiers
        assertEquals(0.0, tiers.single().lowerBound.amount, 1e-9)
        assertEquals(2.5 / 0.45359237, tiers.single().increment.amount, 1e-9)
        assertEquals(Weight.WeightUnit.Lb, tiers.single().increment.unit)
        assertEquals(1, (result as ScanIncrementTiersResult.Success).scanned.microStops.size)
        assertEquals(1.25 / 0.45359237, (result as ScanIncrementTiersResult.Success).scanned.microStops.single().amount, 1e-9)
    }

    @Test
    @DisplayName("Given a single closed band when invoked then it is kept as one open-ended tier")
    fun singleClosedBandIsOpened() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers = listOf(ScannedTierRow(5.0, 100.0, 5.0)),
                    ),
                ),
            )
        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.Success)
        assertEquals(listOf(tier(5.0, null, 5.0)), (result as ScanIncrementTiersResult.Success).scanned.tiers)
    }

    @Test
    @DisplayName("Given a closed final band when invoked then only the final tier is opened")
    fun closedFinalBandIsOpened() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers =
                            listOf(
                                ScannedTierRow(0.0, 40.0, 5.0),
                                ScannedTierRow(40.0, 100.0, 10.0),
                            ),
                    ),
                ),
            )
        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.Success)
        assertEquals(
            listOf(
                tier(0.0, 40.0, 5.0),
                tier(40.0, null, 10.0),
            ),
            (result as ScanIncrementTiersResult.Success).scanned.tiers,
        )
    }

    @Test
    @DisplayName("Given a dropped trailing row when invoked then the last kept tier stays closed and fails")
    fun droppedTrailingRowKeepsFinalTierClosed() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers =
                            listOf(
                                ScannedTierRow(5.0, 100.0, 5.0),
                                ScannedTierRow(100.0, 200.0, 0.0),
                            ),
                    ),
                ),
            )
        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.InvalidTiers)
    }

    @Test
    @DisplayName("Given malformed rows when invoked then they are dropped and valid rows kept")
    fun dropsMalformedRows() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers =
                            listOf(
                                ScannedTierRow(0.0, null, 5.0),
                                ScannedTierRow(0.0, 40.0, 0.0),
                                ScannedTierRow(-10.0, 40.0, 5.0),
                                ScannedTierRow(40.0, 30.0, 5.0),
                                ScannedTierRow(Double.NaN, null, 5.0),
                            ),
                    ),
                ),
            )
        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.Success)
        assertEquals(listOf(tier(0.0, null, 5.0)), (result as ScanIncrementTiersResult.Success).scanned.tiers)
    }

    @Test
    @DisplayName("Given no usable rows when invoked then empty failure is returned")
    fun emptyScanFails() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers = listOf(ScannedTierRow(0.0, 40.0, 0.0)),
                    ),
                ),
            )
        val result = subject(port).invoke(photo)

        assertEquals(ScanIncrementTiersResult.Empty, result)
    }

    @Test
    @DisplayName("Given no usable rows when invoked then the Empty branch is returned (STR-237: the branch is the record; the warning breadcrumb is dropped)")
    fun emptyScanReturnsEmptyBranch() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers = listOf(ScannedTierRow(0.0, 40.0, 0.0)),
                    ),
                ),
            )

        val result = subject(port).invoke(photo)

        assertEquals(ScanIncrementTiersResult.Empty, result)
    }

    @Test
    @DisplayName("Given gapped tiers when invoked then invalid failure is returned")
    fun gappedTiersFail() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers =
                            listOf(
                                ScannedTierRow(0.0, 40.0, 5.0),
                                ScannedTierRow(50.0, null, 10.0),
                            ),
                    ),
                ),
            )
        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.InvalidTiers)
    }

    @Test
    @DisplayName("Given gapped tiers when invoked then the InvalidTiers branch carries the validation errors (STR-237: the branch is the record; the warning breadcrumb is dropped)")
    fun invalidScanReturnsInvalidTiersBranch() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers =
                            listOf(
                                ScannedTierRow(0.0, 40.0, 5.0),
                                ScannedTierRow(50.0, null, 10.0),
                            ),
                    ),
                ),
            )

        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.InvalidTiers)
        assertTrue(
            (result as ScanIncrementTiersResult.InvalidTiers)
                .validation
                .errors
                .contains(com.stratum.domain.lift.IncrementTiersValidationError.NotContiguous),
            "the InvalidTiers branch must carry the validation errors the dropped breadcrumb reported",
        )
    }

    @Test
    @DisplayName("Given valid rows when invoked then the Success branch is returned")
    fun successReturnsSuccessBranch() = runTest {
        val port = FakeIncrementScanPort(Result.success(scannedLb()))

        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.Success)
    }

    @Test
    @DisplayName("Given valid micro stops when invoked then they are kept")
    fun keepsValidMicro() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers = listOf(ScannedTierRow(0.0, null, 15.0)),
                        microStops = listOf(5.0, 10.0),
                    ),
                ),
            )
        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.Success)
        assertEquals(
            listOf(Weight(5.0, Weight.WeightUnit.Lb), Weight(10.0, Weight.WeightUnit.Lb)),
            (result as ScanIncrementTiersResult.Success).scanned.microStops,
        )
    }

    @Test
    @DisplayName("Given invalid micro stops when invoked then tiers are kept and micro dropped")
    fun dropsInvalidMicroKeepsTiers() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers = listOf(ScannedTierRow(0.0, null, 15.0)),
                        microStops = listOf(20.0),
                    ),
                ),
            )
        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.Success)
        assertEquals(listOf(tier(0.0, null, 15.0)), (result as ScanIncrementTiersResult.Success).scanned.tiers)
        assertEquals(emptyList<Weight>(), (result as ScanIncrementTiersResult.Success).scanned.microStops)
    }

    @Test
    @DisplayName("Given port failure when invoked then the ScanFailed branch is returned (STR-243)")
    fun portFailureReturnsScanFailed() = runTest {
        val failure = IOException("network down")
        val port = FakeIncrementScanPort(Result.failure(failure))

        val result = subject(port).invoke(photo)

        assertTrue(result is ScanIncrementTiersResult.ScanFailed)
    }

    @Test
    @DisplayName("Given port failure when invoked then ScanFailed carries the real cause as a diagnostic (STR-240 chunk 8)")
    fun portFailureCarriesCauseOnScanFailed() = runTest {
        // STR-240 chunk 8 (closing STR-238's interim A13): the adapter no longer
        // logs the failure — the branch itself is the carrier, with the exact
        // originating throwable as the diagnostic's cause for the boundary
        // (:app lift-config delegate) to drain exactly once.
        val failure = IOException("network down")
        val port = FakeIncrementScanPort(Result.failure(failure))

        val result = subject(port).invoke(photo)

        val scanFailed = result as ScanIncrementTiersResult.ScanFailed
        assertEquals(1, scanFailed.diagnostics.size)
        val diagnostic = scanFailed.diagnostics.single()
        assertEquals("StratumIncrementScan", diagnostic.tag)
        assertEquals("inferEquipmentIncrements failed: network down", diagnostic.message)
        assertSame(failure, diagnostic.cause)
    }

    private fun scannedLb(): ScannedTiers =
        ScannedTiers(
            unit = Weight.WeightUnit.Lb,
            tiers =
                listOf(
                    ScannedTierRow(0.0, 100.0, 5.0),
                    ScannedTierRow(100.0, null, 10.0),
                ),
        )

    private fun tier(lower: Double, upper: Double?, inc: Double): IncrementTier =
        IncrementTier(
            lowerBound = Weight(lower, Weight.WeightUnit.Lb),
            upperBound = upper?.let { Weight(it, Weight.WeightUnit.Lb) },
            increment = Weight(inc, Weight.WeightUnit.Lb),
        )
}
