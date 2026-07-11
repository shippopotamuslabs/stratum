package com.stratum.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramImportTimelineStepTest {
    @Test
    @DisplayName("Given null phase when mapped then the first import step is active")
    fun `null phase starts at uploading`() {
        assertEquals(0, null.activeImportTimelineIndex())
    }

    @Test
    @DisplayName("Given converting phase when mapped then timeline index matches pipeline order")
    fun `converting phase maps to expected index`() {
        assertEquals(
            programImportTimelinePhases.indexOf(ImportProgramPhaseUi.Converting),
            ImportProgramPhaseUi.Converting.activeImportTimelineIndex(),
        )
    }

    @Test
    @DisplayName("Given complete phase when mapped then every timeline step is completed")
    fun `complete phase marks all steps done`() {
        assertEquals(
            programImportTimelinePhases.size,
            ImportProgramPhaseUi.Complete.activeImportTimelineIndex(),
        )
    }

    @Test
    @DisplayName("Given step index relative to active index when status resolved then prior steps complete")
    fun `timeline step status follows active index`() {
        val activeIndex = ImportProgramPhaseUi.Analyzing.activeImportTimelineIndex()
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
            timelineStepStatus(programImportTimelinePhases.lastIndex, activeIndex),
        )
    }
}
