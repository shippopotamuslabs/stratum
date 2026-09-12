package com.stratum.domain.training

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.onboarding.Weekday
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RecordRestDaySkipUseCaseTest {
    @Test
    @DisplayName("WL-028 RecordRestDaySkipUseCase creates REST_DAY zero-set session")
    fun wl028_restDaySkipSession() = runTest {
        val repo = FakeTrainingLogRepository()
        val useCase = RecordRestDaySkipUseCase(repo, FakeProgramRepository(program()))

        // Prefer slot-index API (schedule identity); rest@1 on a Mon/Wed train week.
        val session = useCase(slotIndex = 1).getOrThrow()

        assertEquals(TrainingSessionKind.REST_DAY, session.kind)
        assertNull(session.workoutId)
        assertEquals(1, session.cycleSlotIndexSnapshot)
        assertTrue(repo.getSetsForSession(session.id).isEmpty())
    }

    @Test
    @DisplayName("Returns failure when no active program exists")
    fun returnsFailureWhenNoActiveProgram() = runTest {
        val useCase = RecordRestDaySkipUseCase(FakeTrainingLogRepository(), FakeProgramRepository(null))

        val slotResult = useCase(slotIndex = 1)
        assertTrue(slotResult.isFailure)
        assertTrue(slotResult.exceptionOrNull() is IllegalStateException)

        val weekdayResult = useCase(weekday = Weekday.Tuesday)
        assertTrue(weekdayResult.isFailure)
        assertTrue(weekdayResult.exceptionOrNull() is IllegalStateException)
    }

    @Test
    @DisplayName("Returns failure when slot is not a rest slot")
    fun returnsFailureWhenSlotNotRest() = runTest {
        val useCase = RecordRestDaySkipUseCase(FakeTrainingLogRepository(), FakeProgramRepository(program()))

        // slot 0 is a training workout, not rest
        val result = useCase(slotIndex = 0)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    private fun program() =
        WorkoutLoggingFixtures.twoWorkoutProgram().copy(
            cycleLength = 7,
            // Mon + Wed train → rest slots Tue,Thu,Fri,Sat,Sun
            restSlotIndices = setOf(1, 3, 4, 5, 6),
            cycleStartEpochDay = 0L,
        )
}
