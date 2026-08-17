package com.stratum.domain.logging

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and returns [Result]. On failure, logs once via [loggingPort] with [tag] and a
 * context [message] so operational errors always leave a stack (CODE_STYLE_RULES.md §1).
 *
 * Prefer this in orchestrating use cases that wrap repository / store I/O. Do not also log
 * the same failure in the ViewModel. [CancellationException] is rethrown so coroutine
 * cancellation is not recorded as an operational failure.
 */
suspend inline fun <T> runCatchingLogged(
    loggingPort: LoggingPort,
    tag: String,
    message: String,
    crossinline block: suspend () -> T,
): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        loggingPort.error(tag, "$message: ${error.message}", error)
        Result.failure(error)
    }
