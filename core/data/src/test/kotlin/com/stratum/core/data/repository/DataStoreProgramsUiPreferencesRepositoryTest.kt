package com.stratum.core.data.repository

import app.cash.turbine.test
import com.stratum.domain.program.ProgramsUiPreferences
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Robolectric tests for [DataStoreProgramsUiPreferencesRepository]. The typed DataStore file is
 * process-scoped (one per Application), so each test resets to defaults in [setup].
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("DataStoreProgramsUiPreferencesRepository")
class DataStoreProgramsUiPreferencesRepositoryTest {

    private lateinit var store: DataStoreProgramsUiPreferencesRepository

    @BeforeEach
    fun setup() = runTest {
        store = DataStoreProgramsUiPreferencesRepository(RuntimeEnvironment.getApplication())
        // Shared DataStore file across tests in this process — restore defaults first.
        store.setExpandedWorkoutId(null)
    }

    @Test
    @DisplayName("observe emits null expandedWorkoutId by default")
    fun defaultExpandedId() = runTest {
        store.observe().test {
            assertEquals(ProgramsUiPreferences(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("setExpandedWorkoutId persists and re-emits")
    fun setExpandedPersists() = runTest {
        store.observe().test {
            assertEquals(ProgramsUiPreferences(), awaitItem())

            store.setExpandedWorkoutId("w-push")

            assertEquals(
                ProgramsUiPreferences(expandedWorkoutId = "w-push"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("setExpandedWorkoutId(null) clears the expanded card")
    fun clearExpandedPersists() = runTest {
        store.setExpandedWorkoutId("w-push")

        store.observe().test {
            assertEquals(
                ProgramsUiPreferences(expandedWorkoutId = "w-push"),
                awaitItem(),
            )

            store.setExpandedWorkoutId(null)

            assertEquals(ProgramsUiPreferences(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
