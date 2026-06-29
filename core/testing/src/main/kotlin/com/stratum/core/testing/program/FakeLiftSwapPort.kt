package com.stratum.core.testing.program

import com.stratum.domain.program.generation.swap.LiftSwapPort
import com.stratum.domain.program.generation.swap.LiftSwapRequest
import com.stratum.domain.program.generation.swap.LiftSwapResult

/** No-op lift swap port for pipeline tests — returns empty swaps. */
class FakeLiftSwapPort : LiftSwapPort {
    override suspend fun swapLifts(request: LiftSwapRequest): Result<LiftSwapResult> =
        Result.success(LiftSwapResult(swaps = emptyList()))
}
