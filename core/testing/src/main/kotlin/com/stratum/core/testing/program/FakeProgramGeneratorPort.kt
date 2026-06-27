package com.stratum.core.testing.program

import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramGeneratorPort

/**
 * In-memory [ProgramGeneratorPort]. Returns a configured [program] (or fails) and captures the
 * payload it was invoked with so tests can assert what the use case forwarded.
 */
class FakeProgramGeneratorPort(
    private val program: Program? = null,
    private val fail: Boolean = false,
    private val failMessage: String = "FakeProgramGeneratorPort configured to fail",
) : ProgramGeneratorPort {
    var invokedPayload: String? = null
        private set

    override suspend fun generateProgram(payload: String): Result<Program> {
        invokedPayload = payload
        return if (fail || program == null) {
            Result.failure(IllegalStateException(failMessage))
        } else {
            Result.success(program)
        }
    }
}
