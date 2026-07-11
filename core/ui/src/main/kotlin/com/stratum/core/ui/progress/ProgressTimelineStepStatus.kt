package com.stratum.core.ui.progress

/**
 * Visual state of one step on [com.stratum.core.ui.component.StratumProgressTimeline].
 */
enum class ProgressTimelineStepStatus {
    Completed,
    Active,
    Pending,
}

fun timelineStepStatus(
    stepIndex: Int,
    activeIndex: Int,
): ProgressTimelineStepStatus =
    when {
        stepIndex < activeIndex -> ProgressTimelineStepStatus.Completed
        stepIndex == activeIndex -> ProgressTimelineStepStatus.Active
        else -> ProgressTimelineStepStatus.Pending
    }
