package com.stratum.domain.logging

import javax.inject.Inject

/**
 * Thin wrapper around [LoggingPort.error]. Prefer logging at the infrastructure adapter or
 * orchestrating use case (see `CODE_STYLE_RULES.md` §1) rather than from ViewModels.
 *
 * Kept for rare presentation-layer last-resort reporting; new code should inject [LoggingPort]
 * only at adapters/use cases, and map failures to UI state (`userMessage` / error branches).
 */
@Deprecated(
    message = "Log operational failures in adapters/use cases; ViewModels should map Result to UI state",
    replaceWith = ReplaceWith("loggingPort.error(tag, message, throwable)"),
)
class LogErrorUseCase @Inject constructor(
    private val loggingPort: LoggingPort,
) {
    operator fun invoke(tag: String, message: String, throwable: Throwable? = null) {
        loggingPort.error(tag, message, throwable)
    }
}
