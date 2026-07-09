package com.stratum.domain.program

import com.stratum.domain.program.identity.ProgramId
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes [Program] templates. Supports multiple user programs (library + one active).
 *
 * - [observeProgram] / [getProgram] expose the **active** training program only.
 * - [saveProgram] upserts a full hierarchy for that program id without deleting other programs.
 * - [saveToLibrary] persists inactive; [activateProgram] promotes a program and demotes the prior
 *   active. Programs are never destroyed on save/activate.
 */
interface ProgramRepository {
    fun observeProgram(): Flow<Program?>

    suspend fun getProgram(): Program?

    suspend fun getProgramById(programId: ProgramId): Program?

    /** Upserts the full hierarchy for [program]. When [Program.isActive] is true, demotes others. */
    suspend fun saveProgram(program: Program)

    /** Persists [program] as inactive library inventory (never activates it). */
    suspend fun saveToLibrary(program: Program)

    /**
     * Makes [programId] the sole active user program. No-op delete of other programs — prior active
     * becomes library inventory.
     */
    suspend fun activateProgram(programId: ProgramId)

    /** Inactive user programs (excludes active and import-history). */
    fun observeLibraryPrograms(): Flow<List<Program>>

    suspend fun updateCyclePointer(
        programId: ProgramId,
        currentCycleNumber: Int,
        cyclePosition: Int,
    )
}
