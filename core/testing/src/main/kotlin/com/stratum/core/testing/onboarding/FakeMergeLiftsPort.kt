package com.stratum.core.testing.onboarding

import com.stratum.core.testing.lift.FakeLiftNotesRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.lift.MergeLiftsPort
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout

/**
 * In-memory [MergeLiftsPort] for domain / ViewModel tests. Mirrors production semantics without
 * Room: reassign sets, re-point program templates (allowing the same catalog lift twice in a
 * workout), hide seeds, delete customs.
 */
class FakeMergeLiftsPort(
    private val liftRepository: FakeLiftRepository,
    private val trainingLogRepository: FakeTrainingLogRepository,
    private val programRepository: FakeProgramRepository,
    private val liftNotesRepository: FakeLiftNotesRepository = FakeLiftNotesRepository(),
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

        liftNotesRepository.remapLiftIds(allSources, targetLiftId)

        for (program in programRepository.getAllUserPrograms()) {
            val updated = program.repointSources(allSources, targetLiftId)
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

    private fun Program.repointSources(
        sourceLiftIds: Set<LiftId>,
        targetLiftId: LiftId,
    ): Program {
        val workouts =
            workouts.map { workout ->
                workout.repointSources(sourceLiftIds, targetLiftId)
            }
        return if (workouts == this.workouts) this else copy(workouts = workouts)
    }

    private fun Workout.repointSources(
        sourceLiftIds: Set<LiftId>,
        targetLiftId: LiftId,
    ): Workout {
        val repointed =
            workoutLifts.map { slot ->
                if (slot.liftId in sourceLiftIds) {
                    slot.copy(liftId = targetLiftId)
                } else {
                    slot
                }
            }
        return if (repointed == workoutLifts) this else copy(workoutLifts = repointed)
    }
}
