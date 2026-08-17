package com.stratum.domain.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TelemetryPiiScrubberTest {
    @Test
    fun `scrub redacts email addresses`() {
        val scrubbed = TelemetryPiiScrubber.scrub("Contact user@example.com for help")

        assertEquals(
            "Contact ${TelemetryPiiScrubber.REDACTED_EMAIL} for help",
            scrubbed,
        )
    }

    @Test
    fun `scrub redacts content and file URIs`() {
        val scrubbed = TelemetryPiiScrubber.scrub("Imported from content://com.android.providers.media.documents/document/123")

        assertEquals(
            "Imported from ${TelemetryPiiScrubber.REDACTED_URI}",
            scrubbed,
        )
    }

    @Test
    fun `scrub redacts Google Play order IDs`() {
        val scrubbed = TelemetryPiiScrubber.scrub("Purchase order GPA.1234-5678-9012-34567 completed")

        assertEquals(
            "Purchase order ${TelemetryPiiScrubber.REDACTED_ORDER_ID} completed",
            scrubbed,
        )
    }

    @Test
    fun `scrub redacts auth tokens`() {
        val scrubbed = TelemetryPiiScrubber.scrub("Authorization: Bearer ya29.a0AfH6SMDh123456789")

        assertEquals(
            "Authorization: ${TelemetryPiiScrubber.REDACTED_TOKEN}",
            scrubbed,
        )
    }

    @Test
    fun `scrub redacts JSON auth tokens and secrets`() {
        val jsonPayload = """{"apiKey": "secret_api_key_12345", "token": "ya29.secret_token_12345"}"""
        val scrubbed = TelemetryPiiScrubber.scrub(jsonPayload)

        assertEquals(
            """{${TelemetryPiiScrubber.REDACTED_TOKEN}, ${TelemetryPiiScrubber.REDACTED_TOKEN}}""",
            scrubbed,
        )
    }

    @Test
    fun `scrub redacts id_token and password fields`() {
        val scrubbed = TelemetryPiiScrubber.scrub("Auth payload: id_token: eyJhbGciOi12345678, password=supersecretpass123")

        assertEquals(
            "Auth payload: ${TelemetryPiiScrubber.REDACTED_TOKEN}, ${TelemetryPiiScrubber.REDACTED_TOKEN}",
            scrubbed,
        )
    }

    @Test
    fun `scrub redacts credit cards`() {
        val scrubbed = TelemetryPiiScrubber.scrub("Payment card 4111 2222 3333 4444 charged")

        assertEquals(
            "Payment card ${TelemetryPiiScrubber.REDACTED_CARD} charged",
            scrubbed,
        )
    }

    @Test
    fun `scrub redacts unix and android storage paths`() {
        val scrubbed = TelemetryPiiScrubber.scrub("Export written to /storage/emulated/0/Download/export.json and /Users/developer/file.txt")

        assertEquals(
            "Export written to ${TelemetryPiiScrubber.REDACTED_PATH} and ${TelemetryPiiScrubber.REDACTED_PATH}",
            scrubbed,
        )
    }

    @Test
    fun `scrub redacts quoted paths with spaces`() {
        val scrubbed = TelemetryPiiScrubber.scrub("""Saved to "/storage/emulated/0/Download/Stratum Export.json"""")

        assertEquals(
            "Saved to ${TelemetryPiiScrubber.REDACTED_PATH}",
            scrubbed,
        )
    }

    @Test
    fun `scrub preserves standard http and https URLs with data or storage paths`() {
        val url = "Request to https://api.stratum.com/data/v1/sync and https://storage.googleapis.com/bucket/file"
        val scrubbed = TelemetryPiiScrubber.scrub(url)

        assertEquals(url, scrubbed)
    }

    @Test
    fun `scrub redacts short secrets`() {
        val scrubbed = TelemetryPiiScrubber.scrub("password=abc and apiKey: 123")

        assertEquals(
            "${TelemetryPiiScrubber.REDACTED_TOKEN} and ${TelemetryPiiScrubber.REDACTED_TOKEN}",
            scrubbed,
        )
    }

    @Test
    fun `scrub redacts paths in key-value pairs with equals or colon delimiters`() {
        val scrubbed = TelemetryPiiScrubber.scrub("file=/storage/emulated/0/Download/export.json and path:/data/data/com.stratum/databases/stratum.db")

        assertEquals(
            "file=${TelemetryPiiScrubber.REDACTED_PATH} and path:${TelemetryPiiScrubber.REDACTED_PATH}",
            scrubbed,
        )
    }

    @Test
    fun `scrub redacts snake_case tokens and padded base64 tokens`() {
        val scrubbed = TelemetryPiiScrubber.scrub("user_token=secret123, session_token: ya29.abcd==, secret=AQIDBA==")

        assertEquals(
            "${TelemetryPiiScrubber.REDACTED_TOKEN}, ${TelemetryPiiScrubber.REDACTED_TOKEN}, ${TelemetryPiiScrubber.REDACTED_TOKEN}",
            scrubbed,
        )
    }

    @Test
    fun `scrub returns null for null input`() {
        assertNull(TelemetryPiiScrubber.scrub(null))
    }

    @Test
    fun `scrub returns blank for blank input`() {
        assertEquals("", TelemetryPiiScrubber.scrub(""))
        assertEquals("   ", TelemetryPiiScrubber.scrub("   "))
    }
}

