package com.stratum.core.testing.lift

import com.stratum.application.lift.IncrementScanPort
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.ScannedTiers
import com.stratum.domain.lift.Weight

/** In-memory [IncrementScanPort] for use-case and ViewModel tests. */
class FakeIncrementScanPort(
    var result: Result<ScannedTiers> =
        Result.success(ScannedTiers(Weight.WeightUnit.Lb, emptyList())),
) : IncrementScanPort {
    val calls = mutableListOf<ScanPhoto>()

    override suspend fun scanTiers(photo: ScanPhoto): Result<ScannedTiers> {
        calls += photo
        return result
    }
}
