package com.stratum.core.infrastructure.repository

import app.cash.turbine.test
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Robolectric tests for [DataStoreThemePreferencesRepository]. The typed DataStore file is
 * process-scoped (one per Application), so each test resets to defaults in [setup].
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("DataStoreThemePreferencesRepository")
class DataStoreThemePreferencesRepositoryTest {

    private lateinit var store: DataStoreThemePreferencesRepository

    @BeforeEach
    fun setup() = runTest {
        store = DataStoreThemePreferencesRepository(RuntimeEnvironment.getApplication())
        // Shared DataStore file across tests in this process — restore defaults first.
        store.setFamily(ThemeFamily.Tonal)
        store.setColorMode(ColorMode.System)
    }

    @Test
    @DisplayName("observe emits Tonal + System by default")
    fun defaultPreferences() = runTest {
        store.observe().test {
            assertEquals(ThemePreferences(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("setFamily persists NeoBrutalist and re-emits")
    fun setFamilyPersists() = runTest {
        store.observe().test {
            assertEquals(ThemePreferences(), awaitItem())

            store.setFamily(ThemeFamily.NeoBrutalist)

            assertEquals(
                ThemePreferences(family = ThemeFamily.NeoBrutalist),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("setColorMode persists Light and re-emits")
    fun setColorModePersists() = runTest {
        store.observe().test {
            assertEquals(ThemePreferences(), awaitItem())

            store.setColorMode(ColorMode.Light)

            assertEquals(
                ThemePreferences(colorMode = ColorMode.Light),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("family and colorMode updates compose into a single preference snapshot")
    fun combinedUpdates() = runTest {
        store.setFamily(ThemeFamily.NeoBrutalist)
        store.setColorMode(ColorMode.Dark)

        store.observe().test {
            assertEquals(
                ThemePreferences(
                    family = ThemeFamily.NeoBrutalist,
                    colorMode = ColorMode.Dark,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
