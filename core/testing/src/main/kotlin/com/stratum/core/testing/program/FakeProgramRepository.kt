package com.stratum.core.testing.program

import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [ProgramRepository] over a [MutableStateFlow]. `saveProgram` overwrites (replace
 * semantics, not append) and records every saved program so tests can assert ordering and counts.
 */
class FakeProgramRepository(
    initial: Program? = null,
    private val failOnSave: Boolean = false,
) : ProgramRepository {
    private val state = MutableStateFlow(initial)
    val saveProgramCalls = mutableListOf<Program>()

    val current: Program? get() = state.value

    override fun observeProgram(): Flow<Program?> = state.asStateFlow()

    override suspend fun getProgram(): Program? = state.value

    override suspend fun saveProgram(program: Program) {
        check(!failOnSave) { "FakeProgramRepository configured to fail on save" }
        saveProgramCalls.add(program)
        state.value = program
    }

    override suspend fun updateCyclePointer(
        programId: com.stratum.domain.program.identity.ProgramId,
        currentCycleNumber: Int,
        cyclePosition: Int,
    ) {
        val current = state.value ?: return
        if (current.id != programId) return
        state.value = current.copy(
            currentCycleNumber = currentCycleNumber,
            cyclePosition = cyclePosition,
        )
    }
}
