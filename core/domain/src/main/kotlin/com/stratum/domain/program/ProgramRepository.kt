package com.stratum.domain.program

import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the generated [Program]. Repository ports expose `Flow` observers plus suspend
 * reads/writes (ARCHITECTURE.md §4.3). `saveProgram` replaces the entire program (not appends) so
 * regeneration overwrites cleanly.
 */
interface ProgramRepository {
    fun observeProgram(): Flow<Program?>

    suspend fun getProgram(): Program?

    suspend fun saveProgram(program: Program)
}
