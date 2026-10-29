package com.stratum.liftconfig

import com.stratum.application.lift.IncrementScanPort
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.ScannedTiers
import kotlinx.coroutines.CompletableDeferred

/**
 * [IncrementScanPort] that suspends until the test completes [gate], so scan
 * tests can interleave lifecycle events (initialize, unit flip) mid-scan.
 */
internal class GatedScanPort(
    private val gate: CompletableDeferred<Result<ScannedTiers>>,
) : IncrementScanPort {
    val calls = mutableListOf<ScanPhoto>()

    override suspend fun scanTiers(photo: ScanPhoto): Result<ScannedTiers> {
        calls += photo
        return gate.await()
    }
}
