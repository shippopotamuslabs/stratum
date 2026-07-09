package com.stratum.domain.logging

/**
 * Runs [block] and returns [Result]. On failure, logs once via [loggingPort] with [tag] and a
 * context [message] so operational errors always leave a stack (CODE_STYLE_RULES.md §1).
 *
 * Prefer this in orchestrating use cases that wrap repository / store I/O. Do not also log
 * the same failure in the ViewModel.
 */
suspend inline fun <T> runCatchingLogged(
    loggingPort: LoggingPort,
    tag: String,
    message: String,
    crossinline block: suspend () -> T,
): Result<T> =
    runCatching { block() }.onFailure { error ->
        loggingPort.error(tag, "$message: ${error.message}", error)
    }
