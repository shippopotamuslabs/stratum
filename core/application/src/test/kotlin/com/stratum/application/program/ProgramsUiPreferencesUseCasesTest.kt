package com.stratum.application.program

import com.stratum.core.testing.program.FakeProgramsUiPreferencesRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

class ProgramsUiPreferencesUseCasesTest {

    private val fake = FakeProgramsUiPreferencesRepository()

    @Test
    @DisplayName("setExpandedWorkoutId writes the id and returns Success (STR-243)")
    fun setExpandedSuccess() = runTest {
        val result = SetExpandedWorkoutIdUseCase(fake)("workout-1")
        assertEquals(ProgramsUiPreferenceResult.Success, result)
        assertEquals("workout-1", fake.current.expandedWorkoutId)
    }

    @Test
    @DisplayName("setExpandedWorkoutId returns PersistenceFailed carrying the failure diagnostic when the write throws (STR-243)")
    fun setExpandedPersistenceFailure() = runTest {
        val failure = RuntimeException("store unavailable")
        val throwing =
            object : ProgramsUiPreferencesRepository by fake {
                override suspend fun setExpandedWorkoutId(workoutId: String?) {
                    throw failure
                }
            }
        val result = SetExpandedWorkoutIdUseCase(throwing)("workout-1")
        val failed = result as ProgramsUiPreferenceResult.PersistenceFailed
        assertEquals(1, failed.diagnostics.size)
        val diagnostic = failed.diagnostics.single()
        assertEquals("SetExpandedWorkoutId", diagnostic.tag)
        assertEquals("Failed to set expanded workout id: store unavailable", diagnostic.message)
        assertSame(failure, diagnostic.cause)
    }

    @Test
    @DisplayName("setExpandedWorkoutId propagates CancellationException instead of capturing it (STR-243)")
    fun setExpandedCancellationPropagates() {
        val cancelling =
            object : ProgramsUiPreferencesRepository by fake {
                override suspend fun setExpandedWorkoutId(workoutId: String?) {
                    throw CancellationException("cancelled")
                }
            }
        val subject = SetExpandedWorkoutIdUseCase(cancelling)
        assertThrows(CancellationException::class.java) {
            runBlocking { subject("workout-1") }
        }
    }
}
