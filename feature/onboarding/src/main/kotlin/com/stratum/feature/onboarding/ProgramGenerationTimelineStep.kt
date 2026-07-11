package com.stratum.feature.onboarding

import com.stratum.core.ui.progress.ProgressTimelineStepStatus
import com.stratum.core.ui.progress.activeTimelineIndex as sharedActiveTimelineIndex
import com.stratum.core.ui.progress.programGenerationTimelinePhases as sharedProgramGenerationTimelinePhases
import com.stratum.core.ui.progress.timelineStepStatus as sharedTimelineStepStatus

/** @see com.stratum.core.ui.progress.programGenerationTimelinePhases */
internal val programGenerationTimelinePhases: List<ProgramGenerationProgressPhaseUi> =
    sharedProgramGenerationTimelinePhases

/** @see com.stratum.core.ui.progress.ProgressTimelineStepStatus */
internal typealias ProgramGenerationTimelineStepStatus = ProgressTimelineStepStatus

internal fun ProgramGenerationProgressPhaseUi?.activeTimelineIndex(): Int =
    sharedActiveTimelineIndex()

internal fun timelineStepStatus(
    stepIndex: Int,
    activeIndex: Int,
): ProgramGenerationTimelineStepStatus =
    sharedTimelineStepStatus(stepIndex, activeIndex)
