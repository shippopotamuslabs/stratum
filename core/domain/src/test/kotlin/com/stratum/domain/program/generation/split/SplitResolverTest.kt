package com.stratum.domain.program.generation.split

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.SPECIALIZATION_PRESETS
import com.stratum.domain.program.generation.reference.FrequencyTier
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SplitResolverTest {

    @Test
    @DisplayName("calendar recovery penalizes repeated lower sessions on Mon/Tue/Wed layout")
    fun recoveryPenalizesTightLowerSchedule() {
        val context = parseScenarioContext(scenarioPayload(3, emptyMap()))
        val weekdays = listOf("Monday", "Tuesday", "Wednesday")
        val tight = listOf(SessionTypeId.Lower, SessionTypeId.UpperPush, SessionTypeId.Lower)
        val spread = listOf(SessionTypeId.Lower, SessionTypeId.UpperPush, SessionTypeId.UpperPull)
        val tightScore = recoveryScore(tight, weekdays, context)
        val spreadScore = recoveryScore(spread, weekdays, context)
        assertTrue(spreadScore > tightScore, "spread=$spreadScore tight=$tightScore")
    }

    @Test
    @DisplayName("tier-1 rotation templates exist for 2–6 training days")
    fun tier1RotationTemplatesPresent() {
        val ids = setOf("2d-t1-fb-rot", "4d-t1-main-carousel", "5d-t1-micro", "6d-t1-micro")
        val found = SPLIT_CATALOG.filter { it.id in ids }
        assertTrue(found.size == ids.size)
        assertTrue(found.all { it.frequencyTier == FrequencyTier.One })
    }

    @Test
    @DisplayName("specialization profiles do not prefer tier-1 rotation templates")
    fun specializationAvoidsTier1Rotation() {
        val context = parseScenarioContext(
            scenarioPayload(5, SPECIALIZATION_PRESETS.getValue("arms")),
        )
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        assertFalse(resolved.template.id.contains("t1-micro"))
    }
}
