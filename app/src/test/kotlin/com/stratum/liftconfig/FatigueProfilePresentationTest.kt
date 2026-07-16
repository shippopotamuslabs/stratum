package com.stratum.liftconfig

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LiftCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * FPR-S5-001 / FPR-S5-002 / FPR-S5-007 / FPR-S5-M001 / FPR-S5-006 presentation + string contracts.
 */
class FatigueProfilePresentationTest {

    @Test
    @DisplayName("FPR-S5-001 fatigue detail labels are High Medium Low")
    fun fprS5001_detailLabelsHighMediumLow() {
        assertEquals("High", FatigueProfile.High.toDetailLabel())
        assertEquals("Medium", FatigueProfile.Medium.toDetailLabel())
        assertEquals("Low", FatigueProfile.Low.toDetailLabel())
    }

    @Test
    @DisplayName("FPR-S5-M001 presentation no longer uses Compound -- Spine Loaded labels")
    fun fprS5M001_oldCompoundLabelsGone() {
        val high = FatigueProfile.High.toDetailLabel()
        val medium = FatigueProfile.Medium.toDetailLabel()
        val low = FatigueProfile.Low.toDetailLabel()
        assertEquals("High", high)
        assertFalse(high == "Compound -- Spine Loaded")
        assertEquals("Medium", medium)
        assertFalse(medium == "Compound -- No Spine Loading")
        assertEquals("Low", low)
        assertFalse(low == "Isolation")
    }

    @Test
    @DisplayName("FPR-S5-002 string resource title is Fatigue profile")
    fun fprS5002_fieldTitleFatigueProfile() {
        val strings = uiStringsXml()
        assertTrue(
            strings.contains("name=\"lift_info_fatigue_profile\""),
            "strings.xml must define lift_info_fatigue_profile",
        )
        assertTrue(
            strings.contains(">Fatigue profile</string>"),
            "lift_info_fatigue_profile value must be Fatigue profile",
        )
    }

    @Test
    @DisplayName("FPR-S5-007 fatigue tier descriptions live under sheet options, not a single blob")
    fun fprS5007_perOptionDescriptions() {
        val strings = uiStringsXml()
        assertFalse(
            strings.contains("name=\"lift_info_fatigue_help\""),
            "inline fatigue help blob must be removed from Details",
        )
        assertEquals(
            "Heavy free-weight compounds — squat, deadlift, barbell row",
            extractStringValue(strings, "lift_info_fatigue_desc_high"),
        )
        assertEquals(
            "Supported multi-joint work — machine press, iso-lateral row",
            extractStringValue(strings, "lift_info_fatigue_desc_medium"),
        )
        assertEquals(
            "Accessories — fly, curl, raise, extension",
            extractStringValue(strings, "lift_info_fatigue_desc_low"),
        )
    }

    @Test
    @DisplayName("FPR-S5-006 Gym and lift-swap filter options include Accessory labels")
    fun fprS5006_filterChipsAccessory() {
        val labels = LiftCategory.entries.map { it.displayName }
        assertTrue("Upper Accessory" in labels)
        assertTrue("Lower Accessory" in labels)
        assertFalse("Upper Isolation" in labels)
        assertFalse("Lower Isolation" in labels)
    }

    private fun uiStringsXml(): String {
        val candidates =
            listOf(
                File("../core/ui/src/main/res/values/strings.xml"),
                File("core/ui/src/main/res/values/strings.xml"),
                File("../../core/ui/src/main/res/values/strings.xml"),
            )
        val file =
            candidates.firstOrNull { it.exists() }
                ?: error("core/ui strings.xml not found from ${File(".").absolutePath}")
        return file.readText()
    }

    private fun extractStringValue(xml: String, name: String): String {
        val regex =
            Regex("""<string name="$name"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(xml) ?: error("string resource $name not found")
        return match.groupValues[1]
    }
}
