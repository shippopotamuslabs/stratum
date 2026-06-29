package com.stratum.feature.onboarding

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
    @DisplayName("Given integrating phase when mapped then timeline index matches pipeline order")
    fun `integrating phase maps to expected index`() {
        assertEquals(
            programGenerationTimelinePhases.indexOf(ProgramGenerationProgressPhaseUi.Integrating),
            ProgramGenerationProgressPhaseUi.Integrating.activeTimelineIndex(),
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
            ProgramGenerationProgressPhaseUi.SplitPlanning.activeTimelineIndex(),
            programGenerationTimelinePhases.indexOf(ProgramGenerationProgressPhaseUi.SplitPlanning),
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
        val activeIndex = ProgramGenerationProgressPhaseUi.BuildingWorkouts.activeTimelineIndex()
        assertEquals(
            ProgramGenerationTimelineStepStatus.Completed,
            timelineStepStatus(0, activeIndex),
        )
        assertEquals(
            ProgramGenerationTimelineStepStatus.Active,
            timelineStepStatus(activeIndex, activeIndex),
        )
        assertEquals(
            ProgramGenerationTimelineStepStatus.Pending,
            timelineStepStatus(programGenerationTimelinePhases.lastIndex, activeIndex),
        )
    }
}
