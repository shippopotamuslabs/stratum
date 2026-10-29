package com.stratum.application.lift

import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.ScannedTiers

/** Cloud vision port for inferring increment tiers from a stack-label photo. */
interface IncrementScanPort {
    suspend fun scanTiers(photo: ScanPhoto): Result<ScannedTiers>
}
