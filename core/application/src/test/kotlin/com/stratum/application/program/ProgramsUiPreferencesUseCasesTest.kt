package com.stratum.application.program

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramsUiPreferencesRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

class ProgramsUiPreferencesUseCasesTest {

    private val fake = FakeProgramsUiPreferencesRepository()

    @Test
    @DisplayName("setExpandedWorkoutId writes the id and returns Success (STR-243)")
    fun setExpandedSuccess() = runTest {
        val result = SetExpandedWorkoutIdUseCase(fake, FakeLoggingPort())("workout-1")
        assertEquals(ProgramsUiPreferenceResult.Success, result)
        assertEquals("workout-1", fake.current.expandedWorkoutId)
    }

    @Test
    @DisplayName("setExpandedWorkoutId returns PersistenceFailed when the write throws (STR-243)")
    fun setExpandedPersistenceFailure() = runTest {
        val throwing =
            object : ProgramsUiPreferencesRepository by fake {
                override suspend fun setExpandedWorkoutId(workoutId: String?) {
                    throw RuntimeException("store unavailable")
                }
            }
        val result = SetExpandedWorkoutIdUseCase(throwing, FakeLoggingPort())("workout-1")
        assertEquals(ProgramsUiPreferenceResult.PersistenceFailed, result)
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
        val subject = SetExpandedWorkoutIdUseCase(cancelling, FakeLoggingPort())
        assertThrows(CancellationException::class.java) {
            runBlocking { subject("workout-1") }
        }
    }
}
