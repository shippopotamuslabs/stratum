package com.stratum.domain.checkin

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.ProgramRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Program muscles currently trained (for subjective chip scope). */
class GetCheckInMuscleGroupsUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val liftRepository: LiftRepository,
) {
    suspend operator fun invoke(): List<MuscleGroup> {
        val program = programRepository.getProgram() ?: return emptyList()
        val liftsById = liftRepository.observeLifts().first().associateBy { it.id }
        return program.workouts
            .flatMap { it.workoutLifts }
            .flatMap { workoutLift -> liftsById[workoutLift.liftId]?.primaryMuscles.orEmpty() }
            .distinct()
            .sortedBy { it.name }
    }
}
