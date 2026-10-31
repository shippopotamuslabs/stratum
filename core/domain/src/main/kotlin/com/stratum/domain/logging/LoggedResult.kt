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

/**
 * Drains a [Result] returned by a repository write without logging it there. On failure,
 * logs once via [loggingPort] with [tag] and a context [message] so operational errors
 * always leave a stack (CODE_STYLE_RULES.md §1).
 *
 * Prefer this in orchestrating use cases that receive repository / store [Result]s. Do not
 * also log the same failure in the ViewModel. [CancellationException] is rethrown so
 * coroutine cancellation is not recorded as an operational failure.
 */
fun <T> Result<T>.onFailureLogged(
    loggingPort: LoggingPort,
    tag: String,
    message: String,
): Result<T> =
    onFailure { error ->
        if (error is CancellationException) throw error
        loggingPort.error(tag, "$message: ${error.message}", error)
    }

/**
 * Returns [Result.failure] for an expected domain outcome (missing row, stale id, invalid
 * input) that must leave a warning breadcrumb only — never an error / Crashlytics non-fatal.
 * Use for validation outside [runCatchingLogged] blocks. Pass [message] only when the
 * breadcrumb must differ from the throwable message.
 */
fun LoggingPort.expectedFailure(
    tag: String,
    throwable: Throwable,
    message: String = throwable.message ?: "Expected failure",
): Result<Nothing> {
    warning(tag, message)
    return Result.failure(throwable)
}
