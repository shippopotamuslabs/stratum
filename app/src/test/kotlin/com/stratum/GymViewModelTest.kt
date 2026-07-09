package com.stratum

import com.stratum.core.testing.logging.FakeLoggingPort

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.ui.component.LiftLibraryFilter
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.SetLiftEnabledUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class GymViewModelTest {

    private val fakeLiftRepository = FakeLiftRepository()
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
        subject = GymViewModel(
            observeLiftCatalog = ObserveLiftCatalogUseCase(fakeLiftRepository),
            setLiftEnabled = SetLiftEnabledUseCase(fakeLiftRepository, FakeLoggingPort()),
        )
    }

    @Test
    @DisplayName("Given catalog from repository when observed then maps all lifts including disabled ones")
    fun mapsAllLifts() = runTest {
        subject.uiState.test {
            val state = awaitItem()
            val updatedState = if (state.options.isEmpty()) awaitItem() else state

            assertEquals(3, updatedState.options.size)
            // custom first, then alphabetical
            assertEquals("Custom Row", updatedState.options[0].name)
            assertEquals("Bench Press", updatedState.options[1].name)
            assertEquals("Squat", updatedState.options[2].name)

            // verify enabled flag mapping
            assertTrue(updatedState.options[0].enabled)
            assertTrue(updatedState.options[1].enabled)
            assertFalse(updatedState.options[2].enabled)
        }
    }

    @Test
    @DisplayName("Given search query when changed then filters catalog by name")
    fun searchFiltersByName() = runTest {
        subject.uiState.test {
            awaitItem()

            subject.onSearchQueryChanged("squat")
            val filteredState = awaitItem()
            assertEquals(1, filteredState.options.size)
            assertEquals("Squat", filteredState.options[0].name)
        }
    }

    @Test
    @DisplayName("Given library filter when applied then filters custom vs default lifts")
    fun libraryFiltersCorrectly() = runTest {
        subject.uiState.test {
            awaitItem()

            subject.onFilterIntent(LiftSwapFilterIntent.SelectLibrary(LiftLibraryFilter.Custom))
            val customState = awaitItem()
            assertEquals(1, customState.options.size)
            assertEquals("Custom Row", customState.options[0].name)

            subject.onFilterIntent(LiftSwapFilterIntent.SelectLibrary(LiftLibraryFilter.StratumDefault))
            val defaultState = awaitItem()
            assertEquals(2, defaultState.options.size)
            assertTrue(defaultState.options.all { !it.isCustom })
        }
    }

    @Test
    @DisplayName("Given lift id when toggleLiftEnabled is called then toggles lift in repository")
    fun togglesLiftEnabled() = runTest {
        subject.uiState.test {
            awaitItem()

            subject.toggleLiftEnabled("1", false)
            val updatedState = awaitItem()
            assertFalse(updatedState.options.first { it.id == "1" }.enabled)
            assertFalse(fakeLiftRepository.currentLifts.first { it.id == LiftId("1") }.enabled)
        }
    }
}
