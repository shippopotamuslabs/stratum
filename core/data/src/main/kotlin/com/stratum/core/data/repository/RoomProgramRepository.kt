package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.LiftSetDao
import com.stratum.core.data.db.ProgramDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.WorkoutDao
import com.stratum.core.data.db.WorkoutLiftDao
import com.stratum.core.data.mapper.rejoinProgram
import com.stratum.core.data.mapper.toEntity
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * Room-backed [ProgramRepository]. `saveProgram` replaces the entire hierarchy in one transaction
 * (delete-all-then-insert across all four tables) so regeneration overwrites cleanly rather than
 * appending (mutation-defense PROG-GEN-203), mirroring `RoomResponseProfilePersistenceRepository`.
 *
 * `observeProgram` watches only the root `programs` row and reloads the full hierarchy inside a
 * read transaction on each emission. That avoids torn intermediate states from combining four
 * independent table observers during delete-all-then-insert. `getProgram` uses the same read path.
 */
class RoomProgramRepository @Inject constructor(
    private val database: StratumDatabase,
    private val programDao: ProgramDao,
    private val workoutDao: WorkoutDao,
    private val workoutLiftDao: WorkoutLiftDao,
    private val liftSetDao: LiftSetDao,
) : ProgramRepository {
    override fun observeProgram(): Flow<Program?> =
        programDao.observe().mapLatest { entity ->
            if (entity == null) null else readFromDb()
        }

    override suspend fun getProgram(): Program? = readFromDb()

    override suspend fun saveProgram(program: Program) {
        database.withTransaction {
            liftSetDao.deleteAll()
            workoutLiftDao.deleteAll()
            workoutDao.deleteAll()
            programDao.deleteAll()
            programDao.upsert(program.toEntity())
            if (program.workouts.isNotEmpty()) {
                workoutDao.insertAll(program.workouts.map { it.toEntity() })
                val lifts = program.workouts.flatMap { it.workoutLifts }
                if (lifts.isNotEmpty()) {
                    workoutLiftDao.insertAll(lifts.map { it.toEntity() })
                    val sets = lifts.flatMap { it.sets }
                    if (sets.isNotEmpty()) {
                        liftSetDao.insertAll(sets.map { it.toEntity() })
                    }
                }
            }
        }
    }

    /** One-shot rejoin inside a read transaction for a consistent hierarchy snapshot. */
    private suspend fun readFromDb(): Program? = database.withTransaction {
        val programEntity = programDao.get() ?: return@withTransaction null
        val workouts = workoutDao.getForProgram(programEntity.id.value)
        val liftsByWorkout = workouts.associate { it.id.value to workoutLiftDao.getForWorkout(it.id.value) }
        val setsByLift = liftsByWorkout.values
            .flatten()
            .associate { it.id.value to liftSetDao.getForWorkoutLift(it.id.value) }
        rejoinProgram(programEntity, workouts, liftsByWorkout, setsByLift)
    }
}
