package com.stratum

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeMergeLiftsPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.workout.FakeWorkoutOverlayStore
import com.stratum.core.ui.component.LiftLibraryFilter
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MergeLiftsUseCase
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.SetLiftEnabledUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.ComputePersonalRecordsUseCase
import com.stratum.domain.training.RebuildLocalDerivedTrainingDataUseCase
import com.stratum.domain.training.RefreshWeeklySeriesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class GymViewModelTest {

    private val fakeLiftRepository = FakeLiftRepository()
    private val fakeLogRepository = FakeTrainingLogRepository()
    private val fakeProgramRepository = FakeProgramRepository()
    private val logging = FakeLoggingPort()
    private lateinit var mergePort: FakeMergeLiftsPort
    private lateinit var subject: GymViewModel

    private val seedLifts = listOf(
        Lift(
            id = LiftId("1"),
            name = "Bench Press",
            movementPatterns = emptySet(),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = false,
            muscleLengthBias = MuscleLengthBias.MidRange,
        ),
        Lift(
            id = LiftId("2"),
            name = "Squat",
            movementPatterns = emptySet(),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = false,
            isCustom = false,
            muscleLengthBias = MuscleLengthBias.Lengthened,
        ),
        Lift(
            id = LiftId("3"),
            name = "Custom Row",
            movementPatterns = emptySet(),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = true,
            muscleLengthBias = MuscleLengthBias.Shortened,
        ),
    )

    @BeforeEach
    fun setup() {
        fakeLiftRepository.seedLifts(seedLifts)
        mergePort = FakeMergeLiftsPort(fakeLiftRepository, fakeLogRepository, fakeProgramRepository)
        subject =
            GymViewModel(
                observeLiftCatalog = ObserveLiftCatalogUseCase(fakeLiftRepository),
                setLiftEnabled = SetLiftEnabledUseCase(fakeLiftRepository, logging),
                mergeLifts =
                    MergeLiftsUseCase(
                        liftRepository = fakeLiftRepository,
                        trainingLogRepository = fakeLogRepository,
                        mergeLiftsPort = mergePort,
                        computePersonalRecords = ComputePersonalRecordsUseCase(fakeLogRepository),
                        rebuildLocalDerived =
                            RebuildLocalDerivedTrainingDataUseCase(
                                localDerivedStages =
                                    com.stratum.core.testing.training.localDerivedTrainingStages(
                                        trainingLog = fakeLogRepository,
                                        liftRepository = fakeLiftRepository,
                                    ),
                                responseModelingScheduler = FakeResponseModelingSchedulerPort(),
                                loggingPort = logging,
                            ),
                        workoutOverlayStore = FakeWorkoutOverlayStore(),
                        loggingPort = logging,
                    ),
                deleteLift =
                    com.stratum.domain.lift.DeleteLiftUseCase(
                        deleteLiftPort =
                            com.stratum.core.testing.onboarding.FakeDeleteLiftPort(
                                fakeLiftRepository,
                                fakeProgramRepository,
                            ),
                        rebuildDerived =
                            RebuildLocalDerivedTrainingDataUseCase(
                                localDerivedStages =
                                    com.stratum.core.testing.training.localDerivedTrainingStages(
                                        trainingLog = fakeLogRepository,
                                        liftRepository = fakeLiftRepository,
                                    ),
                                responseModelingScheduler = FakeResponseModelingSchedulerPort(),
                                loggingPort = logging,
                            ),
                        loggingPort = logging,
                    ),
                getLiftDeleteImpact =
                    com.stratum.domain.lift.GetLiftDeleteImpactUseCase(
                        fakeLiftRepository,
                        fakeProgramRepository,
                        fakeLogRepository,
                    ),
            )
    }

    @Test
    @DisplayName("Given catalog from repository when observed then maps all lifts including disabled ones")
    fun mapsAllLifts() = runTest {
        subject.uiState.test {
            val state = awaitItem()
            val updatedState = if (state.sheet.options.isEmpty()) awaitItem() else state
            val options = updatedState.sheet.options

            assertEquals(3, options.size)
            assertEquals("Custom Row", options[0].name)
            assertEquals("Bench Press", options[1].name)
            assertEquals("Squat", options[2].name)

            assertTrue(options[0].enabled)
            assertTrue(options[1].enabled)
            assertFalse(options[2].enabled)
        }
    }

    @Test
    @DisplayName("Given search query when changed then filters catalog by name")
    fun searchFiltersByName() = runTest {
        subject.uiState.test {
            skipInitialCatalog()

            subject.onSearchQueryChanged("squat")
            val filteredState = awaitItem()
            assertEquals(1, filteredState.sheet.options.size)
            assertEquals("Squat", filteredState.sheet.options[0].name)
        }
    }

    @Test
    @DisplayName("Given library filter when applied then filters custom vs default lifts")
    fun libraryFiltersCorrectly() = runTest {
        subject.uiState.test {
            skipInitialCatalog()

            subject.onFilterIntent(LiftSwapFilterIntent.SelectLibrary(LiftLibraryFilter.Custom))
            val customState = awaitItem()
            assertEquals(1, customState.sheet.options.size)
            assertEquals("Custom Row", customState.sheet.options[0].name)

            subject.onFilterIntent(LiftSwapFilterIntent.SelectLibrary(LiftLibraryFilter.StratumDefault))
            val defaultState = awaitItem()
            assertEquals(2, defaultState.sheet.options.size)
            assertTrue(defaultState.sheet.options.all { !it.isCustom })
        }
    }

    @Test
    @DisplayName("Given lift id when toggleLiftEnabled is called then toggles lift in repository")
    fun togglesLiftEnabled() = runTest {
        subject.uiState.test {
            skipInitialCatalog()

            subject.toggleLiftEnabled("1", false)
            val updatedState = awaitItem()
            assertFalse(updatedState.sheet.options.first { it.id == "1" }.enabled)
            assertFalse(fakeLiftRepository.currentLifts.first { it.id == LiftId("1") }.enabled)
        }
    }

    @Test
    @DisplayName("Given origin lift when enterMergeMode then preselects and enables merge chrome")
    fun enterMergeModePreselects() = runTest {
        subject.uiState.test {
            skipInitialCatalog()

            subject.enterMergeMode("1")
            val mergeState = awaitItem()
            assertTrue(mergeState.isMergeMode)
            assertEquals(listOf("1"), mergeState.sheet.selectedOptions.map { it.id })
            assertFalse(mergeState.canConfirmMerge)
        }
    }

    @Test
    @DisplayName("Given second lift when toggled in merge mode then can confirm")
    fun toggleSelectionEnablesConfirm() = runTest {
        subject.uiState.test {
            skipInitialCatalog()

            subject.enterMergeMode("1")
            awaitItem()

            val second = subject.uiState.value.sheet.options.first { it.id == "3" }
            subject.toggleMergeSelection(second)
            val selected = awaitItem()
            assertTrue(selected.canConfirmMerge)
            assertEquals(setOf("1", "3"), selected.sheet.selectedLiftIds)
        }
    }

    @Test
    @DisplayName("Given two lifts when confirmMerge then custom deleted / seed hidden and mode exits")
    fun confirmMergeAppliesPort() = runTest {
        subject.uiState.test {
            val catalog = skipInitialCatalog()
            val custom = catalog.sheet.options.first { it.id == "3" }
            subject.enterMergeMode("1")
            awaitItem()
            subject.toggleMergeSelection(custom)
            awaitItem()
            subject.confirmMerge("1")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, mergePort.applyMergeCallCount)
        assertFalse(subject.uiState.value.isMergeMode)
        assertFalse(subject.uiState.value.isMerging)
        assertTrue(fakeLiftRepository.currentLifts.none { it.id == LiftId("3") })
        assertTrue(fakeLiftRepository.currentLifts.any { it.id == LiftId("1") })
    }

    @Test
    @DisplayName("Given confirmMerge already running when confirm again then second call is ignored")
    fun doubleConfirmIsSingleFlight() = runTest {
        subject.uiState.test {
            val catalog = skipInitialCatalog()
            val custom = catalog.sheet.options.first { it.id == "3" }
            subject.enterMergeMode("1")
            awaitItem()
            subject.toggleMergeSelection(custom)
            awaitItem()
            subject.confirmMerge("1")
            subject.confirmMerge("1")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, mergePort.applyMergeCallCount)
    }

    @Test
    @DisplayName("STR-131: start delete stages the dialog with DB impact for a seed lift")
    fun startDeleteStagesImpact() = runTest {
        fakeProgramRepository.saveProgram(
            com.stratum.domain.program.Program(
                id = com.stratum.domain.program.identity.ProgramId("p-1"),
                name = "Main",
                createdAt = java.time.Instant.parse("2026-01-01T00:00:00Z"),
                cycleLength = 1,
                isActive = true,
                workouts =
                    listOf(
                        com.stratum.domain.program.Workout(
                            id = com.stratum.domain.program.identity.WorkoutId("w-1"),
                            programId = com.stratum.domain.program.identity.ProgramId("p-1"),
                            name = "Push",
                            weekday = null,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    com.stratum.domain.program.WorkoutLift(
                                        id = com.stratum.domain.program.identity.WorkoutLiftId("wl-1"),
                                        workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
                                        liftId = LiftId("1"),
                                        progressionScheme = com.stratum.domain.program.ProgressionScheme.Linear,
                                        position = 0,
                                        sets = emptyList(),
                                    ),
                                ),
                        ),
                    ),
            ),
        )
        subject.uiState.test {
            skipInitialCatalog()
            subject.onStartDelete("1")
            val state = awaitItem()
            val pending = state.pendingDelete
            assertEquals("Bench Press", pending?.name)
            assertEquals(1, pending?.totalSlotCount)
            assertEquals(listOf("Push"), pending?.affectedWorkouts?.map { it.name })
            assertEquals(0, pending?.loggedSetCount)
            assertEquals(false, pending?.inActiveSession)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("STR-131: confirm delete tombstones seed and custom alike and reports success")
    fun confirmDeleteTombstones() = runTest {
        subject.uiState.test {
            skipInitialCatalog()
            subject.onStartDelete("3")
            awaitItem()
            subject.onConfirmDelete()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fakeLiftRepository.currentLifts.single { it.id == LiftId("3") }.isDeleted)
        assertEquals("Deleted Custom Row.", subject.userMessage.value)
        assertEquals(null, subject.uiState.value.pendingDelete)
    }

    @Test
    @DisplayName("STR-131: dismiss clears the staged delete without deleting")
    fun dismissDeleteClears() = runTest {
        subject.uiState.test {
            skipInitialCatalog()
            subject.onStartDelete("1")
            awaitItem()
            subject.onDismissDelete()
            val state = awaitItem()
            assertEquals(null, state.pendingDelete)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(fakeLiftRepository.currentLifts.single { it.id == LiftId("1") }.isDeleted)
    }

    @Test
    @DisplayName("STR-131: delete is not offered in merge mode")
    fun startDeleteIgnoredInMergeMode() = runTest {
        subject.uiState.test {
            skipInitialCatalog()
            subject.enterMergeMode("1")
            awaitItem()
            subject.onStartDelete("2")
            advanceUntilIdle()
            assertEquals(null, subject.uiState.value.pendingDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<GymUiState>.skipInitialCatalog(): GymUiState {
        val state = awaitItem()
        return if (state.sheet.options.isEmpty()) awaitItem() else state
    }
}
