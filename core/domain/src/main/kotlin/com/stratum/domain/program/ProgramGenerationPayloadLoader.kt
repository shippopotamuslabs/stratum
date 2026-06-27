package com.stratum.domain.program

/**
 * Loads and serializes the program-generation payload (profile + specialization + proven/available
 * lifts + insights). Introduced as a seam so [GenerateProgramUseCase] depends on a narrow contract
 * rather than the concrete [LoadProgramGenerationPayloadUseCase] and its many repository
 * dependencies (ARCHITECTURE.md §4.4 — use cases compose use cases).
 *
 * The default implementation is [LoadProgramGenerationPayloadUseCase]; production binds it via Hilt
 * and tests substitute a fake.
 */
fun interface ProgramGenerationPayloadLoader {
    suspend fun load(): String
}
