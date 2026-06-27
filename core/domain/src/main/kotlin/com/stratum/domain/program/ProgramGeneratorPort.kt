package com.stratum.domain.program

import kotlinx.coroutines.flow.Flow

/**
 * Generates a [Program] from a serialized onboarding payload. The cloud implementation is a thin
 * Firebase Callable client (`:core:inference`); unit tests use a fake. Returns a `Result` so
 * callers can treat network/auth/schema failures uniformly (AGENTS.md §4.6).
 */
interface ProgramGeneratorPort {
    suspend fun generateProgram(payload: String): Result<Program>
}
