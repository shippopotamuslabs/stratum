package com.stratum

import com.stratum.core.data.sync.CLOUD_SYNC_PREFS_FILE_NAME
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * STR-206: the production sync prefs file ([CLOUD_SYNC_PREFS_FILE_NAME]) must stay
 * excluded from Auto Backup and device transfer — full rationale on
 * `CloudSyncPrefsRecord`. The expected path is built from the production
 * constant, so renaming the DataStore file in code fails this test until the
 * rule files are updated to match.
 */
class BackupRulesSyncExclusionTest {
    @Test
    @DisplayName("backup_rules.xml excludes the cloud sync prefs file")
    fun backupRulesExcludeSyncPrefs() {
        val doc = parseXml(xmlFile("backup_rules.xml"))
        assertTrue(
            sectionExcludesSyncPrefs(doc.documentElement),
            "full-backup-content must exclude datastore/$CLOUD_SYNC_PREFS_FILE_NAME",
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
                "<$sectionName> must exclude datastore/$CLOUD_SYNC_PREFS_FILE_NAME",
            )
        }
    }

    private fun sectionExcludesSyncPrefs(section: Element): Boolean {
        val excludes = section.getElementsByTagName("exclude")
        return (0 until excludes.length).any { index ->
            val exclude = excludes.item(index) as Element
            exclude.getAttribute("domain") == "file" &&
                exclude.getAttribute("path") == "datastore/$CLOUD_SYNC_PREFS_FILE_NAME"
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
