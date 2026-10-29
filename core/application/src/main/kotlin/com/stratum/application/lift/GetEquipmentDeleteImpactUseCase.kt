package com.stratum.application.lift

import com.stratum.domain.lift.AffectedTemplateWorkout
import com.stratum.domain.lift.EquipmentDeleteImpact
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.application.program.ProgramRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import com.stratum.domain.lift.SeedEquipmentIds

class GetEquipmentDeleteImpactUseCase @Inject constructor(
    private val equipmentRepository: EquipmentRepository,
    private val liftRepository: LiftRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(id: EquipmentId): EquipmentDeleteImpact? {
        val equipment = equipmentRepository.getEquipment(id) ?: return null
        val lifts = liftRepository.observeLifts().first()
        val affected = filterEquipmentAffectedLifts(id, lifts)
        val affectedWorkouts =
            programRepository.getAllUserPrograms()
                .flatMap { program ->
                    program.workouts.map { workout -> program.name to workout }
                }
                .mapNotNull { (programName, workout) ->
                    val count = workout.workoutLifts.count { it.equipmentId == id }
                    if (count == 0) {
                        null
                    } else {
                        AffectedTemplateWorkout(programName, workout.name, count)
                    }
                }
                .sortedWith(compareBy({ it.workoutName.lowercase() }, { it.programName.lowercase() }))
        val candidates =
            equipmentRepository.observeEquipment().first()
                .filter { it.id != id && it.id !in SeedEquipmentIds.hiddenFromCatalog }
        return EquipmentDeleteImpact(
            equipmentId = id,
            equipmentName = equipment.name,
            affectedLifts = affected.lifts,
            affectedWorkouts = affectedWorkouts,
            replacementCandidates = candidates,
        )
    }
}
