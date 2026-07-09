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
import com.stratum.domain.program.identity.ProgramId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * Room-backed [ProgramRepository]. Multi-program: upserts hierarchy per program id; never deletes
 * sibling programs on save/activate. Child rows for a program are replaced by deleting that
 * program's workouts (FK cascade clears lifts/sets) then re-inserting.
 *
 * `observeProgram` / `getProgram` return the active user program only (import-history excluded).
 */
class RoomProgramRepository @Inject constructor(
    private val database: StratumDatabase,
    private val programDao: ProgramDao,
    private val workoutDao: WorkoutDao,
    private val workoutLiftDao: WorkoutLiftDao,
    private val liftSetDao: LiftSetDao,
) : ProgramRepository {
    override fun observeProgram(): Flow<Program?> =
        programDao.observeActive().mapLatest { entity ->
            if (entity == null) null else readProgram(entity.id)
        }

    override fun observeLibraryPrograms(): Flow<List<Program>> =
        programDao.observeLibrary().map { entities ->
            entities.mapNotNull { entity -> readProgram(entity.id) }
        }

    override suspend fun getProgram(): Program? {
        val active = programDao.getActive() ?: return null
        return readProgram(active.id)
    }

    override suspend fun getProgramById(programId: ProgramId): Program? = readProgram(programId)

    override suspend fun saveProgram(program: Program) {
        database.withTransaction {
            if (program.isActive) {
                programDao.clearActiveFlags()
            }
            upsertHierarchy(program)
        }
    }

    override suspend fun saveToLibrary(program: Program) {
        saveProgram(program.copy(isActive = false))
    }

    override suspend fun activateProgram(programId: ProgramId) {
        database.withTransaction {
            val existing = programDao.getById(programId)
                ?: error("Cannot activate unknown program: ${programId.value}")
            check(existing.id.value != "import-history") {
                "Import-history program cannot be activated"
            }
            programDao.clearActiveFlags()
            programDao.setActive(programId, isActive = true)
        }
    }

    override suspend fun updateCyclePointer(
        programId: ProgramId,
        currentCycleNumber: Int,
        cyclePosition: Int,
    ) {
        programDao.updateCyclePointer(
            programId = programId,
            currentCycleNumber = currentCycleNumber,
            cyclePosition = cyclePosition,
        )
    }

    private suspend fun upsertHierarchy(program: Program) {
        // Cascade FK deletes lifts/sets under this program's workouts only.
        workoutDao.deleteForProgram(program.id)
        programDao.upsert(program.toEntity())
        if (program.workouts.isEmpty()) return
        workoutDao.upsertAll(program.workouts.map { it.toEntity() })
        val lifts = program.workouts.flatMap { it.workoutLifts }
        if (lifts.isEmpty()) return
        workoutLiftDao.upsertAll(lifts.map { it.toEntity() })
        val sets = lifts.flatMap { it.sets }
        if (sets.isNotEmpty()) {
            liftSetDao.upsertAll(sets.map { it.toEntity() })
        }
    }

    private suspend fun readProgram(programId: ProgramId): Program? =
        database.withTransaction {
            val programEntity = programDao.getById(programId) ?: return@withTransaction null
            val workouts = workoutDao.getForProgram(programEntity.id.value)
            val liftsByWorkout =
                workouts.associate { workout ->
                    workout.id.value to workoutLiftDao.getForWorkout(workout.id.value)
                }
            val setsByLift =
                liftsByWorkout.values
                    .flatten()
                    .associate { lift ->
                        lift.id.value to liftSetDao.getForWorkoutLift(lift.id.value)
                    }
            rejoinProgram(programEntity, workouts, liftsByWorkout, setsByLift)
        }
}
