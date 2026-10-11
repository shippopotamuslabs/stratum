package com.stratum.domain.training

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

        val incomingStamped = newOrUpdatedSets.map(PersonalRecordScoring::withComputedEstimatedOneRm)
        val incomingIds = incomingStamped.map { it.id }.toSet()

        val noLiftIdSets = incomingStamped.filter { it.liftId == null }
        updatedIncoming.addAll(
            noLiftIdSets.map { it.copy(isPersonalRecord = false, estimatedOneRm = null) },
        )

        val groups = incomingStamped.filter { it.liftId != null }.groupBy { it.liftId!! }

        for ((liftId, incoming) in groups) {
            val historyRaw =
                trainingLogRepository.getSetsForLift(liftId)
                    .filter { it.id !in incomingIds && it.id.value !in excludeSetIds }
            val flagged =
                PersonalRecordScoring.withPersonalRecordsPerPair(incoming + historyRaw)
                    .associateBy { it.id }

            updatedIncoming.addAll(incoming.map { flagged.getValue(it.id) })

            for (raw in historyRaw) {
                val next = flagged.getValue(raw.id)
                if (next.isPersonalRecord != raw.isPersonalRecord) {
                    oldPrSetsToUpdate[raw.id.value] = next
                }
            }
        }

        ComputationResult(
            updatedIncomingSets = updatedIncoming,
            oldPrSetsToUpdate =
                oldPrSetsToUpdate.values.sortedBy { it.isPersonalRecord },
        )
    }
}
