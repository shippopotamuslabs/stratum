package com.stratum.domain.program

import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutLiftId
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes [Program] templates. Supports multiple user programs (library + one active).
 *
 * - [observeProgram] / [getProgram] expose the **active** training program only.
 * - [saveProgram] upserts a full hierarchy for that program id without deleting other programs.
 * - [saveToLibrary] persists inactive; [activateProgram] promotes a program and demotes the prior
 *   active. Save/activate never destroy sibling programs.
 * - [deleteProgram] is the only path that permanently removes a user program hierarchy.
 */
interface ProgramRepository {
    fun observeProgram(): Flow<Program?>

    /** Observes a single program (active or library) by id; emits null when missing. */
    fun observeProgramById(programId: ProgramId): Flow<Program?>

    suspend fun getProgram(): Program?

    suspend fun getProgramById(programId: ProgramId): Program?

    /**
     * All user programs (active + library), excluding the import-history sentinel.
     * Used when re-pointing template lift ids before catalog delete (merge lifts).
     */
    suspend fun getAllUserPrograms(): List<Program>

    /** Upserts the full hierarchy for [program]. When [Program.isActive] is true, demotes others. */
    suspend fun saveProgram(program: Program)

    /** Persists [program] as inactive library inventory (never activates it). */
    suspend fun saveToLibrary(program: Program)

    /**
     * Makes [programId] the sole active user program. No-op delete of other programs — prior active
     * becomes library inventory.
     */
    suspend fun activateProgram(programId: ProgramId)

    /**
     * Permanently removes the program **template** hierarchy (workouts/lifts/sets). Training
     * sessions and logged sets are retained: session soft refs are cleared explicitly
     * (not Room FKs). Must refuse `import-history`.
     */
    suspend fun deleteProgram(programId: ProgramId)

    /** Inactive user programs (excludes active and import-history). */
    fun observeLibraryPrograms(): Flow<List<Program>>

    /**
     * Targeted pin-note write for one template slot. Returns false when no `workout_lifts`
     * row exists (overlay-added / ad-hoc slot — caller should persist on the overlay).
     */
    suspend fun setWorkoutLiftPinnedNote(id: WorkoutLiftId, note: String?): Boolean

    /**
     * @param cycleStartEpochDay when non-null, updates the cycle projection anchor (typically on
     * cycle rollover). When null, leaves the stored anchor unchanged.
     */
    suspend fun updateCyclePointer(
        programId: ProgramId,
        currentCycleNumber: Int,
        cyclePosition: Int,
        cycleStartEpochDay: Long? = null,
    )
}
