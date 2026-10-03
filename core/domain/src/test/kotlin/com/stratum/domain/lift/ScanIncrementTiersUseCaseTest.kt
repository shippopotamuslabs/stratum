package com.stratum.domain.lift

import com.stratum.core.testing.lift.FakeIncrementScanPort
import com.stratum.core.testing.logging.FakeLoggingPort
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ScanIncrementTiersUseCaseTest {

    private val photo = ScanPhoto(byteArrayOf(1, 2, 3), "image/jpeg")

    private fun subject(
        port: FakeIncrementScanPort,
        loggingPort: FakeLoggingPort = FakeLoggingPort(),
    ): ScanIncrementTiersUseCase =
        ScanIncrementTiersUseCase(
            port,
            ValidateIncrementTiersUseCase(),
            ValidateMicroIncrementsUseCase(),
            loggingPort,
        )

    @Test
    @DisplayName("Given lb rows when invoked then tiers are returned in storage pounds")
    fun returnsLbTiers() = runTest {
        val port = FakeIncrementScanPort(Result.success(scannedLb()))
        val result = subject(port).invoke(photo)

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(
                tier(0.0, 100.0, 5.0),
                tier(100.0, null, 10.0),
            ),
            result.getOrThrow().tiers,
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

        assertTrue(result.isSuccess)
        val tiers = result.getOrThrow().tiers
        assertEquals(0.0, tiers.single().lowerBound.amount, 1e-9)
        assertEquals(2.5 / 0.45359237, tiers.single().increment.amount, 1e-9)
        assertEquals(Weight.WeightUnit.Lb, tiers.single().increment.unit)
        assertEquals(1, result.getOrThrow().microStops.size)
        assertEquals(1.25 / 0.45359237, result.getOrThrow().microStops.single().amount, 1e-9)
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

        assertTrue(result.isSuccess)
        assertEquals(listOf(tier(0.0, null, 5.0)), result.getOrThrow().tiers)
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

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IncrementScanEmptyException)
    }

    @Test
    @DisplayName("Given no usable rows when invoked then an empty breadcrumb is logged with photo context")
    fun emptyScanLogsWarningBreadcrumb() = runTest {
        val port =
            FakeIncrementScanPort(
                Result.success(
                    ScannedTiers(
                        unit = Weight.WeightUnit.Lb,
                        tiers = listOf(ScannedTierRow(0.0, 40.0, 0.0)),
                    ),
                ),
            )
        val loggingPort = FakeLoggingPort()

        val result = subject(port, loggingPort).invoke(photo)

        assertTrue(result.isFailure)
        assertEquals(1, loggingPort.warnings.size)
        val warning = loggingPort.warnings.single()
        assertEquals("StratumIncrementScan", warning.tag)
        assertTrue(warning.message.contains("incrementScanEmpty"))
        assertTrue(warning.message.contains("photoBytes=3"))
        assertTrue(warning.message.contains("scannedRows=1"))
        assertTrue(warning.message.contains("droppedRows=1"))
        assertTrue(loggingPort.errors.isEmpty())
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

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IncrementTiersInvalidException)
    }

    @Test
    @DisplayName("Given gapped tiers when invoked then an invalid breadcrumb is logged with validation errors")
    fun invalidScanLogsWarningBreadcrumb() = runTest {
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
        val loggingPort = FakeLoggingPort()

        val result = subject(port, loggingPort).invoke(photo)

        assertTrue(result.isFailure)
        assertEquals(1, loggingPort.warnings.size)
        val warning = loggingPort.warnings.single()
        assertEquals("StratumIncrementScan", warning.tag)
        assertTrue(warning.message.contains("incrementScanInvalid"))
        assertTrue(warning.message.contains("photoBytes=3"))
        assertTrue(warning.message.contains("tierCount=2"))
        assertTrue(warning.message.contains("NotContiguous"))
        assertTrue(loggingPort.errors.isEmpty())
    }

    @Test
    @DisplayName("Given valid rows when invoked then no breadcrumb is logged")
    fun successLogsNothing() = runTest {
        val port = FakeIncrementScanPort(Result.success(scannedLb()))
        val loggingPort = FakeLoggingPort()

        val result = subject(port, loggingPort).invoke(photo)

        assertTrue(result.isSuccess)
        assertTrue(loggingPort.warnings.isEmpty())
        assertTrue(loggingPort.errors.isEmpty())
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

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(Weight(5.0, Weight.WeightUnit.Lb), Weight(10.0, Weight.WeightUnit.Lb)),
            result.getOrThrow().microStops,
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

        assertTrue(result.isSuccess)
        assertEquals(listOf(tier(0.0, null, 15.0)), result.getOrThrow().tiers)
        assertEquals(emptyList<Weight>(), result.getOrThrow().microStops)
    }

    @Test
    @DisplayName("Given port failure when invoked then the failure propagates")
    fun portFailurePropagates() = runTest {
        val failure = IOException("network down")
        val port = FakeIncrementScanPort(Result.failure(failure))

        val result = subject(port).invoke(photo)

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    @DisplayName("Given port failure when invoked then the use case logs nothing (adapter owns callable logging)")
    fun portFailureLogsNothing() = runTest {
        val port = FakeIncrementScanPort(Result.failure(IOException("network down")))
        val loggingPort = FakeLoggingPort()

        val result = subject(port, loggingPort).invoke(photo)

        assertTrue(result.isFailure)
        assertTrue(loggingPort.warnings.isEmpty())
        assertTrue(loggingPort.errors.isEmpty())
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
