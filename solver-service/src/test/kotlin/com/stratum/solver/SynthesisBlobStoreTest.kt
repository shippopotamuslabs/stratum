package com.stratum.solver

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Download size policy: the cap is enforced on the fetched bytes, not just
 * blob metadata, closing the metadata/content TOCTOU window (STR-52 review).
 */
class SynthesisBlobStoreTest {
    @Test
    fun `normal content passes`() {
        checkDownloadedContent(ByteArray(1024))
    }

    @Test
    fun `empty content rejected`() {
        assertThrows<IllegalArgumentException> { checkDownloadedContent(ByteArray(0)) }
    }

    @Test
    fun `oversized content rejected`() {
        assertThrows<IllegalArgumentException> { checkDownloadedContent(ByteArray(OVER_CAP_BYTES)) }
    }

    private companion object {
        // One byte over the 5 MiB production cap.
        const val OVER_CAP_BYTES = 5 * 1024 * 1024 + 1
    }
}
