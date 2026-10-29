package com.stratum.feature.settings

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.export.ExportLogRange
import com.stratum.application.export.ExportTrainingLogsUseCase
import com.stratum.application.export.PreviewExportTrainingLogsUseCase
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ExportLogsViewModelTest {

    private val zone = ZoneId.of("America/New_York")
    private val clock: () -> Instant = { Instant.parse("2026-07-16T15:00:00Z") }

    private fun sessionA() =
        TrainingSession(
            id = TrainingSessionId("sess-a"),
            programId = null,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.AD_HOC,
            origin = TrainingSessionOrigin.IMPORT,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = Instant.parse("2026-07-01T14:00:00Z"),
            endedAt = Instant.parse("2026-07-01T15:00:00Z"),
            sourceApp = "hevy",
        )

    private fun setA1() =
        LoggedSet(
            id = LoggedSetId("set-a1"),
            sessionId = TrainingSessionId("sess-a"),
            exerciseNameRaw = "Squat",
            liftId = null,
            setIndex = 0,
            weight = Weight(100.0, Weight.WeightUnit.Lb),
            reps = 5,
            rir = 2,
            completedAt = Instant.parse("2026-07-01T14:05:00Z"),
            liftContext = LoggedLiftContext(liftName = "Squat"),
        )

    private fun buildVm(repo: FakeTrainingLogRepository = FakeTrainingLogRepository()): ExportLogsViewModel =
        ExportLogsViewModel(
            previewExport = PreviewExportTrainingLogsUseCase(repo, clock),
            exportLogs = ExportTrainingLogsUseCase(repo, clock, FakeLoggingPort()),
            zone = zone,
        )

    @Test
    @DisplayName("IE-S6-001 Export logs click emits share event")
    fun ieS6_001_shareEvent() = runTest {
        val repo = FakeTrainingLogRepository(listOf(sessionA()), listOf(setA1()))
        val vm = buildVm(repo)
        // Ensure AllTime selected (default)
        vm.shareEvents.test {
            vm.onExportClick()
            advanceUntilIdle()
            val event = awaitItem()
            assertEquals("application/json", event.mimeType)
            assertFalse(vm.uiState.value.isExporting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("IE-S6-002 Selecting Last30 updates preview to sessionCount 1")
    fun ieS6_002_last30Preview() = runTest {
        val repo = FakeTrainingLogRepository(listOf(sessionA()), listOf(setA1()))
        val vm = buildVm(repo)
        vm.onRangePresetSelected(ExportRangePresetUi.Last30)
        advanceUntilIdle()
        assertEquals(ExportRangePresetUi.Last30, vm.uiState.value.rangePreset)
        assertEquals(1, vm.uiState.value.preview!!.sessionCount)
        assertEquals(1, vm.uiState.value.preview!!.setCount)
    }

    @Test
    @DisplayName("IE-S6-003 Empty range export surfaces EmptyRange presentation error")
    fun ieS6_003_emptyExportError() = runTest {
        val vm = buildVm(FakeTrainingLogRepository())
        vm.shareEvents.test {
            vm.onExportClick()
            advanceUntilIdle()
            assertEquals(ExportLogsErrorUi.EmptyRange, vm.uiState.value.error)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("IE-S6-004 Custom dates stored on state")
    fun ieS6_004_customDates() = runTest {
        val vm = buildVm()
        vm.onRangePresetSelected(ExportRangePresetUi.Custom)
        vm.onCustomDatesChanged(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 1, 1), vm.uiState.value.customStart)
        assertEquals(LocalDate.of(2026, 1, 31), vm.uiState.value.customEnd)
        assertEquals(ExportRangePresetUi.Custom, vm.uiState.value.rangePreset)
    }
}
