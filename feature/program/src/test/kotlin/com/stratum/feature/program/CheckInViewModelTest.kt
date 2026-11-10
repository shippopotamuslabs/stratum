package com.stratum.feature.program

import com.stratum.application.checkin.ApplyCheckInRecommendationsUseCase
import com.stratum.application.checkin.CheckInPreferencesRepository
import com.stratum.application.checkin.CompleteCheckInUseCase
import com.stratum.application.checkin.EvaluateCheckInUseCase
import com.stratum.application.checkin.GetCheckInMuscleGroupsUseCase
import com.stratum.application.checkin.ObservePendingCheckInUseCase
import com.stratum.application.checkin.SkipCheckInUseCase
import com.stratum.application.lift.RankSwapCandidatesUseCase
import com.stratum.application.program.ComputeProgramMuscleVolumeUseCase
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.checkin.FakeCheckInPreferencesRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeLiftWeeklyPerformanceRepository
import com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.checkin.CheckInPreferences
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.SkippedProgramChangeEventRow
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * STR-237 Route (i): the check-in failure records live on the use-case outcome
 * branches; this ViewModel is the boundary that drains them exactly once
 * through the module [DiagnosticDrain].
 */
@ExtendWith(MainDispatcherRule::class)
class CheckInViewModelTest {

    private val logging = FakeLoggingPort()

    @Test
    @DisplayName("skip failure drains exactly one error record and shows the error copy")
    fun skipFailureDrainsOnce() = runTest {
        val cause = IllegalStateException("prefs boom")
        val failingPrefs =
            object : CheckInPreferencesRepository by FakeCheckInPreferencesRepository() {
                override suspend fun skip(now: Instant) {
                    throw cause
                }
            }
        val viewModel = buildViewModel(prefs = failingPrefs)

        viewModel.skip()

        assertEquals(1, logging.errors.size)
        val entry = logging.errors.single()
        assertEquals("SkipCheckIn", entry.tag)
        assertEquals("Failed to skip check-in: prefs boom", entry.message)
        assertSame(cause, entry.throwable)
        assertEquals("Could not skip check-in", viewModel.uiState.value.errorMessage)
    }

    @Test
    @DisplayName("evaluation drains skipped-row warnings exactly once (Special 2)")
    fun evaluateDrainsSkippedRowWarningsOnce() = runTest {
        val events = FakeProgramChangeEventRepository()
        events.seedSkippedRows(
            SkippedProgramChangeEventRow(
                id = ProgramChangeEventId("evt-bad"),
                eventType = ProgramChangeEventType.SCHEME_CHANGED,
            ),
        )
        val viewModel = buildViewModel(events = events)

        viewModel.openCheckIn()
        viewModel.setHasLocalizedIssues(false)
        viewModel.goNext()

        assertEquals(1, logging.warnings.size)
        val entry = logging.warnings.single()
        assertEquals("ProgramChangeEvents", entry.tag)
        assertEquals(
            "Skipping undecodable program_change_event id=evt-bad type=SCHEME_CHANGED",
            entry.message,
        )
        assertTrue(logging.errors.isEmpty())
    }

    @Test
    @DisplayName("apply completion failure drains exactly one error record")
    fun applyCompletionFailureDrainsOnce() = runTest {
        val cause = IllegalStateException("prefs boom")
        val failingPrefs =
            object : CheckInPreferencesRepository by FakeCheckInPreferencesRepository() {
                override suspend fun complete(now: Instant) {
                    throw cause
                }
            }
        val viewModel = buildViewModel(prefs = failingPrefs)

        viewModel.openCheckIn()
        viewModel.setHasLocalizedIssues(false)
        viewModel.goNext()
        viewModel.goNext()

        assertEquals(1, logging.errors.size)
        val entry = logging.errors.single()
        assertEquals("CompleteCheckIn", entry.tag)
        assertEquals("Failed to complete check-in: prefs boom", entry.message)
        assertSame(cause, entry.throwable)
        assertEquals("Could not apply check-in", viewModel.uiState.value.errorMessage)
    }

    @Test
    @DisplayName("banner flag mirrors pending check-in in uiState (STR-290)")
    fun bannerMirrorsPendingCheckIn() = runTest {
        val prefs = FakeCheckInPreferencesRepository()
        val viewModel = buildViewModel(prefs = prefs)

        assertFalse(viewModel.uiState.value.showBanner)

        prefs.markPending(Instant.parse("2026-02-01T00:00:00Z"))

        assertTrue(viewModel.uiState.value.showBanner)
    }

