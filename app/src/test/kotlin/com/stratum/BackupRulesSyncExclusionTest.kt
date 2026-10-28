package com.stratum

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * STR-206: `cloud_sync_prefs.json` (durable account owner, hydrate flags, pull
 * cursors, device id) must stay excluded from Auto Backup and device transfer —
 * restoring it once stranded a fresh sign-in on a months-old database because the
 * restored cursors claimed the install was already synced. These tests pin the
 * shipped rule files so an edit cannot silently drop the exclusion.
 */
class BackupRulesSyncExclusionTest {
    @Test
    @DisplayName("backup_rules.xml excludes the cloud sync prefs file")
    fun backupRulesExcludeSyncPrefs() {
        val doc = parseXml(xmlFile("backup_rules.xml"))
        assertTrue(
            sectionExcludesSyncPrefs(doc.documentElement),
            "full-backup-content must exclude datastore/cloud_sync_prefs.json",
        )
    }

    @Test
    @DisplayName("data_extraction_rules.xml excludes the cloud sync prefs file for cloud backup and device transfer")
    fun dataExtractionRulesExcludeSyncPrefs() {
        val doc = parseXml(xmlFile("data_extraction_rules.xml"))
        for (sectionName in listOf("cloud-backup", "device-transfer")) {
            val sections = doc.getElementsByTagName(sectionName)
            assertTrue(sections.length == 1, "expected exactly one <$sectionName> section")
            assertTrue(
                sectionExcludesSyncPrefs(sections.item(0) as Element),
                "<$sectionName> must exclude datastore/cloud_sync_prefs.json",
            )
        }
    }

    private fun sectionExcludesSyncPrefs(section: Element): Boolean {
        val excludes = section.getElementsByTagName("exclude")
        return (0 until excludes.length).any { index ->
            val exclude = excludes.item(index) as Element
            exclude.getAttribute("domain") == "file" &&
                exclude.getAttribute("path") == "datastore/cloud_sync_prefs.json"
        }
    }

    private fun parseXml(file: File): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun xmlFile(name: String): File {
        val candidates =
            listOf(
                File("src/main/res/xml/$name"),
                File("app/src/main/res/xml/$name"),
            )
        return candidates.firstOrNull { it.isFile }
            ?: error("cannot locate $name from ${File(".").absolutePath}")
    }
}
