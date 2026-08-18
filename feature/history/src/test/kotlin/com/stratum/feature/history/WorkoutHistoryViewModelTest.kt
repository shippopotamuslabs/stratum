package com.stratum.feature.history

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.training.ObserveWorkoutHistoryCalendarUseCase
import com.stratum.feature.history.ui.WorkoutHistoryUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class WorkoutHistoryViewModelTest {
    @Test
    @DisplayName("Calendar collector failure maps to Error and retry recovers")
    fun calendarFailureMapsToErrorThenRetry() = runTest {
        val logRepo = FakeTrainingLogRepository()
        logRepo.observeAllSessionsError = IllegalStateException("calendar boom")
        val viewModel =
            WorkoutHistoryViewModel(
                observeWorkoutHistoryCalendar = ObserveWorkoutHistoryCalendarUseCase(logRepo),
            )
        advanceUntilIdle()

        assertEquals(WorkoutHistoryUiState.Error, viewModel.uiState.value)

        logRepo.observeAllSessionsError = null
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is WorkoutHistoryUiState.Loaded)
    }
}
