package com.stratum.domain.coroutines

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and returns its outcome as a [Result]. [CancellationException] is rethrown so
 * coroutine cancellation propagates as cancellation instead of arriving as a failure result.
 */
suspend inline fun <T> runCatchingCancellable(
    crossinline block: suspend () -> T,
): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
