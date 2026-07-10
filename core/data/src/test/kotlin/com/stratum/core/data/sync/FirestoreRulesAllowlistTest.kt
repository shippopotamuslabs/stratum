package com.stratum.core.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Keeps [firestore.rules] `isSyncCollection` in lockstep with [SyncCollection.ALLOWLIST].
 */
class FirestoreRulesAllowlistTest {
    @Test
    fun `rules allowlist matches SyncCollection`() {
        val rulesFile = File("firestore.rules")
        // Tests run with module cwd or project root; try both.
        val content =
            sequenceOf(
                rulesFile,
                File("../firestore.rules"),
                File("../../firestore.rules"),
            ).firstOrNull { it.exists() }?.readText()
                ?: error("firestore.rules not found from ${File(".").absolutePath}")

        val block =
            Regex(
                """function isSyncCollection\(collection\) \{\s*return collection in \[([^\]]+)\]""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(content)
                ?: error("isSyncCollection block not found in firestore.rules")
        val listed =
            Regex("""'([^']+)'""")
                .findAll(block.groupValues[1])
                .map { it.groupValues[1] }
                .toSet()
        assertEquals(SyncCollection.ALLOWLIST, listed)
    }
}
