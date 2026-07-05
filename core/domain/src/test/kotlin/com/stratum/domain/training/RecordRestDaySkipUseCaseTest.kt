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

        val session = useCase(Weekday.Monday).getOrThrow()

        assertEquals(TrainingSessionKind.REST_DAY, session.kind)
        assertNull(session.workoutId)
        assertEquals(Weekday.Monday, session.weekdaySnapshot)
        assertTrue(repo.getSetsForSession(session.id).isEmpty())
    }

    private fun program() = WorkoutLoggingFixtures.twoWorkoutProgram()
}