    @Test
    @DisplayName("skip clears the banner flag (STR-290)")
    fun skipClearsBannerFlag() = runTest {
        val prefs = FakeCheckInPreferencesRepository()
        val viewModel = buildViewModel(prefs = prefs)
        prefs.markPending(Instant.parse("2026-02-01T00:00:00Z"))
        assertTrue(viewModel.uiState.value.showBanner)

        viewModel.skip()

        assertFalse(viewModel.uiState.value.showBanner)
    }

    @Test
    @DisplayName("open and dismiss preserve the banner flag (STR-290)")
    fun openAndDismissPreserveBannerFlag() = runTest {
        val prefs = FakeCheckInPreferencesRepository()
        val viewModel = buildViewModel(prefs = prefs)
        prefs.markPending(Instant.parse("2026-02-01T00:00:00Z"))

        viewModel.openCheckIn()

        assertTrue(viewModel.uiState.value.showBanner)

        viewModel.dismiss()

        assertTrue(viewModel.uiState.value.showBanner)
        assertFalse(viewModel.uiState.value.isOpen)
    }

    private fun buildViewModel(
        prefs: CheckInPreferencesRepository = FakeCheckInPreferencesRepository(),
        events: FakeProgramChangeEventRepository = FakeProgramChangeEventRepository(),
    ): CheckInViewModel {
        val programRepository = FakeProgramRepository(singleLiftProgram())
        val liftRepository = FakeLiftRepository()
        liftRepository.seedLifts(listOf(BENCH))
        if (prefs is FakeCheckInPreferencesRepository) {
            prefs.seed(CheckInPreferences(enabled = true))
        }
        return CheckInViewModel(
            observePendingCheckIn = ObservePendingCheckInUseCase(prefs),
            skipCheckIn = SkipCheckInUseCase(prefs),
            evaluateCheckIn =
                EvaluateCheckInUseCase(
                    programRepository = programRepository,
                    liftRepository = liftRepository,
                    liftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
                    programChangeEventRepository = events,
                    rankSwapCandidates = RankSwapCandidatesUseCase(liftRepository),
                    computeProgramMuscleVolume = ComputeProgramMuscleVolumeUseCase(),
                    muscleVolumeResponseSnapshotRepository =
                        FakeMuscleVolumeResponseSnapshotRepository(),
                ),
            applyRecommendations =
                ApplyCheckInRecommendationsUseCase(
                    programRepository = programRepository,
                    programChangeEventRepository = events,
                    completeCheckIn = CompleteCheckInUseCase(prefs),
                ),
            getCheckInMuscleGroups = GetCheckInMuscleGroupsUseCase(programRepository, liftRepository),
            rankSwapCandidates = RankSwapCandidatesUseCase(liftRepository),
            diagnosticDrain = DiagnosticDrain(logging),
        )
    }

    private fun singleLiftProgram(): Program {
        val workoutLiftId = WorkoutLiftId("wl-1")
        val sets =
            (0 until 3).map { index ->
                LiftSet(
                    id = LiftSetId("ls-$index"),
                    workoutLiftId = workoutLiftId,
                    position = index,
                    setType = SetType.Standard,
                    repMin = 8,
                    repMax = 12,
                    rirGoal = 2,
                    isTopSet = index == 0,
                )
            }
        return Program(
            id = ProgramId("p-1"),
            name = "Split",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 7,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts =
                listOf(
                    Workout(
                        id = WorkoutId("w-1"),
                        programId = ProgramId("p-1"),
                        name = "Push",
                        weekday = Weekday.Monday,
                        position = 0,
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = workoutLiftId,
                                    workoutId = WorkoutId("w-1"),
                                    liftId = BENCH.id,
                                    progressionScheme = ProgressionScheme.Double,
                                    position = 0,
                                    sets = sets,
                                ),
                            ),
                    ),
                ),
        )
    }

    private companion object {
        val BENCH = Lift(
            id = LiftId("bench-press"),
            name = "Bench Press",
            movementPatterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.Tricep, MuscleGroup.AnteriorDeltoid),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            fatigueProfile = FatigueProfile.Medium,
        )
    }
}
