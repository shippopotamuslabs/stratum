package com.stratum.domain.logging

import javax.inject.Inject

/**
 * Clean architecture usecase wrapping domain logging.
 * Allows ViewModels to report errors without directly injecting the [LoggingPort] interface.
 */
class LogErrorUseCase @Inject constructor(
    private val loggingPort: LoggingPort,
) {
    operator fun invoke(tag: String, message: String, throwable: Throwable? = null) {
        loggingPort.error(tag, message, throwable)
    }
}
