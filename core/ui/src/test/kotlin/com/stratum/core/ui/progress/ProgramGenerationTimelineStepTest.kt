package com.stratum.core.ui.progress

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramGenerationTimelineStepTest {
    @Test
    @DisplayName("Given null progress when mapped then the first pipeline phase is active")
    fun `null progress starts at first phase`() {
        assertEquals(0, null.activeTimelineIndex())
    }

    @Test
    @DisplayName("Given finalizing phase when mapped then timeline index matches pipeline order")
    fun `finalizing phase maps to expected index`() {
        assertEquals(
            programGenerationTimelinePhases.indexOf(ProgramGenerationProgressPhaseUi.FinalizingProgram),
            ProgramGenerationProgressPhaseUi.FinalizingProgram.activeTimelineIndex(),
        )
    }

    @Test
    @DisplayName("Given emitted phases when mapped then timeline index matches pipeline order")
    fun `phases map to pipeline indices`() {
        assertEquals(
            0,
            ProgramGenerationProgressPhaseUi.PreparingPayload.activeTimelineIndex(),
        )
        assertEquals(
            ProgramGenerationProgressPhaseUi.PlanningVolume.activeTimelineIndex(),
            programGenerationTimelinePhases.indexOf(ProgramGenerationProgressPhaseUi.PlanningVolume),
        )
        assertEquals(
            ProgramGenerationProgressPhaseUi.SavingProgram.activeTimelineIndex(),
            programGenerationTimelinePhases.lastIndex,
        )
    }

    @Test
    @DisplayName("Given complete phase when mapped then every timeline step is completed")
    fun `complete phase marks all steps done`() {
        assertEquals(
            programGenerationTimelinePhases.size,
            ProgramGenerationProgressPhaseUi.Complete.activeTimelineIndex(),
        )
    }

    @Test
    @DisplayName("Given step index relative to active index when status resolved then prior steps complete")
    fun `timeline step status follows active index`() {
        val activeIndex = ProgramGenerationProgressPhaseUi.SynthesizingProgram.activeTimelineIndex()
        assertEquals(
            ProgressTimelineStepStatus.Completed,
            timelineStepStatus(0, activeIndex),
        )
        assertEquals(
            ProgressTimelineStepStatus.Active,
            timelineStepStatus(activeIndex, activeIndex),
        )
        assertEquals(
            ProgressTimelineStepStatus.Pending,
            timelineStepStatus(programGenerationTimelinePhases.lastIndex, activeIndex),
        )
    }
}
