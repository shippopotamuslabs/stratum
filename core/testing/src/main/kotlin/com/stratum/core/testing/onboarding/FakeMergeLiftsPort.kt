package com.stratum.core.testing.onboarding

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.lift.MergeLiftsPort
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift

/**
 * In-memory [MergeLiftsPort] for domain / ViewModel tests. Mirrors production semantics without
 * Room: reassign sets, collapse/repoint program templates, hide seeds, delete customs.
 */
class FakeMergeLiftsPort(
    private val liftRepository: FakeLiftRepository,
    private val trainingLogRepository: FakeTrainingLogRepository,
    private val programRepository: FakeProgramRepository,
) : MergeLiftsPort {

    var applyMergeCallCount: Int = 0
        private set

    override suspend fun applyMerge(
        targetLiftId: LiftId,
        targetName: String,
        sourcesToHide: Set<LiftId>,
        sourcesToDelete: Set<LiftId>,
    ) {
        applyMergeCallCount++
        val allSources = sourcesToHide + sourcesToDelete
        require(allSources.isNotEmpty()) { "At least one source lift is required" }
        require(targetLiftId !in allSources) { "Target lift cannot also be a source" }

        val sets = trainingLogRepository.getSetsForLifts(allSources + targetLiftId)
        if (sets.isNotEmpty()) {
            val rewritten =
                sets.map { set ->
                    if (set.liftId in allSources) {
                        set.copy(
                            liftId = targetLiftId,
                            exerciseNameRaw = targetName,
                            liftContext = set.liftContext.copy(liftName = targetName),
                            isPersonalRecord = false,
                        )
                    } else {
                        set.copy(isPersonalRecord = false)
                    }
                }
            trainingLogRepository.updateSets(rewritten)
        }

        for (program in programRepository.getAllUserPrograms()) {
            val updated = program.collapseAndRepoint(allSources, targetLiftId)
            if (updated != program) {
                programRepository.saveProgram(updated)
            }
        }

        for (id in sourcesToHide) {
            liftRepository.setLiftEnabled(id, enabled = false)
        }
        if (sourcesToDelete.isNotEmpty()) {
            liftRepository.deleteLifts(sourcesToDelete)
        }
    }

    private fun Program.collapseAndRepoint(
        sourceLiftIds: Set<LiftId>,
        targetLiftId: LiftId,
    ): Program {
        val workouts =
            workouts.map { workout ->
                workout.collapseAndRepoint(sourceLiftIds, targetLiftId)
            }
        return if (workouts == this.workouts) this else copy(workouts = workouts)
    }

    private fun Workout.collapseAndRepoint(
        sourceLiftIds: Set<LiftId>,
        targetLiftId: LiftId,
    ): Workout {
        val hasTarget = workoutLifts.any { it.liftId == targetLiftId }
        val sourceSlots = workoutLifts.filter { it.liftId in sourceLiftIds }.sortedBy { it.position }
        val nonSource = workoutLifts.filter { it.liftId !in sourceLiftIds }

        val keptSources: List<WorkoutLift> =
            when {
                sourceSlots.isEmpty() -> emptyList()
                hasTarget -> emptyList() // drop sources; keep existing target
                else -> listOf(sourceSlots.first().copy(liftId = targetLiftId))
            }

        val merged =
            (nonSource + keptSources)
                .groupBy { it.liftId }
                .flatMap { (_, slots) ->
                    if (slots.size <= 1) {
                        slots
                    } else {
                        listOf(slots.minBy { it.position })
                    }
                }
                .sortedBy { it.position }

        return if (merged == workoutLifts) this else copy(workoutLifts = merged)
    }
}
