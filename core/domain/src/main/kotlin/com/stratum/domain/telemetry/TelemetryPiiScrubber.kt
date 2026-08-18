package com.stratum.domain.telemetry

/**
 * Scrubs common PII patterns from telemetry text before Crashlytics breadcrumbs or future
 * OpenTelemetry export via [com.stratum.domain.logging.LoggingPort].
 */
object TelemetryPiiScrubber {
    private val emailPattern =
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val pathPattern =
        Regex(
            """(?i)(?<=^|[\s"'(<\[=:,])(?:["']/(?:home|Users|var|tmp|data|storage|sdcard|mnt|private)/[^"'\\]+["']|""" +
                """/(?:home|Users|var|tmp|data|storage|sdcard|mnt|private)/[^\s"'\\,;)]*)""",
        )
    private val creditCardPattern =
        Regex(
            """\b(?:(?:\d{4}[\s-]?){3}\d{1,4}|3[47]\d{2}[\s-]?\d{6}[\s-]?\d{5})\b""",
        )
    private val uriPattern =
        Regex("""\b(?:content|file)://[^\s"'\\]+""")
    private val playOrderIdPattern =
        Regex("""\bGPA\.\d{4}-\d{4}-\d{4}-\d{5}\b""")
    private val tokenPattern =
        Regex(
            """(?i)(?:(?:(?<=[^A-Za-z0-9_-]|^)|["'])(?:[A-Za-z0-9_]*?(?:api[_-]?key|secret|token|access[_-]?token|refresh[_-]?token|""" +
                """id[_-]?token|auth[_-]?token|client[_-]?secret|private[_-]?key|password))\b["']?\s*""" +
                """[:=]\s*["']?[A-Za-z0-9._~+/-=]{1,}["']?|\bbearer\s+[A-Za-z0-9._~+/-=]{1,})""",
        )

    fun scrub(value: String?): String? {
        if (value.isNullOrBlank()) {
            return value
        }
        return value
            .replace(uriPattern, REDACTED_URI)
            .replace(playOrderIdPattern, REDACTED_ORDER_ID)
            .replace(tokenPattern, REDACTED_TOKEN)
            .replace(emailPattern, REDACTED_EMAIL)
            .replace(creditCardPattern, REDACTED_CARD)
            .replace(pathPattern, REDACTED_PATH)
    }

    const val REDACTED_EMAIL = "[REDACTED_EMAIL]"
    const val REDACTED_CARD = "[REDACTED_CARD]"
    const val REDACTED_PATH = "[REDACTED_PATH]"
    const val REDACTED_URI = "[REDACTED_URI]"
    const val REDACTED_ORDER_ID = "[REDACTED_ORDER_ID]"
    const val REDACTED_TOKEN = "[REDACTED_TOKEN]"
}

