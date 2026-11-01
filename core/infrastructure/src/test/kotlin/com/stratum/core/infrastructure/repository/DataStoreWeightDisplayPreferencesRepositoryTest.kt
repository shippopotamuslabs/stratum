package com.stratum.core.infrastructure.repository

import app.cash.turbine.test
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Robolectric tests for [DataStoreWeightDisplayPreferencesRepository]. The typed DataStore file is
 * process-scoped (one per Application), so each test resets to defaults in [setup].
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("DataStoreWeightDisplayPreferencesRepository")
class DataStoreWeightDisplayPreferencesRepositoryTest {

    private lateinit var store: DataStoreWeightDisplayPreferencesRepository

    @BeforeEach
    fun setup() = runTest {
        store = DataStoreWeightDisplayPreferencesRepository(RuntimeEnvironment.getApplication())
        store.setDisplayUnit(Weight.WeightUnit.Lb)
    }

    @Test
    @DisplayName("observe emits Lb by default")
    fun defaultPreferences() = runTest {
        store.observe().test {
            assertEquals(WeightDisplayPreferences(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("setDisplayUnit persists Kg and re-emits")
    fun setDisplayUnitPersists() = runTest {
        store.observe().test {
            assertEquals(WeightDisplayPreferences(), awaitItem())

            store.setDisplayUnit(Weight.WeightUnit.Kg)

            assertEquals(
                WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Kg),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("toggling back to Lb re-emits default")
    fun toggleBackToLb() = runTest {
        store.setDisplayUnit(Weight.WeightUnit.Kg)
        store.setDisplayUnit(Weight.WeightUnit.Lb)

        store.observe().test {
            assertEquals(WeightDisplayPreferences(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
