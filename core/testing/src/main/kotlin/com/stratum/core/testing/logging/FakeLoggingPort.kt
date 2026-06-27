package com.stratum.core.testing.logging

import com.stratum.domain.logging.LoggingPort

/** In-memory [LoggingPort] for unit tests. Records errors for assertions; other levels are no-op. */
class FakeLoggingPort : LoggingPort {
    data class ErrorEntry(
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    val errors = mutableListOf<ErrorEntry>()

    override fun debug(tag: String, message: String) = Unit

    override fun info(tag: String, message: String) = Unit

    override fun warning(tag: String, message: String) = Unit

    override fun error(tag: String, message: String, throwable: Throwable?) {
        errors += ErrorEntry(tag, message, throwable)
    }
}
