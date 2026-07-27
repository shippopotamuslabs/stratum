package com.stratum.core.data.db

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * FPR-S2-005 / FPR-S2-006 — Room v1 schema export uses fatigueProfile columns only.
 */
class FatigueProfileSchemaContractTest {

    private val schemasDir =
        File("schemas/com.stratum.core.data.db.StratumDatabase")

    @Test
    @DisplayName("FPR-S2-005 exported 1.json uses fatigueProfile columns")
    fun fprS2005_schemaUsesFatigueProfileColumns() {
        val schema = schemasDir.resolve("1.json")
        assertTrue(schema.exists(), "1.json schema export missing")
        val text = schema.readText()
        assertTrue(
            text.contains("\"fatigueProfile\""),
            "1.json must contain fatigueProfile column identity",
        )
        assertFalse(
            text.contains("\"kinematicProfile\""),
            "1.json must not contain kinematicProfile column identity",
        )
        assertTrue(
            text.contains("fatigueProfile"),
            "createSql must include fatigueProfile",
        )
        assertFalse(
            text.contains("kinematicProfile"),
            "createSql must not include kinematicProfile",
        )
    }

    @Test
    @DisplayName("FPR-S2-006 schema export has v1+v2 (myo) and forbids 3.json")
    fun fprS2006_allowsVersion2Forbids3() {
        assertTrue(schemasDir.resolve("1.json").exists(), "1.json must exist")
        // Room v2 exports myoRepKind (intensifier sets); 2.json is required.
        assertTrue(schemasDir.resolve("2.json").exists(), "2.json schema export missing")
        assertFalse(schemasDir.resolve("3.json").exists(), "3.json must not exist")
        val v2 = schemasDir.resolve("2.json").readText()
        assertTrue(
            v2.contains("\"fatigueProfile\""),
            "2.json must retain fatigueProfile column identity",
        )
        assertFalse(
            v2.contains("\"kinematicProfile\""),
            "2.json must not contain kinematicProfile column identity",
        )
    }
}
