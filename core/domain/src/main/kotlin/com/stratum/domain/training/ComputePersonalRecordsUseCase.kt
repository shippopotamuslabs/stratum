package com.stratum.domain.training

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject

class ComputePersonalRecordsUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    data class ComputationResult(
        val updatedIncomingSets: List<LoggedSet>,
        val oldPrSetsToUpdate: List<LoggedSet>,
    )

    suspend operator fun invoke(
        newOrUpdatedSets: List<LoggedSet>,
        excludeSetIds: Set<String> = emptySet(),
    ): Result<ComputationResult> = runCatching {
        val updatedIncoming = mutableListOf<LoggedSet>()
        val oldPrSetsToUpdate = mutableMapOf<String, LoggedSet>()

        // 1. Calculate estimatedOneRm for all incoming sets (considering RIR if present)
        val setsWithE1rm = newOrUpdatedSets.map { set ->
            if (set.liftId != null && set.reps > 0) {
                val effectiveReps = set.reps + (set.rir ?: 0)
                val e1Rm = EstimatedOneRm.calculate(set.weight.amount, effectiveReps)
                set.copy(
                    estimatedOneRm = e1Rm,
                    isPersonalRecord = false
                )
            } else {
                set.copy(
                    estimatedOneRm = null,
                    isPersonalRecord = false
                )
            }
        }

        // 2. Group sets by liftId
        val groups = setsWithE1rm.filter { it.liftId != null }.groupBy { it.liftId!! }

        // Keep track of the final mapped sets. For incoming sets without liftId, add them directly.
        val noLiftIdSets = setsWithE1rm.filter { it.liftId == null }
        updatedIncoming.addAll(noLiftIdSets)

        for ((liftId, sets) in groups) {
            // Find incoming set with max e1RM
            val maxIncomingSet = sets.maxByOrNull { it.estimatedOneRm ?: 0.0 }
            
            // Query the current PR set for this lift
            val existingPr = trainingLogRepository.getPersonalRecordForLift(liftId)
            
            val activeExistingPr = if (existingPr != null && existingPr.id.value !in excludeSetIds) {
                existingPr
            } else {
                null
            }

            if (maxIncomingSet != null && maxIncomingSet.estimatedOneRm != null) {
                val newMaxE1rm = maxIncomingSet.estimatedOneRm
                val existingMaxE1rm = activeExistingPr?.estimatedOneRm ?: 0.0

                if (activeExistingPr == null || newMaxE1rm > existingMaxE1rm) {
                    // We have a new PR!
                    val mappedSets = sets.map { set ->
                        if (set.id == maxIncomingSet.id) {
                            set.copy(isPersonalRecord = true)
                        } else {
                            set.copy(isPersonalRecord = false)
                        }
                    }
                    updatedIncoming.addAll(mappedSets)

                    if (activeExistingPr != null) {
                        if (mappedSets.none { it.id == activeExistingPr.id }) {
                            oldPrSetsToUpdate[activeExistingPr.id.value] = activeExistingPr.copy(isPersonalRecord = false)
                        }
                    }
                } else {
                    // Existing PR remains
                    val mappedSets = sets.map { set ->
                        set.copy(isPersonalRecord = false)
                    }
                    updatedIncoming.addAll(mappedSets)
                }
            } else {
                // All incoming sets have null e1RM
                val mappedSets = sets.map { set ->
                    set.copy(isPersonalRecord = false)
                }
                updatedIncoming.addAll(mappedSets)
            }
        }

        ComputationResult(
            updatedIncomingSets = updatedIncoming,
            oldPrSetsToUpdate = oldPrSetsToUpdate.values.toList(),
        )
    }
}
