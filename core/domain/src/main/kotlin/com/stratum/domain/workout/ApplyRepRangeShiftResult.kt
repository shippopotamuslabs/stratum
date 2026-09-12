package com.stratum.domain.workout

sealed interface ApplyRepRangeShiftResult {
    data object Success : ApplyRepRangeShiftResult
    data object ProgramNotFound : ApplyRepRangeShiftResult
    data object LiftNotFound : ApplyRepRangeShiftResult
    data class ProposalMismatch(val reason: String) : ApplyRepRangeShiftResult
    data class Failure(val cause: Throwable) : ApplyRepRangeShiftResult
}

val ApplyRepRangeShiftResult.isSuccess: Boolean
    get() = this is ApplyRepRangeShiftResult.Success

val ApplyRepRangeShiftResult.isFailure: Boolean
    get() = this !is ApplyRepRangeShiftResult.Success
