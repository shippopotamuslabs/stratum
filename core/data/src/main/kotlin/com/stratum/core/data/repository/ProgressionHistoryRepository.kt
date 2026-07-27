package com.stratum.core.data.repository

import com.stratum.core.data.db.TrainingLoggedSetDao
import com.stratum.core.data.db.TrainingSessionDao
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toTrainingDomain
import com.stratum.domain.workout.ProgressionHistoryPort
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotHistoryRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Historical-priority read implementation (D3, research Gap 4). Returns sets for a slot
 * across **completed** sessions (including imported history and other programs): same-workout
 * entries first (n−1, n−2, … by recency), then same-lift / same-position from other workouts
 * or imports. Program id is not a hard filter — imports use sentinel `import-history` and
 * soft-unlinked sessions may have a null program id.
 *
 * **In-progress sessions are excluded** so live logs never become "previous" or progression
 * baselines when the lifter leaves and re-enters mid-workout.
 */
@Singleton
class ProgressionHistoryRepository @Inject constructor(
    private val sessionDao: TrainingSessionDao,
    private val setDao: TrainingLoggedSetDao,
) : ProgressionHistoryPort {
    override suspend fun historyForSlot(request: SlotHistoryRequest): List<SlotHistoryEntry> {
        val sets =
            setDao.getHistoryForSlot(
                workoutId = request.workoutId,
                liftId = request.liftId,
                setIndex = request.setIndex,
            )
        val entries =
            sets.mapNotNull { entity ->
                val session = sessionDao.getById(entity.sessionId)?.toDomain() ?: return@mapNotNull null
                // Defense in depth — DAO already filters COMPLETED.
                if (session.status != com.stratum.domain.training.TrainingSessionStatus.COMPLETED) {
                    return@mapNotNull null
                }
                SlotHistoryEntry(session, entity.toTrainingDomain())
            }
        val byStartedDesc = compareByDescending<SlotHistoryEntry> { it.session.startedAt }
        val (sameWorkout, otherWorkout) =
            entries.partition { entry -> entry.session.workoutId == request.workoutId }
        return sameWorkout.sortedWith(byStartedDesc) + otherWorkout.sortedWith(byStartedDesc)
    }
}
