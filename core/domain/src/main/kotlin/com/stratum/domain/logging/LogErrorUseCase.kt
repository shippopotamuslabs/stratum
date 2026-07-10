package com.stratum.domain.logging

import javax.inject.Inject

/**
 * Thin wrapper around [LoggingPort.error] for presentation-layer last-resort reporting.
 *
 * Prefer logging operational failures in infrastructure adapters or orchestrating use cases
 * (see `CODE_STYLE_RULES.md` §1) and mapping [Result] failures to UI state. ViewModels must not
 * inject [LoggingPort] directly (ARCHITECTURE.md §4.4 / ForbiddenPortInViewModel); use this
 * use case when a failure originates outside a use case (e.g. local file materialization) or
 * when a use case returns a failure that still needs a presentation-side breadcrumb.
 */
class LogErrorUseCase @Inject constructor(
    private val loggingPort: LoggingPort,
) {
    operator fun invoke(tag: String, message: String, throwable: Throwable? = null) {
        loggingPort.error(tag, message, throwable)
    }
}
