package com.stratum.domain.program.generation.context

import com.stratum.domain.program.schedule.TrainingTimeline

/**
 * Resolves recovery geometry for this context (applies gen cycle defaults via materialize).
 *
 * Schedule identity is [ProgramGenerationContext.trainingDaysPerWeek] + optional
 * [ProgramGenerationContext.restSlotIndices] / [ProgramGenerationContext.cycleLength].
 */
fun ProgramGenerationContext.resolveTrainingTimeline(): TrainingTimeline =
    TrainingTimeline.resolve(
        trainingSlotCount = trainingDaysPerWeek.coerceAtLeast(1),
        restSlotIndices = restSlotIndices,
        cycleLength = cycleLength,
    )

/**
 * Copies this context with cycle-space fields filled from [resolveTrainingTimeline] so
 * solver, seed, validation, and post-passes share the same ring.
 */
fun ProgramGenerationContext.withResolvedCycleSpace(): ProgramGenerationContext {
    val timeline = resolveTrainingTimeline()
    return copy(
        cycleLength = timeline.cycleLength,
        restSlotIndices = timeline.restSlotIndices,
        trainingDaysPerWeek = timeline.trainingSlotIndices.size.coerceAtLeast(1),
    )
}
