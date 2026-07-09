package com.stratum.core.testing.program

import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.identity.ProgramId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory multi-program [ProgramRepository]. [saveProgram] upserts by id; when [Program.isActive]
 * is true, other programs are demoted. Never deletes sibling programs.
 */
class FakeProgramRepository(
    initial: Program? = null,
    private val failOnSave: Boolean = false,
) : ProgramRepository {
    private val programs = MutableStateFlow<Map<String, Program>>(
        if (initial == null) emptyMap() else mapOf(initial.id.value to initial.copy(isActive = true)),
    )
    val saveProgramCalls = mutableListOf<Program>()

    /** Active program only (matches production [getProgram]). */
    val current: Program? get() = programs.value.values.firstOrNull { it.isActive }

    override fun observeProgram(): Flow<Program?> =
        programs.map { map -> map.values.firstOrNull { program -> program.isActive } }

    override fun observeLibraryPrograms(): Flow<List<Program>> =
        programs.map { map -> map.values.filter { program -> !program.isActive } }

    override suspend fun getProgram(): Program? = current

    override suspend fun getProgramById(programId: ProgramId): Program? =
        programs.value[programId.value]

    override suspend fun saveProgram(program: Program) {
        check(!failOnSave) { "FakeProgramRepository configured to fail on save" }
        saveProgramCalls.add(program)
        val next = programs.value.toMutableMap()
        if (program.isActive) {
            next.replaceAll { _, existing -> existing.copy(isActive = false) }
        }
        next[program.id.value] = program
        programs.value = next
    }

    override suspend fun saveToLibrary(program: Program) {
        saveProgram(program.copy(isActive = false))
    }

    override suspend fun activateProgram(programId: ProgramId) {
        val existing = programs.value[programId.value]
            ?: error("Cannot activate unknown program: ${programId.value}")
        val next = programs.value.toMutableMap()
        next.replaceAll { _, program -> program.copy(isActive = false) }
        next[programId.value] = existing.copy(isActive = true)
        programs.value = next
    }

    override suspend fun updateCyclePointer(
        programId: ProgramId,
        currentCycleNumber: Int,
        cyclePosition: Int,
    ) {
        val existing = programs.value[programId.value] ?: return
        programs.value =
            programs.value + (
                programId.value to existing.copy(
                    currentCycleNumber = currentCycleNumber,
                    cyclePosition = cyclePosition,
                )
            )
    }
}
